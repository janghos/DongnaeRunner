package kr.co.dongnae.runner.screen

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

    private var timerJob: Job? = null
    private var locationCallback: LocationCallback? = null

    fun startRunning() {
        if (_isRunning.value) return
        _isRunning.value = true
        _isPaused.value = false
        startTimer()
    }

    fun pauseRunning() {
        if (!_isRunning.value || _isPaused.value) return
        _isPaused.value = true
        stopTimer()
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
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isRunning.value && !_isPaused.value) {
                kotlinx.coroutines.delay(1000)
                _elapsedTime.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun startLocationUpdates() {
        if (locationCallback != null) return
        val request = LocationRequest.create().apply {
            interval = 2000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val lastLocation = result.lastLocation
                if (lastLocation != null) {
                    val newPoint = LatLng(lastLocation.latitude, lastLocation.longitude)
                    _routePoints.value = _routePoints.value + newPoint
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


