package kr.co.dongnae.runner

import com.google.android.gms.auth.api.signin.GoogleSignInClient
import kotlinx.coroutines.tasks.await

suspend fun trySilentSignIn(
    client: GoogleSignInClient,
    onResult: (String?) -> Unit
) {
    try {
        val acc = client.silentSignIn().await() // 코루틴으로 대기
        onResult(acc?.idToken)
    } catch (e: Exception) {
        onResult(null) // 실패 시 null
    }
}