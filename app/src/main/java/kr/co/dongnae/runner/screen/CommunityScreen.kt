package kr.co.dongnae.runner.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kr.co.dongnae.runner.model.Comment
import kr.co.dongnae.runner.model.Post
import kr.co.dongnae.runner.model.RunListItem
import kr.co.dongnae.runner.viewModel.CommunityViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    navController: NavController,
    uid: String,
    viewModel: CommunityViewModel = hiltViewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var showPostDialog by remember { mutableStateOf(false) }
    var selectedPost by remember { mutableStateOf<Post?>(null) }
    val runRecords by viewModel.runRecords.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadNearbyPosts()
        viewModel.loadRunRecords()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("커뮤니티") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = { showPostDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "게시글 작성")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && posts.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (posts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "반경 5km 이내 게시글이 없습니다",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(posts) { post ->
                        PostItem(
                            post = post,
                            onPostClick = {
                                selectedPost = post
                                viewModel.loadComments(post.post_id)
                            },
                            onDeleteClick = {
                                viewModel.deletePost(post.post_id)
                            },
                            isOwner = post.user_id == uid
                        )
                    }
                }
            }

            error?.let {
                LaunchedEffect(it) {
                    // 에러 처리 (Toast 등으로 표시 가능)
                }
            }
        }
    }

    // 게시글 작성 다이얼로그
    if (showPostDialog) {
        PostCreateDialog(
            runRecords = runRecords,
            onDismiss = { showPostDialog = false },
            onCreatePost = { title, content, runRecordId ->
                viewModel.createPost(title, content, runRecordId)
                showPostDialog = false
            }
        )
    }

    // 게시글 상세 다이얼로그
    selectedPost?.let { post ->
        // 게시글 선택 시 댓글 불러오기
        LaunchedEffect(post.post_id) {
            android.util.Log.d("CommunityScreen", "게시글 선택됨: postId=${post.post_id}")
            viewModel.loadComments(post.post_id)
        }
        
        PostDetailDialog(
            post = post,
            comments = comments[post.post_id] ?: emptyList(),
            viewModel = viewModel,
            onDismiss = { selectedPost = null },
            onDeletePost = {
                viewModel.deletePost(post.post_id)
                selectedPost = null
            },
            onDeleteComment = { commentId ->
                viewModel.deleteComment(post.post_id, commentId)
            },
            onCreateComment = { content ->
                android.util.Log.d("CommunityScreen", "댓글 작성 요청: postId=${post.post_id}, content=$content")
                viewModel.createComment(post.post_id, content)
            },
            currentUserId = uid
        )
    }
}

@Composable
fun PostItem(
    post: Post,
    onPostClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isOwner: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPostClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${post.user_name} · ${post.region}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isOwner) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3
            )

            // 러닝 기록 정보 표시
            post.distance_km?.let { distance ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "거리: ${String.format("%.2f", distance)}km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    post.pace_per_km?.let { pace ->
                        Text(
                            text = "페이스: ${formatPace(pace)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    post.duration_min?.let { duration ->
                        Text(
                            text = "시간: ${formatDuration(duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDate(post.created_at),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "댓글 ${post.comment_count}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PostDetailDialog(
    post: Post,
    comments: List<Comment>,
    viewModel: CommunityViewModel,
    onDismiss: () -> Unit,
    onDeletePost: () -> Unit,
    onDeleteComment: (String) -> Unit,
    onCreateComment: (String) -> Unit,
    currentUserId: String
) {
    var commentText by remember { mutableStateOf("") }
    val commentsState by viewModel.comments.collectAsState()
    val currentComments = commentsState[post.post_id] ?: comments
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // 지도 경로 디코딩
    val routePoints = remember(post.routes_polyline) {
        if (post.routes_polyline.isNullOrBlank()) {
            emptyList<LatLng>()
        } else {
            try {
                PolyUtil.decode(post.routes_polyline)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState()
    
    // Maps 초기화
    LaunchedEffect(Unit) {
        try {
            MapsInitializer.initialize(context)
        } catch (e: Exception) {
            android.util.Log.e("PostDetailDialog", "Maps 초기화 실패", e)
        }
    }
    
    // 지도 카메라 위치 설정
    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            try {
                val boundsBuilder = LatLngBounds.builder()
                routePoints.forEach(boundsBuilder::include)
                val bounds = boundsBuilder.build()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            } catch (e: Exception) {
                android.util.Log.e("PostDetailDialog", "카메라 업데이트 실패", e)
                try {
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(routePoints.first(), 15f))
                } catch (e2: Exception) {
                    android.util.Log.e("PostDetailDialog", "카메라 이동 실패", e2)
                }
            }
        }
    }
    
    // 디버깅용 로그
    LaunchedEffect(currentComments.size, post.post_id) {
        android.util.Log.d("PostDetailDialog", "댓글 업데이트: postId=${post.post_id}, 댓글 개수=${currentComments.size}")
        currentComments.forEachIndexed { index, comment ->
            android.util.Log.d("PostDetailDialog", "댓글[$index]: ${comment.content}, comment_id=${comment.comment_id}")
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${post.user_name} · ${post.region}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (post.user_id == currentUserId) {
                            TextButton(onClick = onDeletePost) {
                                Text("삭제", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = onDismiss) {
                            Text("닫기")
                        }
                    }
                }
                
                Divider()
                
                // 내용 스크롤 영역
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = post.content,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // 러닝 기록 정보
                    post.distance_km?.let { distance ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "러닝 기록",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "거리: ${String.format("%.2f", distance)}km",
                                style = MaterialTheme.typography.bodySmall
                            )
                            post.pace_per_km?.let { pace ->
                                Text(
                                    text = "페이스: ${formatPace(pace)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            post.duration_min?.let { duration ->
                                Text(
                                    text = "시간: ${formatDuration(duration)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // 지도 경로 표시
                    if (routePoints.isNotEmpty()) {
                        Divider()
                        Text(
                            text = "러닝 경로",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState,
                                uiSettings = MapUiSettings(
                                    zoomControlsEnabled = true,
                                    compassEnabled = false
                                )
                            ) {
                                Polyline(
                                    points = routePoints,
                                    color = MaterialTheme.colorScheme.primary,
                                    width = 8f
                                )
                            }
                        }
                    }

                    Divider()

                    Text(
                        text = "댓글 (${currentComments.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentComments) { comment ->
                            CommentItem(
                                comment = comment,
                                onDelete = {
                                    if (comment.user_id == currentUserId) {
                                        onDeleteComment(comment.comment_id)
                                    }
                                },
                                isOwner = comment.user_id == currentUserId
                            )
                        }
                    }
                }
                
                Divider()
                
                // 댓글 입력 영역
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { 
                            commentText = it
                            // 키보드가 올라올 때 스크롤 위치 조정
                            coroutineScope.launch {
                                delay(100)
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        },
                        label = { Text("댓글 입력") },
                        modifier = Modifier.weight(1f),
                        maxLines = 3
                    )
                    Button(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                onCreateComment(commentText)
                                commentText = ""
                            }
                        }
                    ) {
                        Text("작성")
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: Comment,
    onDelete: () -> Unit,
    isOwner: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comment.user_name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatDate(comment.created_at),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isOwner) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "삭제",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun PostCreateDialog(
    runRecords: List<RunListItem>,
    onDismiss: () -> Unit,
    onCreatePost: (String, String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedRunRecord by remember { mutableStateOf<RunListItem?>(null) }
    var showRunRecordSelector by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "게시글 작성",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text("취소")
                    }
                }
                
                Divider()
                
                // 입력 영역
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("제목") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("내용") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        maxLines = 15,
                        minLines = 10
                    )
                    
                    // 러닝 기록 공유 선택
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedRunRecord != null) {
                                "러닝 기록: ${String.format("%.2f", selectedRunRecord!!.distanceKm)}km"
                            } else {
                                "러닝 기록 공유 (선택사항)"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectedRunRecord != null) {
                                TextButton(onClick = { selectedRunRecord = null }) {
                                    Text("제거")
                                }
                            }
                            TextButton(onClick = { showRunRecordSelector = true }) {
                                Text(if (selectedRunRecord == null) "선택" else "변경")
                            }
                        }
                    }
                }
                
                Divider()
                
                // 작성 버튼
                Button(
                    onClick = {
                        if (title.isNotBlank() && content.isNotBlank()) {
                            onCreatePost(title, content, selectedRunRecord?.id)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    enabled = title.isNotBlank() && content.isNotBlank()
                ) {
                    Text("작성", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    // 러닝 기록 선택 다이얼로그
    if (showRunRecordSelector) {
        AlertDialog(
            onDismissRequest = { showRunRecordSelector = false },
            title = { Text("러닝 기록 선택") },
            text = {
                if (runRecords.isEmpty()) {
                    Text(
                        text = "공유할 러닝 기록이 없습니다",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(runRecords) { record ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedRunRecord = record
                                        showRunRecordSelector = false
                                    },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${String.format("%.2f", record.distanceKm)}km · ${record.paceDisplay}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = record.runningTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    record.startTime?.toDate()?.let { date ->
                                        Text(
                                            text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRunRecordSelector = false }) {
                    Text("취소")
                }
            }
        )
    }
}

fun formatDate(timestamp: com.google.firebase.Timestamp): String {
    val date = timestamp.toDate()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(date)
}

fun formatPace(pacePerKm: Double): String {
    val minutes = (pacePerKm / 60).toInt()
    val seconds = (pacePerKm % 60).toInt()
    return String.format("%d'%02d\"", minutes, seconds)
}

fun formatDuration(durationMin: Double): String {
    val hours = (durationMin / 60).toInt()
    val minutes = (durationMin % 60).toInt()
    return if (hours > 0) {
        String.format("%d시간 %d분", hours, minutes)
    } else {
        String.format("%d분", minutes)
    }
}

@Preview(showBackground = true)
@Composable
fun PostItemPreview() {
    val mockPost = Post(
        post_id = "post_001",
        user_id = "user_001",
        user_name = "홍길동",
        title = "오늘의 러닝 기록",
        content = "오늘 5km를 달렸습니다. 날씨가 좋아서 기분이 좋았어요!",
        region = "서울특별시",
        distance_km = 5.24,
        pace_per_km = 300.0,
        duration_min = 26.2,
        created_at = com.google.firebase.Timestamp.now(),
        comment_count = 3
    )
    PostItem(
        post = mockPost,
        onPostClick = {},
        onDeleteClick = {},
        isOwner = true
    )
}

@Preview(showBackground = true)
@Composable
fun CommentItemPreview() {
    val mockComment = Comment(
        comment_id = "comment_001",
        post_id = "post_001",
        user_id = "user_002",
        user_name = "김철수",
        content = "정말 멋진 러닝이네요!",
        created_at = com.google.firebase.Timestamp.now()
    )
    CommentItem(
        comment = mockComment,
        onDelete = {},
        isOwner = false
    )
}


