package kr.co.dongnae.runner.screen

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.annotations.concurrent.Background
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.firebase.firestore.FirebaseFirestore
import kr.co.dongnae.runner.model.RunListItem
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SimpleDateFormat")
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

    LaunchedEffect(runId) {
        db.collection("runRecord").document(runId).get()
            .addOnSuccessListener { d ->
                val start = d.getTimestamp("start_time")?.toDate()?.time ?: 0L
                val end = d.getTimestamp("end_time")?.toDate()?.time ?: 0L
                val diff = end - start
                val totalSeconds = diff / 1000
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("러닝 상세 기록") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (runItem == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("기록이 존재하지 않습니다.")
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text("시작 시간: " + SimpleDateFormat("yyyy.MM.dd HH:mm").format(runItem!!.startTime?.toDate() ?: Date()))
                Spacer(modifier = Modifier.height(8.dp))
                Text("러닝 시간: ${runItem!!.runningTime}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("이동 거리: ${String.format("%.2f", runItem!!.distanceKm)} km")
                Spacer(modifier = Modifier.height(8.dp))
                Text("페이스: ${runItem!!.paceDisplay}")
                Spacer(modifier = Modifier.height(16.dp))

                if (routePoints.isNotEmpty()) {
                    GoogleMap(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        cameraPositionState = cameraPositionState
                    ) {
                        Polyline(points = routePoints, color = Color.Red, width = 6f)
                    }

                    LaunchedEffect(routePoints) {
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(routePoints.first(), 16f))
                    }
                }
            }
        }
    }
}