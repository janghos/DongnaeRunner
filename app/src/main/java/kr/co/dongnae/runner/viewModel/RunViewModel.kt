package kr.co.dongnae.runner.viewModel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.co.dongnae.runner.BuildConfig
import kr.co.dongnae.runner.model.FirestoreUser
import kr.co.dongnae.runner.util.LocationUtil
import javax.inject.Inject

@HiltViewModel
class RunViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _user = MutableStateFlow<FirestoreUser?>(null)
    val user: StateFlow<FirestoreUser?> = _user.asStateFlow()

    private val _currentRegion = MutableStateFlow<String?>(null)
    val currentRegion: StateFlow<String?> = _currentRegion.asStateFlow()

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun loadUser(uid: String) {
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                document.toObject(FirestoreUser::class.java)?.let {
                    _user.value = it
                }
            }
            .addOnFailureListener { e ->
                Log.e("RunViewModel", "Firestore 사용자 정보 불러오기 실패", e)
            }
    }

    suspend fun updateCurrentRegion() {
        try {
            val location = LocationUtil.getCurrentLocation(context, fusedLocationClient)
            if (location != null) {
                val region = LocationUtil.getRegionFromLocation(context, location)
                _currentRegion.value = simplifyRegionName(region)
            } else {
                _currentRegion.value = null
            }
        } catch (e: Exception) {
            Log.e("RunViewModel", "현재 지역 가져오기 실패", e)
            _currentRegion.value = null
        }
    }

    private fun simplifyRegionName(region: String): String {
        return when {
            region.contains("서울") -> "서울"
            region.contains("인천") -> "인천"
            region.contains("경기") -> "경기"
            region.contains("부산") -> "부산"
            region.contains("대구") -> "대구"
            region.contains("광주") -> "광주"
            region.contains("대전") -> "대전"
            region.contains("울산") -> "울산"
            region.contains("세종") -> "세종"
            region.contains("강원") -> "강원"
            region.contains("충북") || region.contains("충청북도") -> "충북"
            region.contains("충남") || region.contains("충청남도") -> "충남"
            region.contains("전북") || region.contains("전라북도") -> "전북"
            region.contains("전남") || region.contains("전라남도") -> "전남"
            region.contains("경북") || region.contains("경상북도") -> "경북"
            region.contains("경남") || region.contains("경상남도") -> "경남"
            region.contains("제주") -> "제주"
            else -> region
        }
    }

    fun logout(activity: Activity?) {
        firebaseAuth.signOut()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_AUTH_KEY_DEBUG)
            .requestEmail()
            .build()

        activity?.let {
            val googleSignInClient = GoogleSignIn.getClient( activity,gso)
            googleSignInClient.signOut()
        }

    }
}