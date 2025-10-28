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


    // 7. 러닝 시작/종료 시간 (Firebase 저장용)
    var startTimestamp: com.google.firebase.Timestamp? = null
    var endTimestamp: com.google.firebase.Timestamp? = null


    // 상태 업데이트 함수 (주로 Service에서 호출)
    fun startTracking() {
        _isTracking.value = true
        _isPaused.value = false
        startTimestamp = com.google.firebase.Timestamp.now()
    }

    fun pauseTracking() {
        _isPaused.value = true
    }

    fun resumeTracking() {
        _isPaused.value = false
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
        startTimestamp = null
        endTimestamp = null
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
}