package kr.co.dongnae.runner.viewModel

import android.Manifest
import android.app.Application
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log // Log import 추가

class RunningViewModel(application: Application) : AndroidViewModel(application) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _elapsedTime = MutableStateFlow(0)
    val elapsedTime: StateFlow<Int> = _elapsedTime

    private val _routePoints = MutableStateFlow<List<LatLng>>(emptyList())
    val routePoints: StateFlow<List<LatLng>> = _routePoints

    // [START] 새 상태 추가: 거리 및 페이스
    private val _distanceKm = MutableStateFlow(0.0)
    val distanceKm: StateFlow<Double> = _distanceKm

    private val _pace = MutableStateFlow("--'--")
    val pace: StateFlow<String> = _pace
    // [END] 새 상태 추가

    private var timerJob: Job? = null
    private var locationCallback: LocationCallback? = null

    init {
        // [수정] 뷰모델 초기화 시 바로 초기 위치를 확보하여 지도 로드를 시작합니다.
        // 위치 권한이 앱에서 이미 처리되었다고 가정하고 호출합니다.
        try {
            fetchAndSetInitialLocation()
        } catch (e: SecurityException) {
            Log.e("RunningViewModel", "Location permission denied during initialization.")
        }
    }

    /**
     * Haversine 공식을 사용하여 두 위경도 지점 사이의 거리를 킬로미터(km)로 계산합니다.
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // 지구 반경 (킬로미터)
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c // 거리 (km)
    }

    /**
     * 총 시간(초)과 총 거리(km)를 바탕으로 분/km 형태의 페이스 문자열 (예: "5'30''")을 계산합니다.
     */
    private fun calculatePace(totalSeconds: Int, totalDistanceKm: Double): String {
        // 거리가 10m(0.01km) 이하이면 페이스 계산을 하지 않습니다.
        if (totalDistanceKm <= 0.01) return "--'--"

        // 1km당 걸린 시간 (초)
        val paceSecondsPerKm = totalSeconds / totalDistanceKm
        val minutes = (paceSecondsPerKm / 60).toInt()
        val seconds = (paceSecondsPerKm % 60).toInt()

        // 분'초초'' 형식으로 포맷
        return String.format("%d'%02d''", minutes, seconds)
    }

    /**
     * 마지막 알려진 위치를 가져와 _routePoints가 비어있는 경우 초기 위치로 추가합니다.
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun fetchAndSetInitialLocation() {
        if (_routePoints.value.isNotEmpty()) return

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null && _routePoints.value.isEmpty()) {
                    val initialPoint = LatLng(location.latitude, location.longitude)
                    _routePoints.value = listOf(initialPoint)
                    Log.d("RunningViewModel", "Initial location set: $initialPoint")
                } else if (location == null) {
                    Log.w("RunningViewModel", "Last known location is null.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("RunningViewModel", "Failed to get last location.", e)
            }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun startRunning() {
        if (_isRunning.value) return
        _isRunning.value = true
        _isPaused.value = false
        startTimer()
        startLocationUpdates()
    }

    fun pauseRunning() {
        if (!_isRunning.value || _isPaused.value) return
        _isPaused.value = true
        stopTimer()
        stopLocationUpdates()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun resumeRunning() {
        if (!_isRunning.value || !_isPaused.value) return
        _isPaused.value = false
        startTimer()
        startLocationUpdates()
    }

    fun stopRunning() {
        _isRunning.value = false
        _isPaused.value = false
        stopTimer()
        stopLocationUpdates()
        _elapsedTime.value = 0
        _routePoints.value = emptyList()
        _distanceKm.value = 0.0
        _pace.value = "--'--"
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isRunning.value && !_isPaused.value) {
                kotlinx.coroutines.delay(1000)
                val newTime = _elapsedTime.value + 1
                _elapsedTime.value = newTime
                // 1초마다 페이스 업데이트 (타이머가 돌고 있는 동안)
                if (_distanceKm.value > 0.01) {
                    _pace.value = calculatePace(newTime, _distanceKm.value)
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun startLocationUpdates() {
        // 이미 위치 업데이트 중이라면 중복 실행 방지
        if (locationCallback != null) return
        val request = LocationRequest.create().apply {
            interval = 2000 // 2초 간격
            fastestInterval = 1000 // 가장 빠른 간격 1초
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY // 높은 정확도
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val lastLocation = result.lastLocation
                if (lastLocation != null) {
                    val newPoint = LatLng(lastLocation.latitude, lastLocation.longitude)
                    val oldPoints = _routePoints.value

                    // 거리 계산 및 업데이트
                    if (oldPoints.isNotEmpty()) {
                        val lastPoint = oldPoints.last()
                        val distance = calculateDistance(
                            lastPoint.latitude, lastPoint.longitude,
                            newPoint.latitude, newPoint.longitude
                        )
                        val newTotalDistance = _distanceKm.value + distance
                        _distanceKm.value = newTotalDistance

                        // 페이스 업데이트 (새로운 거리 반영)
                        _pace.value = calculatePace(_elapsedTime.value, newTotalDistance)
                    }

                    // 경로 업데이트
                    _routePoints.value = oldPoints + newPoint
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
}


