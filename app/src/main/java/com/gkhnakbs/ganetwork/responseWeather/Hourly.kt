package com.gkhnakbs.ganetwork.responseWeather


import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Hourly(
    val time: List<String?>?,
    val temperature_2m: List<Double?>?
)