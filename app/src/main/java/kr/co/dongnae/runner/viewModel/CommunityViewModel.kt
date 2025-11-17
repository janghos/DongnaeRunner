package kr.co.dongnae.runner.viewModel

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kr.co.dongnae.runner.model.Comment
import kr.co.dongnae.runner.model.Post
import kr.co.dongnae.runner.model.RunListItem
import kr.co.dongnae.runner.util.LocationUtil
import javax.inject.Inject

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _comments = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
    val comments: StateFlow<Map<String, List<Comment>>> = _comments

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _runRecords = MutableStateFlow<List<RunListItem>>(emptyList())
    val runRecords: StateFlow<List<RunListItem>> = _runRecords

    private val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    /**
     * 현재 위치 기준 반경 5km 이내 게시글을 불러옵니다
     */
    fun loadNearbyPosts() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val location = LocationUtil.getCurrentLocation(context, fusedLocationClient)
                if (location == null) {
                    _error.value = "위치 정보를 가져올 수 없습니다"
                    _isLoading.value = false
                    return@launch
                }

                val currentLocation = GeoPoint(location.latitude, location.longitude)
                
                // Firestore에서 모든 게시글을 가져온 후 클라이언트에서 필터링
                // (Firestore의 GeoPoint 쿼리는 복잡하므로 클라이언트 필터링 사용)
                val snapshot = firestore.collection("posts")
                    .orderBy("created_at", Query.Direction.DESCENDING)
                    .limit(100)
                    .get()
                    .await()

                val allPosts = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Post::class.java)?.copy(post_id = doc.id)
                }

                // 반경 5km 이내 게시글만 필터링
                val nearbyPosts = allPosts.filter { post ->
                    post.location?.let { postLocation ->
                        val distance = LocationUtil.calculateDistance(
                            location.latitude,
                            location.longitude,
                            postLocation.latitude,
                            postLocation.longitude
                        )
                        distance <= 5.0 // 5km
                    } ?: false
                }

                _posts.value = nearbyPosts
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "게시글 불러오기 실패", e)
                _error.value = "게시글을 불러오는데 실패했습니다: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 사용자의 러닝 기록을 불러옵니다
     */
    fun loadRunRecords() {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            
            try {
                val snapshot = firestore.collection("runRecord")
                    .whereEqualTo("user_id", userId)
                    .orderBy("start_time", Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()

                val records = snapshot.documents.mapNotNull { doc ->
                    val totalSeconds = doc.getLong("duration_seconds")?.toInt() ?: 0
                    val hours = totalSeconds / 3600
                    val minutes = (totalSeconds % 3600) / 60
                    val seconds = totalSeconds % 60

                    RunListItem(
                        id = doc.id,
                        startTime = doc.getTimestamp("start_time"),
                        distanceKm = doc.getDouble("distance_km") ?: 0.0,
                        paceDisplay = doc.getString("pace_display") ?: "--'--",
                        runningTime = String.format("%d시간 %d분 %d초", hours, minutes, seconds)
                    )
                }

                _runRecords.value = records
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "러닝 기록 불러오기 실패", e)
            }
        }
    }

    /**
     * 게시글을 등록합니다
     */
    fun createPost(
        title: String,
        content: String,
        runRecordId: String? = null
    ) {
        viewModelScope.launch {
            val userId = currentUserId
            val userName = firebaseAuth.currentUser?.displayName ?: "익명"

            if (userId == null) {
                _error.value = "로그인이 필요합니다"
                return@launch
            }

            if (title.isBlank() || content.isBlank()) {
                _error.value = "제목과 내용을 입력해주세요"
                return@launch
            }

            _isLoading.value = true
            _error.value = null

            try {
                val location = LocationUtil.getCurrentLocation(context, fusedLocationClient)
                if (location == null) {
                    _error.value = "위치 정보를 가져올 수 없습니다"
                    _isLoading.value = false
                    return@launch
                }

                val region = LocationUtil.getRegionFromLocation(context, location)
                val postLocation = GeoPoint(location.latitude, location.longitude)

                // 러닝 기록 정보 가져오기
                var distanceKm: Double? = null
                var pacePerKm: Double? = null
                var durationMin: Double? = null
                var routesPolyline: String? = null

                if (runRecordId != null) {
                    try {
                        val runRecordDoc = firestore.collection("runRecord")
                            .document(runRecordId)
                            .get()
                            .await()

                        distanceKm = runRecordDoc.getDouble("distance_km")
                        pacePerKm = runRecordDoc.getDouble("pace_per_km")
                        val durationSeconds = runRecordDoc.getLong("duration_seconds")?.toDouble()
                        durationMin = durationSeconds?.div(60.0)
                        routesPolyline = runRecordDoc.getString("routes_polyline")
                    } catch (e: Exception) {
                        Log.e("CommunityViewModel", "러닝 기록 정보 가져오기 실패", e)
                    }
                }

                val post = Post(
                    user_id = userId,
                    user_name = userName,
                    title = title,
                    content = content,
                    region = region,
                    location = postLocation,
                    run_record_id = runRecordId,
                    distance_km = distanceKm,
                    pace_per_km = pacePerKm,
                    duration_min = durationMin,
                    routes_polyline = routesPolyline,
                    created_at = Timestamp.now(),
                    updated_at = Timestamp.now()
                )

                val docRef = firestore.collection("posts")
                    .add(post)
                    .await()

                // 문서 ID를 post_id 필드에 업데이트
                docRef.update("post_id", docRef.id).await()

                // 게시글 목록 새로고침
                loadNearbyPosts()
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "게시글 등록 실패", e)
                _error.value = "게시글 등록에 실패했습니다: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 게시글을 삭제합니다
     */
    fun deletePost(postId: String) {
        viewModelScope.launch {
            val userId = currentUserId
            if (userId == null) {
                _error.value = "로그인이 필요합니다"
                return@launch
            }

            _isLoading.value = true
            _error.value = null

            try {
                val postDoc = firestore.collection("posts").document(postId)
                val post = postDoc.get().await().toObject(Post::class.java)

                if (post?.user_id != userId) {
                    _error.value = "본인의 게시글만 삭제할 수 있습니다"
                    _isLoading.value = false
                    return@launch
                }

                // 댓글도 함께 삭제
                val commentsSnapshot = firestore.collection("comments")
                    .whereEqualTo("post_id", postId)
                    .get()
                    .await()

                commentsSnapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                }

                // 게시글 삭제
                postDoc.delete().await()

                // 게시글 목록 새로고침
                loadNearbyPosts()
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "게시글 삭제 실패", e)
                _error.value = "게시글 삭제에 실패했습니다: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 게시글의 댓글을 불러옵니다
     */
    fun loadComments(postId: String) {
        viewModelScope.launch {
            try {
                Log.d("CommunityViewModel", "댓글 불러오기 시작: postId=$postId")
                
                // orderBy 없이 먼저 시도 (인덱스 문제 방지)
                val snapshot = firestore.collection("comments")
                    .whereEqualTo("post_id", postId)
                    .get()
                    .await()

                Log.d("CommunityViewModel", "댓글 조회 결과: ${snapshot.documents.size}개")
                
                val commentList = snapshot.documents.mapNotNull { doc ->
                    val comment = doc.toObject(Comment::class.java)
                    Log.d("CommunityViewModel", "댓글 데이터: comment_id=${comment?.comment_id}, post_id=${comment?.post_id}, content=${comment?.content}")
                    comment?.copy(comment_id = doc.id)
                }.sortedBy { it.created_at }

                Log.d("CommunityViewModel", "매핑된 댓글: ${commentList.size}개")

                _comments.value = _comments.value.toMutableMap().apply {
                    put(postId, commentList)
                }
                
                Log.d("CommunityViewModel", "댓글 StateFlow 업데이트 완료: ${_comments.value[postId]?.size}개")
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "댓글 불러오기 실패", e)
                _error.value = "댓글을 불러오는데 실패했습니다: ${e.message}"
            }
        }
    }

    /**
     * 댓글을 등록합니다
     */
    fun createComment(postId: String, content: String) {
        viewModelScope.launch {
            val userId = currentUserId
            val userName = firebaseAuth.currentUser?.displayName ?: "익명"

            if (userId == null) {
                _error.value = "로그인이 필요합니다"
                return@launch
            }

            if (content.isBlank()) {
                _error.value = "댓글 내용을 입력해주세요"
                return@launch
            }

            _isLoading.value = true
            _error.value = null

            try {
                val comment = Comment(
                    post_id = postId,
                    user_id = userId,
                    user_name = userName,
                    content = content,
                    created_at = Timestamp.now(),
                    updated_at = Timestamp.now()
                )

                val docRef = firestore.collection("comments")
                    .add(comment)
                    .await()

                // 문서 ID를 comment_id 필드에 업데이트
                docRef.update("comment_id", docRef.id).await()

                // 댓글 수 업데이트
                firestore.collection("posts").document(postId)
                    .update("comment_count", com.google.firebase.firestore.FieldValue.increment(1))
                    .await()

                // 댓글 목록 새로고침
                loadComments(postId)
                loadNearbyPosts()
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "댓글 등록 실패", e)
                _error.value = "댓글 등록에 실패했습니다: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 댓글을 삭제합니다
     */
    fun deleteComment(postId: String, commentId: String) {
        viewModelScope.launch {
            val userId = currentUserId
            if (userId == null) {
                _error.value = "로그인이 필요합니다"
                return@launch
            }

            _isLoading.value = true
            _error.value = null

            try {
                val commentDoc = firestore.collection("comments").document(commentId)
                val comment = commentDoc.get().await().toObject(Comment::class.java)

                if (comment?.user_id != userId) {
                    _error.value = "본인의 댓글만 삭제할 수 있습니다"
                    _isLoading.value = false
                    return@launch
                }

                commentDoc.delete().await()

                // 댓글 수 업데이트
                firestore.collection("posts").document(postId)
                    .update("comment_count", com.google.firebase.firestore.FieldValue.increment(-1))
                    .await()

                // 댓글 목록 새로고침
                loadComments(postId)
                loadNearbyPosts()
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "댓글 삭제 실패", e)
                _error.value = "댓글 삭제에 실패했습니다: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}



