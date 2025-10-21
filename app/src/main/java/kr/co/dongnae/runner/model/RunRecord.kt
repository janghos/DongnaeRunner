package kr.co.dongnae.runner.model

import com.google.firebase.Timestamp

data class RunRecord(
    val run_id: String = "",
    val user_id: String = "",
    val region: String = "",
    val start_time: Timestamp? = null,
    val end_time: Timestamp? = null,
    val duration_min: Double = 0.0,
    val distance_km: Double = 0.0,
    val pace_per_km: Double = 0.0,
    val heartbeat: Int? = null,
    val cadence: Int? = null,
    val routes_polyline: String = ""
)
