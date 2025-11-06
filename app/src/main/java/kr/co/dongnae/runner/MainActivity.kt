package kr.co.dongnae.runner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.AndroidEntryPoint
import kr.co.dongnae.runner.presentation.login.LoginScreen
import kr.co.dongnae.runner.presentation.splash.SplashScreen
import kr.co.dongnae.runner.screen.RunScreen
import kr.co.dongnae.runner.screen.RunningRecordDetailScreen
import kr.co.dongnae.runner.screen.RunningRecordScreen
import kr.co.dongnae.runner.screen.RunningScreen
import kr.co.dongnae.runner.ui.theme.DongneRunnerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DongnaeRunnerApp()
        }
    }
}

@Composable
fun DongnaeRunnerApp() {
    val navController = rememberNavController()


    val context = LocalContext.current
    var lastBackPressed by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = true) {
        val canGoBack = navController.previousBackStackEntry != null
        if (canGoBack) {
            navController.popBackStack()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressed < 2000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressed = now
                Toast.makeText(context, "앱을 종료하려면 한 번 더 눌러주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DongneRunnerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(navController, startDestination = "splash") {
                composable("running/{uid}") { backStackEntry ->
                    val uid = backStackEntry.arguments?.getString("uid") ?: ""
                    RunningScreen(navController, uid)
                }

                composable("records/{uid}") { backStackEntry ->
                    val uid = backStackEntry.arguments?.getString("uid") ?: ""
                    RunningRecordScreen(navController = navController, uid = uid)
                }

                composable("splash") { SplashScreen(navController) }
                composable("login") { LoginScreen(navController) }
                composable(
                    route = "run/{uid}",
                    arguments = listOf(navArgument("uid") { defaultValue = "" })
                ) { backStackEntry ->
                    val uid = backStackEntry.arguments?.getString("uid") ?: ""
                    RunScreen(navController, uid)
                }
                composable("recordDetail/{runId}") { backStackEntry ->
                    val runId = backStackEntry.arguments?.getString("runId") ?: return@composable
                    RunningRecordDetailScreen(navController = navController, runId = runId)
                }
            }
        }
    }
}