package kr.co.dongnae.runner.model

import com.google.firebase.Timestamp

data class FirestoreUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val region: String = "",
    val created_at: Timestamp = Timestamp.now(),
    val last_login: Timestamp = Timestamp.now()
)