package kr.co.dongnae.runner.screen

import android.util.Log
import android.util.Log.e
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kr.co.dongnae.runner.model.RunListItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningRecordScreen(
    navController: NavController,
    uid: String
) {
    var items by remember { mutableStateOf<List<RunListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(uid) {
        FirebaseFirestore.getInstance()
            .collection("runRecord")
            .whereEqualTo("user_id", uid)
            .orderBy("start_time", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { qs ->
                items = qs.documents.map { d ->
                    val startTime = d.getTimestamp("start_time")!!.toDate()!!.time
                    val endTime = d.getTimestamp("end_time")!!.toDate()!!.time
                    val diff = endTime - startTime
                    val totalSeconds = diff/1000
                    val hours = totalSeconds / 3600
                    val minutes = (totalSeconds % 3600) / 60
                    val seconds = totalSeconds % 60

                        RunListItem(
                        id = d.id,
                        startTime = d.getTimestamp("start_time"),
                        distanceKm = (d.getDouble("distance_km") ?: 0.0),
                        paceDisplay = (d.getString("pace_display") ?: "--'--"),
                        runningTime = String.format("%d시간 %d분 %d초", hours, minutes, seconds)
                    )
                }
                loading = false
            }
            .addOnFailureListener { e ->
                loading = false
                Log.e("RunningRecordScreen", "query failed", e)
            }
    }

    RunningRecordContent(
        loading = loading,
        items = items,
        onBack = { navController.popBackStack() },
        onClickItem = { item ->
            navController.navigate("recordDetail/${item.id}")
        }
    )
}

@Composable
private fun RunListRow(item: RunListItem, onClick: () -> Unit) {
    val dateText = item.startTime?.toDate()?.let {
        SimpleDateFormat("yyyy.MM.dd (E) HH:mm", Locale.getDefault()).format(it)
    } ?: "-"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {

            Row(modifier = Modifier
            ){
                Text(dateText + "  /  ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(item.runningTime, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }

            Text(
                text = "거리 ${String.format(Locale.getDefault(), "%.2f", item.distanceKm)} km",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = item.paceDisplay,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============ 2) UI 전용 컴포저블 (프리뷰/실행 공용) ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningRecordContent(
    loading: Boolean,
    items: List<RunListItem>,
    onBack: () -> Unit,
    onClickItem: (RunListItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 러닝 기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) { CircularProgressIndicator() }
            }
            items.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) { Text("기록이 없습니다.") }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    items(items) { item ->
                        RunListRow(item = item) { onClickItem(item) }
                        Divider()
                    }
                }
            }
        }
    }
}

// ============ 3) 프리뷰들 ============

@Preview(showBackground = true, name = "기록 목록 – 로딩")
@Composable
fun RunningRecordLoadingPreview() {
    RunningRecordContent(
        loading = true,
        items = emptyList(),
        onBack = {},
        onClickItem = {}
    )
}

@Preview(showBackground = true, name = "기록 목록 – 빈 상태")
@Composable
fun RunningRecordEmptyPreview() {
    RunningRecordContent(
        loading = false,
        items = emptyList(),
        onBack = {},
        onClickItem = {}
    )
}

@Preview(showBackground = true, name = "기록 목록 – 데이터 있음")
@Composable
fun RunningRecordWithDataPreview() {
    val mockItems = listOf(
        RunListItem(
            id = "run_001",
            startTime = Timestamp(Date()),
            distanceKm = 5.24,
            paceDisplay = "5'12\"",
            runningTime = "1시간 50분 23초"
        ),
        RunListItem(
            id = "run_002",
            startTime = Timestamp(Date(System.currentTimeMillis() - 86_400_000L)),
            distanceKm = 10.03,
            paceDisplay = "4'58\"",
            runningTime = "1시간 50분 23초"
        )
    )
    RunningRecordContent(
        loading = false,
        items = mockItems,
        onBack = {},
        onClickItem = {}
    )
}