package kr.co.dongnae.runner.presentation.login

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.co.dongnae.runner.BuildConfig
import kr.co.dongnae.runner.model.FirestoreUser
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState

    fun signIn(activity: Activity) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_AUTH_KEY_DEBUG)
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(activity, gso)
        val signInIntent = googleSignInClient.signInIntent
        activity.startActivityForResult(signInIntent, 9001)
    }

    fun loginWithGoogle(idToken: String, region: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    user?.let {
                        saveUserToFirestore(it, region)
                    }
                } else {
                    _loginState.value = LoginUiState(error = "로그인 실패: ${task.exception?.message}")
                }
            }
    }

    private fun saveUserToFirestore(firebaseUser: FirebaseUser, region: String) {
        val userDoc = firestore.collection("users").document(firebaseUser.uid)
        val now = Timestamp.now()

        userDoc.get().addOnSuccessListener { document ->
            if (document.exists()) {
                // 기존 유저: region만 바뀌었는지 체크하고 update
                userDoc.update(
                    mapOf(
                        "region" to region,
                        "last_login" to now
                    )
                )
            } else {
                // 신규 유저
                val userData = FirestoreUser(
                    uid = firebaseUser.uid,
                    name = firebaseUser.displayName ?: "No Name",
                    email = firebaseUser.email ?: "No Email",
                    region = region,
                    created_at = now,
                    last_login = now
                )
                userDoc.set(userData)
            }

            // Firestore에서 다시 읽어서 최신 정보로 상태 설정
            userDoc.get().addOnSuccessListener { snapshot ->
                val firestoreUser = snapshot.toObject(FirestoreUser::class.java)
                _loginState.value = LoginUiState(user = firestoreUser)
            }

        }.addOnFailureListener {
            _loginState.value = LoginUiState(error = "Firestore 저장 실패: ${it.message}")
        }
    }
}


data class LoginUiState(
    val user: FirestoreUser? = null,
    val error: String? = null
)