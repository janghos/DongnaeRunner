package kr.co.dongnae.runner.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

data class Post(
    val post_id: String = "",
    val user_id: String = "",
    val user_name: String = "",
    val title: String = "",
    val content: String = "",
    val region: String = "",
    val location: GeoPoint? = null, // 게시글 작성 위치
    val run_record_id: String? = null, // 연결된 러닝 기록 ID (선택사항)
    val distance_km: Double? = null, // 러닝 거리
    val pace_per_km: Double? = null, // 페이스
    val duration_min: Double? = null, // 러닝 시간
    val routes_polyline: String? = null, // 경로 polyline
    val created_at: Timestamp = Timestamp.now(),
    val updated_at: Timestamp = Timestamp.now(),
    val comment_count: Int = 0
)

