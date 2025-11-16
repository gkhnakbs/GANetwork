package com.gkhnakbs.ganetwork.responseWeather


import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class WeatherResponse<T>(
    val latitude: Double?,
    val longitude: Double?,
    val generationtime_ms: Double?,
    val utc_offset_seconds: Int?,
    val timezone: String?,
    val timezone_abbreviation: String?,
    val elevation: Double?,
    val current_units: T?,
    val current: Current?,
    val hourly_units: HourlyUnits?,
    val hourly: Hourly?
)