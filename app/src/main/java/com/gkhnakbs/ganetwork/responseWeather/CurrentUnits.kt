package com.gkhnakbs.ganetwork.responseWeather


import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class CurrentUnits(
    val time: String? = null,
    val interval: String? = null,
    val temperature_2m: String? = null,
    val relative_humidity_2m: String? = null
)