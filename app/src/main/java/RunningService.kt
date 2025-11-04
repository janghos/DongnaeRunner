package kr.co.dongnae.runner.service

import TrackingManager.getDurationSeconds
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kr.co.dongnae.runner.MainActivity
import java.util.Locale

class RunningService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private var runStartTime = 0L
    private var totalPausedTime = 0L
    private var lastPauseStart: Long? = null

    companion object {
        const val CHANNEL_ID = "RunningChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_TOGGLE_PAUSE = "ACTION_TOGGLE_PAUSE"
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
            ACTION_TOGGLE_PAUSE -> {
                if(TrackingManager.isPaused.value){
                    resumeRunning()
                }else {
                    pauseRunning()
                }
            }
        }
        return START_STICKY // 서비스가 강제 종료돼도 다시 시작하도록
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startRunningForeground() {
        if (TrackingManager.isTracking.value) return

        // 먼저 GPS 안정화부터 기다림
        serviceScope.launch {
            val stableLoc = awaitStableLocation()
            val safeLoc = stableLoc ?: try {
                fusedLocationClient.lastLocation.await() // get last known location
            } catch (_: Exception) {
                null
            } ?: Location("fallback").apply {
                latitude = 0.0
                longitude = 0.0
            }

            val firstPoint = LatLng(safeLoc.latitude, safeLoc.longitude)
            TrackingManager.updateRoute(listOf(firstPoint))
            TrackingManager.updateDistance(0.0)
            TrackingManager.updatePace("--'--")

            runStartTime = System.currentTimeMillis()
            totalPausedTime = 0L
            lastPauseStart = null

            TrackingManager.startTracking()
            startTimer()
            startLocationUpdates()

            val notification = buildNotification()

            startForeground(NOTIFICATION_ID, notification)
            updateNotification() // 타이머에서 주기적으로 호출됨
        }
    }

    private fun buildNotification(): Notification {
        val duration = getDurationSeconds()
        val pace = TrackingManager.pace.value
        val distanceKm = TrackingManager.distanceKm.value

        val minutes = duration / 60
        val seconds = duration % 60
        val timeStr = String.format("%02d:%02d", minutes, seconds)
        val distanceStr = String.format(Locale.getDefault(), "%.2fkm", distanceKm)

        // 메인 액티비티 이동 Intent
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingActivityIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // 일시정지/재개 토글 액션 Intent
        val toggleIntent = Intent(this, RunningService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        // FLAG_UPDATE_CURRENT는 알림의 데이터를 업데이트
        val togglePendingIntent = PendingIntent.getService(
            this,
            1, // requestCode 변경
            toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 현재 상태에 따른 버튼 텍스트
        val isPaused = TrackingManager.isPaused.value
        val actionText = if (isPaused) "재개" else "일시정지"
        val actionIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("러닝 중: $timeStr ($distanceStr)")
            .setContentText("페이스: $pace")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(pendingActivityIntent)
            .setOngoing(true)
            .addAction(actionIcon, actionText, togglePendingIntent)


        return builder.build()
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun pauseRunning() {
        lastPauseStart = System.currentTimeMillis()
        TrackingManager.pauseTracking()
        timerJob?.cancel()
        stopLocationUpdates()
        updateNotification()
        // 알림 업데이트 등 필요
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun resumeRunning() {
        lastPauseStart?.let {
            totalPausedTime += System.currentTimeMillis() - it
            lastPauseStart = null
        }
        TrackingManager.resumeTracking()
        startTimer()
        startLocationUpdates()
        updateNotification()
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
            while (TrackingManager.isTracking.value && !TrackingManager.isPaused.value) {
                delay(1000)
                val elapsedSeconds = TrackingManager.getDurationSeconds()
                TrackingManager.updateTime(elapsedSeconds)

                val distance = TrackingManager.distanceKm.value
                val paceStr = calculatePace(elapsedSeconds, distance)
                TrackingManager.updatePace(paceStr)
                updateNotification()
            }
        }
    }

    @RequiresPermission(allOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ])
    private fun startLocationUpdates() {
        if (locationCallback != null) return

        val request = LocationRequest.create().apply {
            interval = 2000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val ACCURACY_THRESHOLD_METERS = 15f
        val DISTANCE_THRESHOLD_METERS = 1.0
        val DISTANCE_THRESHOLD_KM = DISTANCE_THRESHOLD_METERS / 1000.0

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val lastLocation = result.lastLocation ?: return
                val newPoint = LatLng(lastLocation.latitude, lastLocation.longitude)
                val oldPoints = TrackingManager.routePoints.value
                var newTotalDistance = TrackingManager.distanceKm.value

                if (oldPoints.isNotEmpty()) {
                    val lastPoint = oldPoints.last()
                    val distance = calculateDistance(
                        lastPoint.latitude, lastPoint.longitude,
                        newPoint.latitude, newPoint.longitude
                    )

                    if (lastLocation.accuracy < ACCURACY_THRESHOLD_METERS &&
                        distance >= DISTANCE_THRESHOLD_KM
                    ) {
                        newTotalDistance += distance
                        TrackingManager.updateDistance(newTotalDistance)
                        TrackingManager.updateRoute(oldPoints + newPoint)
                        val newPace = calculatePace(TrackingManager.elapsedTime.value, newTotalDistance)
                        TrackingManager.updatePace(newPace)
                    }
                } else {
                    TrackingManager.updateRoute(oldPoints + newPoint)
                }
            }
        }

        Handler(Looper.getMainLooper()).post {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )
        }
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
        if (totalDistanceKm < 0.01) return "--'--" // 최소 거리 이하 무시
        // 1km당 걸린 초 (float precision 보정)
        val paceSecondsPerKm = totalSeconds.toDouble() / totalDistanceKm
        // 시간 계산
        val minutes = (paceSecondsPerKm / 60).toInt()
        val seconds = (paceSecondsPerKm % 60).toInt()
        return String.format("%d'%02d''", minutes, seconds)
    }

    @RequiresPermission(allOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION])
    private suspend fun awaitStableLocation(
        client: FusedLocationProviderClient = fusedLocationClient,
        accuracyMeters: Float = 15f,
        maxUpdates: Int = 6,
        timeoutMs: Long = 8_000L
    ): Location? {

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                var resumed = false
                var received = 0

                // 최신 API (Builder) 우선, 구버전이면 create()로 대체
                val request = try {
                    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, /* interval */ 1000L)
                        .setMinUpdateIntervalMillis(500L)
                        .setMaxUpdates(maxUpdates)
                        .setWaitForAccurateLocation(true) // 가능한 정확한 첫 위치를 기다림
                        .build()
                } catch (_: Throwable) {
                    @Suppress("DEPRECATION")
                    LocationRequest.create().apply {
                        interval = 1000L
                        fastestInterval = 500L
                        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                        @Suppress("DEPRECATION")
                        numUpdates = maxUpdates
                    }
                }

                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        if (resumed) return
                        val loc = result.lastLocation ?: return
                        received++

                        // 정확도 체크 (accuracy <= accuracyMeters)
                        if (loc.accuracy <= accuracyMeters || received >= maxUpdates) {
                            resumed = true
                            client.removeLocationUpdates(this)
                            cont.resume(loc)
                        }
                    }
                }

                // 콜백 등록 (반드시 메인 루퍼 지정)
                client.requestLocationUpdates(
                    request,
                    callback,
                    Looper.getMainLooper()
                )

                // 코루틴 취소 시 콜백 해제
                cont.invokeOnCancellation {
                    client.removeLocationUpdates(callback)
                }
            }
        }
    }
}