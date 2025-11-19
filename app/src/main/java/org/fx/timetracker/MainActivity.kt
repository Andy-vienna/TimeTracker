package org.fx.timetracker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.fx.timetracker.ui.theme.TimeTrackerTheme
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.content.edit

class MainActivity : ComponentActivity() {

    private val statusText = mutableStateOf("Bereit.")
    private val serverUrl = mutableStateOf("")
    private val username = mutableStateOf("")
    private val pendingEventCount = mutableIntStateOf(0)
    private val lastEventKind = mutableStateOf("")
    private val lastEventTimestamp = mutableStateOf("")

    // Use the central OkHttpClient instance from the Application
    private val client: OkHttpClient by lazy { (application as MainApplication).httpClient }

    private val timeEventDao by lazy { (application as MainApplication).database.timeEventDao() }

    private val isSyncing = AtomicBoolean(false)

    private val activityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val kind = result.data?.getStringExtra("MANUAL_EVENT_KIND")
            val timestampMillis = result.data?.getLongExtra("MANUAL_TIMESTAMP", -1L)
            if (kind != null && timestampMillis != null && timestampMillis != -1L) {
                val timestamp = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
                send(kind, manualTimestamp = timestamp)
            }
        }
        loadSettings()
        syncPendingEvents(manualTrigger = false)
    }

    companion object {
        private const val STATUS_EVENT_KIND = "STATUS"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observePendingEvents()
        enableEdgeToEdge()

        setContent {
            TimeTrackerTheme {
                val darkIcons = !isSystemInDarkTheme()
                DisposableEffect(darkIcons) {
                    val window = (this@MainActivity as Activity).window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = darkIcons
                    onDispose {}
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("TimeTracker") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = Color.White,
                                actionIconContentColor = Color.White
                            ),
                            actions = {
                                IconButton(onClick = {
                                    val intent = Intent(this@MainActivity, ManualEntryActivity::class.java)
                                    activityLauncher.launch(intent)
                                }) {
                                    Icon(imageVector = Icons.Outlined.EditCalendar, contentDescription = "Manuelle Nachstempelung")
                                }
                                IconButton(onClick = {
                                    startActivity(Intent(this@MainActivity, PendingEventsActivity::class.java))
                                }) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Warteschlange ansehen")
                                }
                                IconButton(onClick = {
                                    val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                                    activityLauncher.launch(intent)
                                }) {
                                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Einstellungen")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TimeTrackerScreen(
                            status = statusText.value,
                            pendingEvents = pendingEventCount.intValue,
                            version = getAppVersionName(),
                            lastEventKind = lastEventKind.value,
                            lastEventTimestamp = lastEventTimestamp.value,
                            username = username.value,
                            onSendEvent = { eventType -> send(eventType) },
                            onSync = { syncPendingEvents(manualTrigger = true) },
                            onFetchLastEvent = { fetchLastEventFromServer() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
        syncPendingEvents(manualTrigger = false)
    }

    private fun getAppVersionName(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            packageInfo.versionName ?: "N/A"
        } catch (_: Exception) {
            "N/A"
        }
    }

    private fun observePendingEvents() {
        lifecycleScope.launch {
            timeEventDao.getAllEvents().collect { events ->
                pendingEventCount.intValue = events.size
            }
        }
    }

    private fun loadSettings() {
        val p = prefs()
        serverUrl.value = p.getString("server_url", "") ?: ""
        username.value = p.getString("username", "") ?: ""
        lastEventKind.value = p.getString("last_event_kind", "") ?: ""
        lastEventTimestamp.value = p.getString("last_event_timestamp", "") ?: ""
    }

    private fun prefs() = getSharedPreferences("fx", MODE_PRIVATE)

    private fun deviceId(): String {
        val p = prefs()
        var id = p.getString("deviceId", null)
        if (id == null) {
            id = "android-" + UUID.randomUUID().toString()
            p.edit { putString("deviceId", id) }
        }
        return id
    }

    private fun send(kind: String, manualTimestamp: ZonedDateTime? = null) {
        statusText.value = if (manualTimestamp != null) "Manuelles Event..." else "Event..."
        val now = manualTimestamp ?: ZonedDateTime.now(ZoneId.systemDefault())
        val timestamp = now.toOffsetDateTime().toString()

        if (kind != STATUS_EVENT_KIND) {
            val readableText = when (kind) {
                "IN" -> "anwesend"
                "BREAK_START" -> "in Pause"
                "BREAK_END" -> "anwesend"
                "OUT" -> "abwesend"
                else -> ""
            }

            if (readableText.isNotEmpty()) {
                prefs().edit {
                    putString("last_event_kind", kind)
                    putString("last_event_timestamp", timestamp)
                }
                if (manualTimestamp == null) {
                    lastEventKind.value = kind
                    lastEventTimestamp.value = timestamp
                }
            }
        }

        val json = """{"event":"$kind","username":"${username.value}","source":"MOBILE","deviceId":"${deviceId()}","tz":"${now.zone.id}","ts":"$timestamp"}""".replace("\n", "")
        lifecycleScope.launch(Dispatchers.IO) {
            val event = TimeEvent(jsonPayload = json)
            timeEventDao.insert(event)
            syncPendingEvents(manualTrigger = false)
        }
    }

    private fun sendStatusEvent() {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val json = """{"event":"$STATUS_EVENT_KIND","username":"${username.value}","source":"MOBILE","deviceId":"${deviceId()}","tz":"${now.zone.id}","ts":"${now.toOffsetDateTime()}"}""".replace("\n", "")
        lifecycleScope.launch(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${serverUrl.value}/api/time-events")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                client.newCall(req).execute().use {}
            } catch (e: Exception) {
                Log.w("SYNC_ERROR", "Status event failed: ${e.message}")
            }
        }
    }

    private fun fetchLastEventFromServer() {
        if (serverUrl.value.isBlank() || username.value.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val url = "${serverUrl.value}/api/last-event?username=${username.value}"
            val req = Request.Builder().url(url).get().build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body.string().let { responseBody ->
                            if (responseBody.isNotBlank()) {
                                val eventKind = responseBody.substringAfter("\"event\":\"").substringBefore("\"")
                                val eventTs = responseBody.substringAfter("\"ts\":\"").substringBefore("\"")
                                launch(Dispatchers.Main) {
                                    lastEventKind.value = eventKind
                                    lastEventTimestamp.value = eventTs
                                    prefs().edit {
                                        putString("last_event_kind", eventKind)
                                        putString("last_event_timestamp", eventTs)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SYNC_ERROR", "Fetch last event failed: ${e.message}")
            }
        }
    }

    private fun syncPendingEvents(manualTrigger: Boolean = false) {
        if (!isSyncing.compareAndSet(false, true)) return

        if (serverUrl.value.isBlank() || username.value.isBlank()) {
            if (manualTrigger) statusText.value = "Fehler: URL/User fehlt!"
            isSyncing.set(false)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var errorOccurred = false
            try {
                val events = timeEventDao.getAllEventsImmediate()
                if (events.isNotEmpty()) {
                    if (manualTrigger) launch(Dispatchers.Main) { statusText.value = "Synchronisiere..." }
                    for (event in events) {
                        val req = Request.Builder()
                            .url("${serverUrl.value}/api/time-events")
                            .post(event.jsonPayload.toRequestBody("application/json".toMediaType()))
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                timeEventDao.delete(event)
                            } else {
                                throw Exception("Server antwortete mit ${resp.code}")
                            }
                        }
                    }
                    sendStatusEvent()
                    launch(Dispatchers.Main) { statusText.value = "Alle Events synchronisiert." }
                }
            } catch (e: Exception) {
                errorOccurred = true
                Log.e("SYNC_ERROR", "Sync failed: ${e.javaClass.simpleName} - ${e.message}")
                if (manualTrigger) launch(Dispatchers.Main) { statusText.value = "Keine Verbindung.\nSync fehlgeschlagen." }
            } finally {
                if (!errorOccurred) fetchLastEventFromServer()
                isSyncing.set(false)
            }
        }
    }
}

@Composable
fun TimeTrackerScreen(
    status: String,
    pendingEvents: Int,
    version: String,
    lastEventKind: String,
    lastEventTimestamp: String,
    username: String,
    onSendEvent: (String) -> Unit,
    onSync: () -> Unit,
    onFetchLastEvent: () -> Unit
) {
    val isUsernameSet = username.isNotBlank()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Text(status, fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) }
            item {
                OutlinedButton(onClick = onSync, modifier = Modifier.padding(bottom = 35.dp), enabled = pendingEvents > 0) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Synchronisieren", modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (pendingEvents > 0) "Synchronisieren ($pendingEvents)" else "Alles aktuell")
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    EventButton("Kommt", { onSendEvent("IN") }, enabled = isUsernameSet)
                    EventButton("Pause Start", { onSendEvent("BREAK_START") }, enabled = isUsernameSet)
                    EventButton("Pause Ende", { onSendEvent("BREAK_END") }, enabled = isUsernameSet)
                    EventButton("Geht", { onSendEvent("OUT") }, enabled = isUsernameSet)
                }
            }
            item {
                Spacer(modifier = Modifier.height(48.dp))
                if (lastEventKind.isNotEmpty()) {
                    val (displayText, displayColor) = when (lastEventKind) {
                        "IN" -> "anwesend" to Color(0xFF4CAF50)
                        "BREAK_START" -> "in Pause" to Color(0xFFFFC107)
                        "BREAK_END" -> "anwesend" to Color(0xFF4CAF50)
                        "OUT" -> "abwesend" to Color(0xFFF44336)
                        else -> "Unbekannt" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val formattedTimestamp = remember(lastEventTimestamp) {
                        try {
                            OffsetDateTime.parse(lastEventTimestamp).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
                        } catch (_: Exception) { "" }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = displayColor)) { append(displayText) }
                                if (formattedTimestamp.isNotEmpty()) append("\n($formattedTimestamp)")
                            },
                            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center
                        )
                        IconButton(onClick = onFetchLastEvent) { Icon(imageVector = Icons.Default.Refresh, contentDescription = "Letzten Status vom Server abfragen") }
                    }
                }
            }
        }
        Text("Version: $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun EventButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(0.8f).height(75.dp)) {
        Text(text, fontSize = 22.sp)
    }
}
