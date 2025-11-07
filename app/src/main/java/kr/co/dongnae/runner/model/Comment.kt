package kr.co.dongnae.runner.model

import com.google.firebase.Timestamp

data class Comment(
    val comment_id: String = "",
    val post_id: String = "",
    val user_id: String = "",
    val user_name: String = "",
    val content: String = "",
    val created_at: Timestamp = Timestamp.now(),
    val updated_at: Timestamp = Timestamp.now()
)

