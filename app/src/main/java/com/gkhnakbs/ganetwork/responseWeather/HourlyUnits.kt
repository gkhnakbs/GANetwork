package com.gkhnakbs.ganetwork.responseWeather


import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class HourlyUnits(
    val time: String?,
    val temperature_2m: String?
)