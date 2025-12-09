package com.gkhnakbs.ganetwork

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gkhnakbs.ganetwork.responseWeather.CurrentUnits
import com.gkhnakbs.ganetwork.responseWeather.WeatherResponse
import com.gkhnakbs.ganetwork.ui.theme.GANetwork3Theme
import com.gkhnakbs.gnetwork.extensions.get
import com.gkhnakbs.gnetwork.extensions.httpClient
import com.gkhnakbs.gnetwork.interceptor.LoggingInterceptor
import com.gkhnakbs.gnetwork.response.onSuccess
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        val client = httpClient {
            baseUrl = "https://api.open-meteo.com/"
            headers {
                this["accept"] = "*/*"
                this["accept-encoding"] = "gzip"
                this["accept-language"] = "en"
                this["connection"] = "Keep-Alive"
            }

            // addInterceptor(AuthInterceptor(headerName = "Authorization") { null /* ex. "Bearer token" */ })
            addInterceptor(
                LoggingInterceptor(
                    logger = { Log.d("GNetwork", it) },
                    level = LoggingInterceptor.Level.BODY
                )
            )
        }

        setContent {
            val response = remember { mutableStateOf<WeatherResponse<CurrentUnits>?>(null) }
            val isLoading = remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            GANetwork3Theme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LatLng -> ${response.value?.latitude.toString()} - ${response.value?.longitude.toString()}",
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Current Temperature -> " + response.value?.current?.temperature_2m.toString(),
                        textAlign = TextAlign.Center
                    )

                    if (isLoading.value) {
                        CircularWavyProgressIndicator(
                            waveSpeed = 5.dp
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading.value = true
                                val test =
                                    client.get<WeatherResponse<CurrentUnits>>("v1/forecast") {
                                        queryParam("latitude", "38.643976")
                                        queryParam("longitude", "34.734958")
                                        queryParam("hourly", "temperature_2m")
                                        queryParam("current", "temperature_2m,relative_humidity_2m")
                                    }


                                test.onSuccess {
                                    isLoading.value = false
                                    response.value = it
                                }
                            }
                        }
                    ) {
                        Text(
                            text = "GNetwork Test Get Weather",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
