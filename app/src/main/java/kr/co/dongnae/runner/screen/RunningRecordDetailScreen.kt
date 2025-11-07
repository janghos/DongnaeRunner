package kr.co.dongnae.runner.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.firebase.firestore.FirebaseFirestore
import kr.co.dongnae.runner.model.RunListItem
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningRecordDetailScreen(
    navController: NavController,
    runId: String,
) {
    val db = FirebaseFirestore.getInstance()
    var loading by remember { mutableStateOf(true) }
    var runItem by remember { mutableStateOf<RunListItem?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    val cameraPositionState = rememberCameraPositionState()
    val scrollState = rememberScrollState()
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(runId) {
        loading = true
        db.collection("runRecord").document(runId).get()
            .addOnSuccessListener { d ->
                val totalSeconds = d.getLong("duration_seconds")?.toInt() ?: 0
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60

                runItem = RunListItem(
                    id = d.id,
                    startTime = d.getTimestamp("start_time"),
                    distanceKm = d.getDouble("distance_km") ?: 0.0,
                    paceDisplay = d.getString("pace_display") ?: "--'--",
                    runningTime = String.format("%d시간 %d분 %d초", hours, minutes, seconds)
                )

                val polyline = d.getString("routes_polyline") ?: ""
                routePoints = PolyUtil.decode(polyline)

                loading = false
            }
            .addOnFailureListener { e ->
                loading = false
                Log.e("RunningRecordDetail", "load failed", e)
            }
    }

    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            runCatching {
                val boundsBuilder = LatLngBounds.builder()
                routePoints.forEach(boundsBuilder::include)
                val bounds = boundsBuilder.build()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            }.onFailure {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(routePoints.first(), 15f))
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "러닝 기록",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(padding)
        ) {
            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                }

                runItem == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "기록이 존재하지 않습니다.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    val run = runItem!!
                    val formattedStart = run.startTime?.toDate()?.let(dateFormatter::format) ?: "기록 시간 정보 없음"
                    val distanceLabel = String.format(Locale.getDefault(), "%.2f", run.distanceKm)
                    val distanceMeters = String.format(Locale.getDefault(), "%.0f m", run.distanceKm * 1000)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "총 이동 거리",
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = distanceLabel,
                                style = MaterialTheme.typography.displayMedium,
                                color = colorScheme.secondary
                            )
                            Text(
                                text = "킬로미터",
                                style = MaterialTheme.typography.headlineMedium,
                                color = colorScheme.onBackground
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            StatTile(
                                label = "러닝 시간",
                                value = run.runningTime,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "평균 페이스",
                                value = run.paceDisplay,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            shape = RoundedCornerShape(28.dp),
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
                                    Text(
                                        text = "기록된 경로가 없습니다.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = colorScheme.surfaceVariant,
                            tonalElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                InfoRow(label = "시작 시각", value = formattedStart)
                                Divider(color = colorScheme.outline.copy(alpha = 0.4f))
                                InfoRow(label = "총 거리", value = "$distanceLabel km ($distanceMeters)")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onBackground
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StatTilePreview() {
    StatTile(
        label = "러닝 시간",
        value = "1시간 30분 45초"
    )
}

@Preview(showBackground = true)
@Composable
fun InfoRowPreview() {
    InfoRow(
        label = "시작 시각",
        value = "2024.01.15 07:30"
    )
}