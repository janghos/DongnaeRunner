// LoginScreen.kt (리팩토링 완료)
package kr.co.dongnae.runner.presentation.login

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.google.accompanist.permissions.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kr.co.dongnae.runner.BuildConfig
import kr.co.dongnae.runner.model.FirestoreUser
import kr.co.dongnae.runner.trySilentSignIn

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val region = "서울" // TODO: 실제 위치 권한 결과로 대체 예정

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_AUTH_KEY_DEBUG)
            .requestEmail()
            .build()
    }

    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleLoginLauncher = rememberLauncherForActivityResult(
        contract = GoogleLoginContract()
    ) { idToken ->
        if (idToken != null) {
            viewModel.loginWithGoogle(idToken, region)
        } else {
            Toast.makeText(context, "로그인 실패", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    LaunchedEffect(Unit) {
        locationPermissionState.launchMultiplePermissionRequest()
    }

    var triedAuto by remember { mutableStateOf(false) } // 한 번만 시도
    var isAutoLoggingIn by remember { mutableStateOf(false) }

    LaunchedEffect(locationPermissionState.allPermissionsGranted) {
        if (locationPermissionState.allPermissionsGranted && !triedAuto) {
            triedAuto = true
            val last = GoogleSignIn.getLastSignedInAccount(context)
            if (last != null && !last.isExpired) {
                val token = last.idToken
                if (token != null) {
                    isAutoLoggingIn = true
                    Toast.makeText(context, "자동 로그인 중...", Toast.LENGTH_SHORT).show()
                    viewModel.loginWithGoogle(token, region)
                } else {
                    trySilentSignIn(googleClient) { idToken ->
                        if (idToken != null) {
                            isAutoLoggingIn = true
                            Toast.makeText(context, "자동 로그인 중...", Toast.LENGTH_SHORT).show()
                            viewModel.loginWithGoogle(idToken, region)
                        } else {
                            isAutoLoggingIn = false
                        }
                    }
                }
            } else {
                trySilentSignIn(googleClient) { idToken ->
                    if (idToken != null) {
                        isAutoLoggingIn = true
                        Toast.makeText(context, "자동 로그인 중...", Toast.LENGTH_SHORT).show()
                        viewModel.loginWithGoogle(idToken, region)
                    } else {
                        isAutoLoggingIn = false
                    }
                }
            }
        }
    }

    val loginState by viewModel.loginState.collectAsState()

    // Debug: log loginState changes
    LaunchedEffect(loginState) {
        android.util.Log.d("LoginScreen", "loginState changed: user=${loginState.user != null}, loading=${loginState}, error=${loginState.error}")
    }

    // 로그인 성공 시 런스크린으로 이동
    LaunchedEffect(loginState.user) {
        loginState.user?.let {
            isAutoLoggingIn = false
            navController.navigate("run/${it.uid}") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
                restoreState = false
            }
        }
    }

    // UI Content만 따로 분리한 함수 호출
    LoginContent(
        isPermissionGranted = locationPermissionState.allPermissionsGranted,
        onPermissionRequest = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
            launcher.launch(intent)
        },
        onLoginClick = { googleLoginLauncher.launch(Unit) },
        showProgress = isAutoLoggingIn
    )

}

@Composable
fun LoginContent(
    isPermissionGranted: Boolean,
    onPermissionRequest: () -> Unit,
    onLoginClick: () -> Unit,
    showProgress: Boolean
) {
    if (!isPermissionGranted) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(
                    onClick = onPermissionRequest,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("설정으로 이동")
                }
            },
            title = { Text("위치 권한 필요") },
            text = { Text("앱을 사용하려면 위치 권한이 필요합니다.") }
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showProgress) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = "Google 로그인",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginContentPreview() {
    LoginContent(
        isPermissionGranted = true,
        onPermissionRequest = {},
        onLoginClick = {},
        showProgress = false
    )
}

class GoogleLoginContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_AUTH_KEY_DEBUG)
            .requestEmail()
            .build()

        val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode != Activity.RESULT_OK) return null
        val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
        return task.result?.idToken
    }
}