package org.fx.timetracker

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fx.timetracker.ui.theme.TimeTrackerTheme
import org.json.JSONObject

class OnlinePreviewActivity : ComponentActivity() {

    private val networkClient = NetworkClient()
    private var events = mutableStateListOf<OnlineEvent>()
    private var isLoading = mutableStateOf(false)
    private var errorMessage = mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        loadOnlineData()

        setContent {
            TimeTrackerTheme {
                val darkIcons = !isSystemInDarkTheme()
                DisposableEffect(darkIcons) {
                    val window = (this@OnlinePreviewActivity as Activity).window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = darkIcons
                    onDispose {}
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Online Daten") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = Color.White)
                                }
                            },
                            actions = {
                                IconButton(onClick = { loadOnlineData() }) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Aktualisieren", tint = Color.White)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = Color.White
                            )
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isLoading.value) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            } else if (errorMessage.value != null) {
                                Text(
                                    text = errorMessage.value!!,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                                )
                            } else if (events.isEmpty()) {
                                Text(
                                    text = "Keine Daten online gefunden.",
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                    items(events) { event ->
                                        OnlineEventItem(event)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun OnlineEventItem(event: OnlineEvent) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val displayText = when (event.event) {
                    "IN" -> "KOMMT"
                    "BREAK_START" -> "PAUSE START"
                    "BREAK_END" -> "PAUSE ENDE"
                    "OUT" -> "GEHT"
                    else -> event.event
                }
                Text(text = displayText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(text = event.ts.split("T").getOrElse(1) { "" }.take(8), fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Datum: ${event.ts.split("T").firstOrNull() ?: ""}", fontSize = 14.sp)
            Text(text = "Quelle: ${event.source} (${event.deviceId.take(8)}...)", fontSize = 12.sp, color = Color.Gray)
        }
    }

    private fun loadOnlineData() {
        val prefs = getSharedPreferences("fx", MODE_PRIVATE)
        var baseUrl = prefs.getString("server_url", "") ?: ""
        val user = prefs.getString("username", "") ?: ""
        val key = prefs.getString("secret_key", "") ?: ""

        if (baseUrl.isBlank() || user.isBlank() || key.isBlank()) {
            errorMessage.value = "Konfiguration unvollständig!"
            return
        }

        // URL anpassen: Wir nutzen jetzt 'getevents.php'
        val fetchUrl = if (baseUrl.contains(".php")) {
            baseUrl.substringBeforeLast("/") + "/getevents.php?username=$user"
        } else {
            val suffix = if (baseUrl.endsWith("/")) "" else "/"
            baseUrl + suffix + "getevents.php?username=$user"
        }

        isLoading.value = true
        errorMessage.value = null
        events.clear()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val responseJson = networkClient.fetch(fetchUrl, key)
                withContext(Dispatchers.Main) {
                    if (responseJson != null) {
                        try {
                            val json = JSONObject(responseJson)
                            if (json.optBoolean("success", false)) {
                                val dataArray = json.getJSONArray("data")
                                for (i in 0 until dataArray.length()) {
                                    val obj = dataArray.getJSONObject(i)
                                    events.add(
                                        OnlineEvent(
                                            id = obj.getString("id"),
                                            event = obj.getString("event"),
                                            username = obj.getString("username"),
                                            source = obj.getString("source"),
                                            deviceId = obj.getString("deviceid"),
                                            tz = obj.getString("tz"),
                                            ts = obj.getString("ts")
                                        )
                                    )
                                }
                            } else {
                                errorMessage.value = json.optString("error", "Unbekannter Fehler")
                            }
                        } catch (e: Exception) {
                            Log.e("OnlinePreview", "Parsing error", e)
                            errorMessage.value = "Fehler beim Verarbeiten der Daten."
                        }
                    } else {
                        errorMessage.value = "Server nicht erreichbar oder Zugriff verweigert."
                    }
                    isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage.value = "Netzwerkfehler: ${e.message}"
                    isLoading.value = false
                }
            }
        }
    }
}
