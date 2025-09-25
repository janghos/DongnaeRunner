package kr.co.dongnae.runner.viewModel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.co.dongnae.runner.BuildConfig
import kr.co.dongnae.runner.model.FirestoreUser
import javax.inject.Inject

@HiltViewModel
class RunViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _user = MutableStateFlow<FirestoreUser?>(null)
    val user: StateFlow<FirestoreUser?> = _user

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