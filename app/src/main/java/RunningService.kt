// kr.co.dongnae.runner.service/RunningService.kt

package kr.co.dongnae.runner.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RunningService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    companion object {
        const val CHANNEL_ID = "RunningChannel"
        const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null // Unbound Service 사용

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_START_RUNNING" -> startRunningForeground()
            "ACTION_PAUSE_RUNNING" -> pauseRunning()
            "ACTION_RESUME_RUNNING" -> resumeRunning()
            "ACTION_STOP_RUNNING" -> stopRunning()
        }
        return START_STICKY // 서비스가 강제 종료돼도 다시 시작하도록
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startRunningForeground() {
        if (TrackingManager.isTracking.value) return

        TrackingManager.startTracking()
        startTimer()
        startLocationUpdates()

        // 포그라운드 서비스 시작 (영구 알림 표시)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("동네 러닝")
            .setContentText("러닝을 기록 중입니다...")
            .setSmallIcon(android.R.drawable.ic_menu_directions) // 아이콘은 적절히 변경하세요
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun pauseRunning() {
        TrackingManager.pauseTracking()
        timerJob?.cancel()
        stopLocationUpdates()
        // 알림 업데이트 등 필요
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun resumeRunning() {
        TrackingManager.resumeTracking()
        startTimer()
        startLocationUpdates()
        // 알림 업데이트 등 필요
    }

    private fun stopRunning() {
        timerJob?.cancel()
        timerJob = null
        stopLocationUpdates()
        // TrackingManager.stopAndReset() 호출은 ViewModel에서 데이터 저장 후 진행할 수도 있음.
        // 여기서는 서비스 종료 명령만 수행
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf() // 서비스 종료
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var timeSeconds = TrackingManager.elapsedTime.value
            while (TrackingManager.isTracking.value && !TrackingManager.isPaused.value) {
                kotlinx.coroutines.delay(1000)
                timeSeconds++
                TrackingManager.updateTime(timeSeconds)

                // 페이스 업데이트 로직 - 서비스에서 직접 계산하여 TrackingManager 업데이트
                val distance = TrackingManager.distanceKm.value
                val paceStr = calculatePace(timeSeconds, distance)
                TrackingManager.updatePace(paceStr)
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {
        if (locationCallback != null) return

        val request = LocationRequest.create().apply {
            interval = 2000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // GPS 정확도(Accuracy) 임계값 설정 (미터)
        // 30미터 이상 부정확한 위치는 오차로 간주하고 무시
        val ACCURACY_THRESHOLD_METERS = 30f

        // 거리 이동 임계값 설정 (미터)
        // 1미터 이하의 이동은 GPS 오차로 간주하고 무시
        // 민감도를 높여 작은 움직임도 기록하도록 조정
        val DISTANCE_THRESHOLD_METERS = 1.0
        val DISTANCE_THRESHOLD_KM = DISTANCE_THRESHOLD_METERS / 1000.0

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val lastLocation = result.lastLocation

                // 1. 위치 데이터가 유효할 때만 처리
                if (lastLocation != null) {

                    val newPoint = LatLng(lastLocation.latitude, lastLocation.longitude)
                    val oldPoints = TrackingManager.routePoints.value
                    var newTotalDistance = TrackingManager.distanceKm.value

                    if (oldPoints.isNotEmpty()) {
                        val lastPoint = oldPoints.last()

                        // 직전 위치와 새 위치 사이의 거리 계산 (단위: km)
                        val distance = calculateDistance(
                            lastPoint.latitude, lastPoint.longitude,
                            newPoint.latitude, newPoint.longitude
                        )

                        // 2. GPS 정확도가 높고, 이동 거리가 임계값 이상일 때만 누적 거리 추가
                        // 정확도가 낮거나 이동 거리가 너무 작으면 위치 무시
                        if (lastLocation.accuracy < ACCURACY_THRESHOLD_METERS && distance >= DISTANCE_THRESHOLD_KM) {
                            newTotalDistance += distance

                            // TrackingManager 업데이트
                            TrackingManager.updateDistance(newTotalDistance)
                            TrackingManager.updateRoute(oldPoints + newPoint)
                        }
                        // 정확도가 낮거나 이동 거리가 임계값 미만인 경우 위치 무시

                    } else {
                        // 경로의 첫 번째 포인트는 무조건 추가
                        TrackingManager.updateRoute(oldPoints + newPoint)
                    }
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, null)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Running Status"
            val descriptionText = "Displays status of ongoing run"
            val importance = NotificationManager.IMPORTANCE_LOW // 소리/진동 없이
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopLocationUpdates()
    }

    // 기존 ViewModel에서 가져온 유틸리티 함수
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // 지구 반경 (킬로미터)
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun calculatePace(totalSeconds: Int, totalDistanceKm: Double): String {
        if (totalDistanceKm <= 0.01) return "--'--"
        val paceSecondsPerKm = totalSeconds / totalDistanceKm
        val minutes = (paceSecondsPerKm / 60).toInt()
        val seconds = (paceSecondsPerKm % 60).toInt()
        return String.format("%d'%02d''", minutes, seconds)
    }
}