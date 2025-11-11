package org.fx.timetracker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import kotlinx.coroutines.delay
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

    private val client by lazy { OkHttpClient() }

    // Die DB-Instanzen werden jetzt sicher von der Application-Klasse bezogen
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

        // Die Initialisierung findet in MainApplication statt.
        // Wir können die Beobachtung direkt und sicher starten.
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
                                    Icon(
                                        imageVector = Icons.Outlined.EditCalendar,
                                        contentDescription = "Manuelle Nachstempelung"
                                    )
                                }
                                IconButton(onClick = {
                                    startActivity(Intent(this@MainActivity, PendingEventsActivity::class.java))
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.List,
                                        contentDescription = "Warteschlange ansehen"
                                    )
                                }
                                IconButton(onClick = {
                                    val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                                    activityLauncher.launch(intent)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Einstellungen"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TimeTrackerScreen(
                            status = statusText.value,
                            pendingEvents = pendingEventCount.intValue,
                            version = getAppVersionName(),
                            lastEventKind = lastEventKind.value,
                            lastEventTimestamp = lastEventTimestamp.value,
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
        statusText.value = if (manualTimestamp != null) {
            "Manuelles Event gespeichert.\nWarte auf Sync..."
        } else {
            "Event gespeichert.\nWarte auf Sync..."
        }

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
                        .putString("last_event_timestamp", timestamp)
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
            } catch (_: Exception) { /* Fehler beim Senden des Status ist nicht kritisch. */ }
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
                        resp.body.let { responseBody ->
                            val responseBodyString = responseBody.string()
                            if (responseBodyString.isNotBlank()) {
                                val eventKind = responseBodyString.substringAfter("\"event\":\"").substringBefore("\"")
                                val eventTs = responseBodyString.substringAfter("\"ts\":\"").substringBefore("\"")

                                launch(Dispatchers.Main) {
                                    lastEventKind.value = eventKind
                                    lastEventTimestamp.value = eventTs
                                    prefs().edit {
                                        putString("last_event_kind", eventKind)
                                            .putString("last_event_timestamp", eventTs)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fehler beim Abfragen ist nicht kritisch.
            }
        }
    }

    private fun syncPendingEvents(manualTrigger: Boolean = false) {
        if (!isSyncing.compareAndSet(false, true)) {
            return
        }

        if (serverUrl.value.isBlank() || username.value.isBlank()) {
            if (manualTrigger) {
                statusText.value = "Fehler: Bitte IP und User in den Einstellungen festlegen!"
            }
            isSyncing.set(false)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var syncCompletedSuccessfully = false
            try {
                var sentAtLeastOneEvent = false
                var errorOccurred = false

                while (true) {
                    val eventToSync = timeEventDao.getOldestEvent() ?: break
                    if (manualTrigger && !sentAtLeastOneEvent) {
                        launch(Dispatchers.Main) { statusText.value = "Synchronisiere..." }
                    }
                    val req = Request.Builder()
                        .url("${serverUrl.value}/api/time-events")
                        .post(eventToSync.jsonPayload.toRequestBody("application/json".toMediaType()))
                        .build()
                    try {
                        client.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                timeEventDao.delete(eventToSync)
                                sentAtLeastOneEvent = true
                            } else {
                                errorOccurred = true
                                launch(Dispatchers.Main) { statusText.value = "Sync-Fehler: Server\nantwortet mit ${resp.code}" }
                            }
                        }
                    } catch (_: Exception) {
                        errorOccurred = true
                        if (manualTrigger) {
                            launch(Dispatchers.Main) { statusText.value = "Keine Verbindung.\nSync fehlgeschlagen." }
                        }
                    }
                    if (errorOccurred) {
                        break
                    }
                }

                if (sentAtLeastOneEvent) {
                    sendStatusEvent()
                    launch(Dispatchers.Main) {
                        statusText.value = "Alle Events synchronisiert."
                    }
                }
                if (!errorOccurred) {
                    syncCompletedSuccessfully = true
                }
            } finally {
                isSyncing.set(false)
                if (syncCompletedSuccessfully) {
                    launch(Dispatchers.IO) {
                        delay(1000L)
                        fetchLastEventFromServer()
                    }
                }
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
    onSendEvent: (String) -> Unit,
    onSync: () -> Unit,
    onFetchLastEvent: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = status,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            }
            item {
                OutlinedButton(
                    onClick = onSync,
                    modifier = Modifier.padding(bottom = 35.dp),
                    enabled = pendingEvents > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Synchronisieren",
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (pendingEvents > 0) "Synchronisieren ($pendingEvents)" else "Alles aktuell")
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    EventButton(text = "Kommt", onClick = { onSendEvent("IN") })
                    EventButton(text = "Pause Start", onClick = { onSendEvent("BREAK_START") })
                    EventButton(text = "Pause Ende", onClick = { onSendEvent("BREAK_END") })
                    EventButton(text = "Geht", onClick = { onSendEvent("OUT") })
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
                            val odt = OffsetDateTime.parse(lastEventTimestamp)
                            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                            odt.atZoneSameInstant(ZoneId.systemDefault()).format(formatter)
                        } catch (_: Exception) { "" }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = displayColor)) {
                                    append(displayText)
                                }
                                if (formattedTimestamp.isNotEmpty()) {
                                    append("\n($formattedTimestamp)")
                                }
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        IconButton(onClick = onFetchLastEvent) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Letzten Status vom Server abfragen"
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "Version: $version",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun EventButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(75.dp)
    ) {
        Text(text, fontSize = 22.sp)
    }
}
