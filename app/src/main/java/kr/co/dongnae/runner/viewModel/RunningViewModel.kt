package kr.co.dongnae.runner.viewModel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.StateFlow
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.PolyUtil
import kr.co.dongnae.runner.service.RunningService

class RunningViewModel(application: Application) : AndroidViewModel(application) {

    // 1. TrackingManager의 StateFlow를 ViewModel에서 직접 구독
    val isRunning: StateFlow<Boolean> = TrackingManager.isTracking
    val isPaused: StateFlow<Boolean> = TrackingManager.isPaused
    val elapsedTime: StateFlow<Int> = TrackingManager.elapsedTime
    val routePoints: StateFlow<List<LatLng>> = TrackingManager.routePoints
    val distanceKm: StateFlow<Double> = TrackingManager.distanceKm
    val pace: StateFlow<String> = TrackingManager.pace

    // 2. ViewModel이 UI에서 사용할 수 있도록 StateFlow를 LiveData처럼 상태로 변환
    // (이 예제에서는 StateFlow를 그대로 노출하여 Coroutine 환경에서 사용 가능)

    // 3. UI에서 사용할 초기 위치는 더 이상 필요하지 않습니다.
    // 지도에 현재 위치를 표시하려면, TrackingManager.routePoints의 첫 지점을 사용하거나,
    // 별도의 단발성 위치 획득 로직이 필요합니다.

    // 4. 기존 로직 제거 (타이머, 위치 추적)

    /** 서비스에 러닝 시작 명령 전송 */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun startRunning() {
        if (isRunning.value) return

        val intent = Intent(getApplication(), RunningService::class.java).apply {
            action = "ACTION_START_RUNNING"
        }
        getApplication<Application>().startService(intent)
    }

    /** 서비스에 일시 정지 명령 전송 */
    fun pauseRunning() {
        if (!isRunning.value || isPaused.value) return
        val intent = Intent(getApplication(), RunningService::class.java).apply {
            action = "ACTION_PAUSE_RUNNING"
        }
        getApplication<Application>().startService(intent)
    }

    /** 서비스에 재개 명령 전송 */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun resumeRunning() {
        if (!isRunning.value || !isPaused.value) return
        val intent = Intent(getApplication(), RunningService::class.java).apply {
            action = "ACTION_RESUME_RUNNING"
        }
        getApplication<Application>().startService(intent)
    }

    private fun encodePolyline(points: List<LatLng>): String {
        return if (points.isEmpty()) "" else PolyUtil.encode(points)
    }

    /** 저장 및 서비스 종료 명령 전송 */
    fun stopRunningAndSave(uid: String, region: String, avgHeartRate: Int?) {
        // 1. 서비스 중단 명령 전에 TrackingManager에서 현재 데이터 백업
        val pointsBackup = TrackingManager.routePoints.value.toList()
        val elapsedBackup = TrackingManager.elapsedTime.value
        val distanceBackup = TrackingManager.distanceKm.value
        val paceBackup = TrackingManager.pace.value
        val startBackup = TrackingManager.startTimestamp
        val endTs = com.google.firebase.Timestamp.now()

        // 2. 현재까지의 총 일시정지 시간 계산 (현재 일시정지 중인 시간 포함)
        val totalPauseMillis = TrackingManager.getTotalPauseDurationMillis()

        // 3. 서비스 종료 명령 전송
        val intent = Intent(getApplication(), RunningService::class.java).apply {
            action = "ACTION_STOP_RUNNING"
        }
        getApplication<Application>().startService(intent)

        // 4. 일시정지 시간을 제외한 실제 러닝 시간 계산
        val startTime = startBackup?.toDate()?.time ?: endTs.toDate().time
        val endTime = endTs.toDate().time
        val totalDurationMillis = endTime - startTime
        val actualDurationSeconds = maxOf(0, ((totalDurationMillis - totalPauseMillis) / 1000).toInt())

        // 5. 데이터 저장 로직
        val polyline = encodePolyline(pointsBackup)
        val paceMinPerKm: Double? =
            if (distanceBackup > 0.01 && actualDurationSeconds > 0)
                (actualDurationSeconds / 60.0) / distanceBackup else null

        val data = hashMapOf(
            "user_id" to uid,
            "region" to region,
            "start_time" to (startBackup ?: endTs),
            "end_time" to endTs,
            "duration_seconds" to actualDurationSeconds, // 일시정지 시간 제외한 실제 러닝 시간
            "distance_km" to distanceBackup,
            "pace_per_km" to (paceMinPerKm ?: 0.0),
            "pace_display" to paceBackup,
            "heartbeat" to avgHeartRate,
            "routes_polyline" to polyline
        )

        // 6. TrackingManager 상태 초기화 (데이터 저장 후)
        TrackingManager.stopAndReset() // 이 부분이 중요: 모든 StateFlow 초기화

        FirebaseFirestore.getInstance()
            .collection("runRecord")
            .add(data)
            .addOnSuccessListener { 
                Log.d("RunningVM", "러닝 기록 저장 완료: duration_seconds=$actualDurationSeconds, totalPauseMillis=$totalPauseMillis, totalDurationMillis=$totalDurationMillis")
                Toast.makeText(
                    getApplication(),
                    "러닝 기록이 저장되었습니다",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e -> 
                Log.e("RunningVM", "러닝 기록 저장 실패", e)
                Toast.makeText(
                    getApplication(),
                    "러닝 기록 저장에 실패했습니다",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}