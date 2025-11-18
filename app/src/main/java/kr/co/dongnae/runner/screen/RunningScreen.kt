package kr.co.dongnae.runner.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()
    
    // 현재 지역 업데이트
    LaunchedEffect(Unit) {
        viewModel.updateCurrentRegion()
    }
    
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
        onStart =
            {
                viewModel.startRunning()

            },
        onPause = { viewModel.pauseRunning() },
        onResume = { viewModel.resumeRunning() },
        onStop = {
            viewModel.stopRunningAndSave(user!!.uid)
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
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val colorScheme = MaterialTheme.colorScheme
    val gradient = remember(colorScheme) {
        Brush.verticalGradient(
            colors = listOf(
                colorScheme.background,
                colorScheme.surfaceVariant,
                colorScheme.background
            )
        )
    }

    // routePoints가 변경될 때마다 카메라 위치를 업데이트합니다.
    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(routePoints.last(), 16f)
            )
        }
    }

    var isCountingDown by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableStateOf(3) }
    LaunchedEffect(isCountingDown) {
        if (isCountingDown) {
            for (i in 3 downTo 0) {
                countdownValue = i
                delay(1000)
            }
            isCountingDown = false
            onStart()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = String.format("%02d:%02d", elapsedTime / 60, elapsedTime % 60),
                    style = MaterialTheme.typography.displayMedium,
                    color = colorScheme.secondary
                )
                Text(
                    text = if (isRunning && !isPaused) "피치를 올려보세요!" else "러닝을 시작해보세요",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.onBackground
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "거리",
                    value = String.format("%.2f", distanceKm),
                    unit = "km"
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "페이스",
                    value = pace.ifBlank { "--'--" },
                    unit = "min/km"
                )
//                MetricTile(
//                    modifier = Modifier.weight(1f),
//                    label = "심박수",
//                    value = "000",
//                    unit = "bpm"
//                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                shape = RoundedCornerShape(32.dp),
                color = colorScheme.surfaceVariant,
                tonalElevation = 10.dp
            ) {
                if (routePoints.isNotEmpty()) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = false)
                    ) {
                        Polyline(
                            points = routePoints,
                            color = colorScheme.secondary,
                            width = 8f
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isCountingDown -> {
                                Text(
                                    text = countdownValue.toString(),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = colorScheme.secondary
                                )
                            }

                            countdownValue == 0 -> {
                                Text(
                                    text = "기록이 시작됩니다!",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = colorScheme.onBackground
                                )
                            }

                            isRunning -> {
                                CircularProgressIndicator(color = colorScheme.primary)
                            }

                            else -> {
                                Text(
                                    text = "시작 버튼을 눌러 러닝을 기록하세요",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = colorScheme.surfaceVariant,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isRunning) "러닝 상태" else "대기 상태",
                        style = MaterialTheme.typography.titleLarge,
                        color = colorScheme.onBackground
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RunnerButton(
                            modifier = Modifier.weight(1f),
                            text = when {
                                isRunning && !isPaused -> "일시정지"
                                isRunning && isPaused -> "재개"
                                else -> "시작"
                            },
                            onClick = {
                                when {
                                    isRunning && !isPaused -> onPause()
                                    isRunning && isPaused -> onResume()
                                    else -> {
                                        countdownValue = 3
                                        isCountingDown = true
                                    }
                                }
                            }
                        )

                        RunnerButton(
                            modifier = Modifier.weight(1f),
                            text = "종료",
                            onClick = {
                                coroutineScope.launch {
                                    countdownValue = 3
                                    onStop()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun rememberNavControllerStub(): NavController {
    return rememberNavController()
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        color = colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onBackground
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RunnerButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 0.dp
    ) {
        androidx.compose.material3.Button(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            onClick = onClick
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
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