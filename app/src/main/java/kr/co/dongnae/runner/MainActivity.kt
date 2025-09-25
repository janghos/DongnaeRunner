package kr.co.dongnae.runner

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    NavHost(navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController) }
        composable("login") { LoginScreen(navController) }
        composable(
            route = "run/{uid}",
            arguments = listOf(navArgument("uid") { defaultValue = "" })
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid") ?: ""
            RunScreen(navController, uid)
        }
    }
}