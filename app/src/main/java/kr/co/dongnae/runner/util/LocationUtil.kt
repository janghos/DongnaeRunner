package kr.co.dongnae.runner.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import java.util.Locale

object LocationUtil {
    
    /**
     * 현재 위치에서 지역 정보를 가져옵니다 (서울특별시, 인천광역시, 경기도 등)
     */
    suspend fun getRegionFromLocation(
        context: Context,
        location: Location
    ): String {
        return try {
            val geocoder = Geocoder(context, Locale.KOREAN)
            val addresses = geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1
            )
            
            if (addresses!!.isNotEmpty()) {
                val address = addresses[0]
                // 광역시/도 단위로 추출
                extractRegion(address)
            } else {
                "알 수 없음"
            }
        } catch (e: Exception) {
            "알 수 없음"
        }
    }
    
    /**
     * Address에서 지역 정보 추출 (서울특별시, 인천광역시, 경기도 등)
     */
    private fun extractRegion(address: Address): String {
        // adminArea는 보통 "서울특별시", "인천광역시", "경기도" 등
        val adminArea = address.adminArea ?: ""
        if (adminArea.isNotEmpty()) {
            return adminArea
        }
        
        // fallback: locality 사용
        val locality = address.locality ?: ""
        if (locality.isNotEmpty()) {
            return locality
        }
        
        return "알 수 없음"
    }
    
    /**
     * 현재 위치를 가져옵니다
     */
    suspend fun getCurrentLocation(
        context: Context,
        fusedLocationClient: FusedLocationProviderClient
    ): Location? {
        return try {
            val locationResult = fusedLocationClient.lastLocation.await()
            locationResult
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 두 지점 간의 거리를 계산합니다 (km 단위)
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusKm = 6371.0
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadiusKm * c
    }
}

