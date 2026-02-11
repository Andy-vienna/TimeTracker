package org.fx.timetracker

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class NetworkClient {

    private val client = OkHttpClient()
    private val tag = "NetworkClient"

    suspend fun sendEvent(url: String, secretKey: String, jsonPayload: String): Boolean {
        if (url.isBlank() || secretKey.isBlank()) {
            Log.e(tag, "URL or Secret Key is blank. Aborting sendEvent.")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonPayload.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .header("X-API-KEY", secretKey)
                    .post(requestBody)
                    .build()

                Log.d(tag, "--> POST $url")
                Log.d(tag, "Payload: $jsonPayload")

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body.string() // Read body once
                    Log.d(tag, "<-- ${response.code} $url")
                    Log.d(tag, "Response: $responseBody")
                    response.isSuccessful
                }
            } catch (e: IOException) {
                Log.e(tag, "Failed to send event", e)
                false
            }
        }
    }

    @Suppress("unused") // This function is currently not used, but kept for future features.
    suspend fun fetch(url: String, secretKey: String): String? {
        if (url.isBlank() || secretKey.isBlank()) {
            Log.e(tag, "URL or Secret Key is blank. Aborting fetch.")
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("X-API-KEY", secretKey)
                    .get()
                    .build()

                Log.d(tag, "--> GET $url")

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()
                    Log.d(tag, "<-- ${response.code} $url")
                    Log.d(tag, "Response: $responseBody")
                    if (response.isSuccessful) {
                        responseBody
                    } else {
                        null
                    }
                }
            } catch (e: IOException) {
                Log.e(tag, "Failed to fetch data", e)
                null
            }
        }
    }
}
