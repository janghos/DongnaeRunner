import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// kr.co.dongnae.runner.service/TrackingManager.kt

object TrackingManager {

    // 1. 러닝 상태 (Boolean)
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    // 2. 일시 정지 상태 (Boolean)
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    // 3. 경과 시간 (Int, 초 단위)
    private val _elapsedTime = MutableStateFlow(0)
    val elapsedTime: StateFlow<Int> = _elapsedTime

    // 4. 총 거리 (Double, km 단위)
    private val _distanceKm = MutableStateFlow(0.0)
    val distanceKm: StateFlow<Double> = _distanceKm

    // 5. 현재 경로 지점 (List<LatLng>)
    // 서비스가 계속 업데이트하고, 뷰모델이 지도를 그리는 데 사용합니다.
    private val _routePoints = MutableStateFlow<List<com.google.android.gms.maps.model.LatLng>>(emptyList())
    val routePoints: StateFlow<List<com.google.android.gms.maps.model.LatLng>> = _routePoints

    // 6. 페이스 문자열 (String)
    private val _pace = MutableStateFlow("--'--")
    val pace: StateFlow<String> = _pace

    // 6-1. 현재 심박수 (Int, bpm)
    private val _currentHeartRate = MutableStateFlow<Int?>(null)
    val currentHeartRate: StateFlow<Int?> = _currentHeartRate

    // 6-2. 심박수 데이터 리스트 (평균 계산용)
    private val _heartRateList = MutableStateFlow<List<Int>>(emptyList())
    val heartRateList: StateFlow<List<Int>> = _heartRateList

    // 7. 러닝 시작/종료 시간 (Firebase 저장용)
    var startTimestamp: com.google.firebase.Timestamp? = null
    var endTimestamp: com.google.firebase.Timestamp? = null

    private var pauseStartMillis: Long? = null

    // 8. 일시정지된 시간 누적 (millis)
    var pauseDurationMillis: Long = 0


    // 상태 업데이트 함수 (주로 Service에서 호출)
    fun startTracking() {
        _isTracking.value = true
        _isPaused.value = false
        startTimestamp = com.google.firebase.Timestamp.now()
    }

    fun pauseTracking() {
        _isPaused.value = true
        pauseStartMillis = System.currentTimeMillis()
    }

    fun resumeTracking() {
        _isPaused.value = false
        val now = System.currentTimeMillis()
        if (pauseStartMillis != null) {
            pauseDurationMillis += (now - pauseStartMillis!!)
            pauseStartMillis = null
        }
    }

    fun stopAndReset() {
        _isTracking.value = false
        _isPaused.value = false
        endTimestamp = com.google.firebase.Timestamp.now()
        // 상태 초기화
        _elapsedTime.value = 0
        _distanceKm.value = 0.0
        _routePoints.value = emptyList()
        _pace.value = "--'--"
        _currentHeartRate.value = null
        _heartRateList.value = emptyList()
        startTimestamp = null
        endTimestamp = null
        pauseDurationMillis = 0
        pauseStartMillis = null
    }

    fun updateTime(seconds: Int) {
        _elapsedTime.value = seconds
    }

    fun updateDistance(distance: Double) {
        _distanceKm.value = distance
    }

    fun updateRoute(points: List<com.google.android.gms.maps.model.LatLng>) {
        _routePoints.value = points
    }

    fun updatePace(paceStr: String) {
        _pace.value = paceStr
    }

    fun updateHeartRate(heartRate: Int) {
        _currentHeartRate.value = heartRate
        _heartRateList.value = _heartRateList.value + heartRate
    }

    fun getAverageHeartRate(): Int? {
        val list = _heartRateList.value
        return if (list.isNotEmpty()) {
            (list.sum().toDouble() / list.size).toInt()
        } else {
            null
        }
    }

    fun getDurationSeconds(): Int {
        val start = startTimestamp?.toDate()?.time ?: return 0
        val end = endTimestamp?.toDate()?.time ?: System.currentTimeMillis()
        val duration = end - start - pauseDurationMillis
        return maxOf(0, (duration / 1000).toInt())
    }

    /**
     * 현재까지의 총 일시정지 시간을 반환 (현재 일시정지 중인 시간 포함)
     */
    fun getTotalPauseDurationMillis(): Long {
        val currentPauseTime = if (pauseStartMillis != null && _isPaused.value) {
            System.currentTimeMillis() - pauseStartMillis!!
        } else {
            0L
        }
        return pauseDurationMillis + currentPauseTime
    }
}