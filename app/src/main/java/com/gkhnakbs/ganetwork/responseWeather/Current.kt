package com.gkhnakbs.ganetwork.responseWeather


import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Current(
    val time: String?,
    val interval: Int?,
    val temperature_2m: Double?,
    val relative_humidity_2m: Int?
)