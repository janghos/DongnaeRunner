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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kr.co.dongnae.runner.model.FirestoreUser
import kr.co.dongnae.runner.viewModel.RunningViewModel


// ViewModel과 Navigation 로직을 담당하는 상위 컴포저블
@SuppressLint("MissingPermission") // 위치 권한은 Activity 단에서 체크 필요
@Composable
fun RunningScreen(
    navController: NavController,
    uid: String,
    viewModel: RunningViewModel = hiltViewModel()
) {
    // ViewModel state
    val isRunning by viewModel.isRunning.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val distanceKm by viewModel.distanceKm.collectAsState()
    val pace by viewModel.pace.collectAsState()

    var user by remember { mutableStateOf<FirestoreUser?>(null) }
    var isLoadingUser by remember { mutableStateOf(true) }

    // Firestore에서 유저 정보 로드
    LaunchedEffect(uid) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    user = doc.toObject(FirestoreUser::class.java)
                }
                isLoadingUser = false
            }
            .addOnFailureListener {
                isLoadingUser = false
            }
    }

    if (isLoadingUser) {
        // 유저 정보 로딩 중
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (user == null) {
        // 유저 정보가 없을 경우
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("사용자 정보를 불러오지 못했습니다.")
        }
        return
    }



    RunningContent(
        isRunning = isRunning,
        isPaused = isPaused,
        elapsedTime = elapsedTime,
        routePoints = routePoints,
        distanceKm = distanceKm, // 전달
        pace = pace,             // 전달
        onStart = { viewModel.startRunning() },
        onPause = { viewModel.pauseRunning() },
        onResume = { viewModel.resumeRunning() },
        onStop = {
            viewModel.stopRunningAndSave(user!!.uid, user!!.region, null)
        }
    )
}

// 순수 UI를 담당하며, 모든 상태와 이벤트 핸들러를 매개변수로 받습니다. (프리뷰 용이)
@Composable
fun RunningContent(
    isRunning: Boolean,
    isPaused: Boolean,
    elapsedTime: Int,
    routePoints: List<LatLng>,
    distanceKm: Double, // 추가
    pace: String,       // 추가
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val cameraPositionState = rememberCameraPositionState()

    // routePoints가 변경될 때마다 카메라 위치를 업데이트합니다.
    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(routePoints.last(), 16f)
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // [START] 상단: 경과 시간, 거리, 페이스 표시
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 경과 시간
            Text(
                text = "⏱ 경과 시간: ${elapsedTime / 60}분 ${elapsedTime % 60}초",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 거리 (소수점 둘째 자리까지 표시)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "거리 (km)", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = String.format("%.2f", distanceKm),
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                // 페이스
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "페이스 (min/km)", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = pace,
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                //심박수
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "심박수", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "000",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        // [END] 상단

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
                Button(onClick = onPause) {
                    Text("일시정지")
                }
            } else if (isRunning && isPaused) {
                Button(onClick = onResume) {
                    Text("재개")
                }
            } else {
                Button(onClick = onStart) {
                    Text("시작")
                }
            }

            Button(onClick = onStop) {
                Text("종료")
            }
        }
    }
}


@Composable
fun rememberNavControllerStub(): NavController {
    return rememberNavController()
}

// --- RunningContent 상태별 Preview ---

@Preview(showBackground = true, name = "1. Running Content (Initial/Loading)")
@Composable
fun RunningContentInitialPreview() {
    RunningContent(
        isRunning = false,
        isPaused = false,
        elapsedTime = 0,
        routePoints = emptyList(), // No points, shows loading text
        distanceKm = 0.0,          // 추가: 0.0 km
        pace = "--'--",           // 추가: 초기값
        onStart = {},
        onPause = {},
        onResume = {},
        onStop = {}
    )
}

@Preview(showBackground = true, name = "2. Running Content (Active)")
@Composable
fun RunningContentActivePreview() {
    val mockRoute = listOf(
        LatLng(37.5665, 126.9780), // 서울 시청 근처
        LatLng(37.5675, 126.9790),
        LatLng(37.5685, 126.9800)
    )
    RunningContent(
        isRunning = true,
        isPaused = false,
        elapsedTime = 125, // 2분 5초
        routePoints = mockRoute,
        distanceKm = 0.45,         // 추가: 0.45 km
        pace = "4'30''",           // 추가: mock 페이스
        onStart = {},
        onPause = {},
        onResume = {},
        onStop = {}
    )
}

@Preview(showBackground = true, name = "3. Running Content (Paused)")
@Composable
fun RunningContentPausedPreview() {
    val mockRoute = listOf(
        LatLng(37.5665, 126.9780),
        LatLng(37.5675, 126.9790),
        LatLng(37.5685, 126.9800)
    )
    RunningContent(
        isRunning = true,
        isPaused = true,
        elapsedTime = 185, // 3분 5초
        routePoints = mockRoute,
        distanceKm = 0.85,         // 추가: 0.85 km
        pace = "4'54''",           // 추가: mock 페이스
        onStart = {},
        onPause = {},
        onResume = {},
        onStop = {}
    )
}