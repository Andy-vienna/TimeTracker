package org.fx.timetracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.net.ssl.SSLHandshakeException

class SettingsViewModel : ViewModel() {

    private val _isCertificateTrusted = MutableStateFlow(true)
    val isCertificateTrusted = _isCertificateTrusted.asStateFlow()

    private var validationJob: Job? = null

    fun validateServerUrl(serverUrl: String, username: String) {
        validationJob?.cancel()

        if (!serverUrl.startsWith("https://") || username.isBlank()) {
            _isCertificateTrusted.value = true
            return
        }

        validationJob = viewModelScope.launch {
            delay(500L) // Debounce

            try {
                // Create a new, temporary client for each test to bypass any caching.
                val testClient = OkHttpClient()
                val testUrl = "$serverUrl/api/last-event?username=$username"
                val request = Request.Builder().get().url(testUrl).build()

                testClient.newCall(request).execute().use { 
                    _isCertificateTrusted.value = true
                }
            } catch (_: SSLHandshakeException) {
                _isCertificateTrusted.value = false
            } catch (_: Exception) {
                _isCertificateTrusted.value = true
            }
        }
    }
}
