package kr.co.dongnae.runner.model

import com.google.firebase.Timestamp


data class RunListItem(
    val id: String,
    val startTime: Timestamp?,
    val distanceKm: Double,
    val paceDisplay: String
)