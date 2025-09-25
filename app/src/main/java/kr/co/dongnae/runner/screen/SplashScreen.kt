// SplashScreen.kt
package kr.co.dongnae.runner.presentation.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kr.co.dongnae.runner.R

@Composable
fun SplashScreen(navController: NavController) {
    LaunchedEffect(Unit) {
        delay(3000)
        navController.navigate("login") {
            popUpTo("splash") { inclusive = true }
        }
    }

    SplashContent()
}

@Composable
fun SplashContent() {

    var logoResId by remember { mutableStateOf(R.drawable.dongnaerun_icon_1) }

    LaunchedEffect(Unit) {
        delay(1000)
        logoResId = R.drawable.dongnaerun_icon_2
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = logoResId),
                contentDescription = "app logo",
                modifier = Modifier
                    .size(120.dp).padding(bottom = 16.dp)
            )
            Text(
                text = "Dongnae Running",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    SplashContent()
}