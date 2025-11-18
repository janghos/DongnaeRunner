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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

import dagger.hilt.android.AndroidEntryPoint
import kr.co.dongnae.runner.presentation.login.LoginScreen
import kr.co.dongnae.runner.presentation.splash.SplashScreen
import kr.co.dongnae.runner.screen.CommunityScreen
import kr.co.dongnae.runner.screen.RunScreen
import kr.co.dongnae.runner.screen.RunningRecordDetailScreen
import kr.co.dongnae.runner.screen.RunningRecordScreen
import kr.co.dongnae.runner.screen.RunningScreen
import kr.co.dongnae.runner.ui.theme.DongneRunnerTheme
import kr.co.dongnae.runner.viewModel.RunningViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var runningViewModel: RunningViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runningViewModel = ViewModelProvider(this)[RunningViewModel::class.java]
        setContent {
            DongnaeRunnerApp()
        }
    }
    override fun onDestroy() {
        super.onDestroy()

        // 앱 종료 시 기록 초기화 (저장 없이)
        if (::runningViewModel.isInitialized) {
            runningViewModel.clearRunningData()
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

                // NavGraph.kt 등
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
                composable("community/{uid}") { backStackEntry ->
                    val uid = backStackEntry.arguments?.getString("uid") ?: ""
                    CommunityScreen(navController = navController, uid = uid)
                }
            }
        }
    }
}