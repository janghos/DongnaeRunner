// RunningScreen.kt
package kr.co.dongnae.runner.screen

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.MutableStateFlow

@SuppressLint("MissingPermission") // 위치 권한은 Activity 단에서 체크 필요
@Composable
fun RunningScreen(
    navController: NavController,
    uid: String,
    viewModel: RunningViewModel = hiltViewModel()
) {
    val context = navController.context as? Activity
    // ViewModel state
    val isRunning by viewModel.isRunning.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()

    viewModel.startLocationUpdates()
    val cameraPositionState = rememberCameraPositionState()

    // Update camera position when routePoints change
    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(routePoints.last(), 16f)
            )
        }
    }

    // Location updates are now managed internally by the ViewModel.

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 상단: 경과 시간
        Text(
            text = "⏱ 경과 시간: ${elapsedTime / 60}분 ${elapsedTime % 60}초",
            style = MaterialTheme.typography.headlineSmall
        )

        // 지도
        if (routePoints.isNotEmpty()) {
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                cameraPositionState = cameraPositionState
            ) {
                // 경로 Polyline
                Polyline(
                    points = routePoints,
                    color = Color.Red,
                    width = 8f
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("GPS 위치를 불러오는 중...")
            }
        }

        // 버튼 영역
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (isRunning && !isPaused) {
                Button(onClick = { viewModel.pauseRunning() }) {
                    Text("일시정지")
                }
            } else if (isRunning && isPaused) {
                Button(onClick = { viewModel.resumeRunning() }) {
                    Text("재개")
                }
            } else {
                Button(onClick = { viewModel.startRunning() }) {
                    Text("시작")
                }
            }

            Button(onClick = {
                viewModel.stopRunning()
                navController.popBackStack() // RunScreen으로 복귀
            }) {
                Text("종료")
            }
        }
    }
}


@Composable
fun rememberNavControllerStub(): NavController {
    return rememberNavController()
}