package org.fx.timetracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fx.timetracker.ui.theme.TimeTrackerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.content.edit

class MainActivity : ComponentActivity() {

    private val statusText = mutableStateOf("Bereit.")
    private val serverUrl = mutableStateOf("")
    private val username = mutableStateOf("")
    private val secretKey = mutableStateOf("")
    private val pendingEventCount = mutableIntStateOf(0)
    private val lastEventKind = mutableStateOf("")
    private val lastEventTimestamp = mutableStateOf("")

    private val networkClient by lazy { NetworkClient() }

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
                                    startActivity(Intent(this@MainActivity, OnlinePreviewActivity::class.java))
                                }) {
                                    Icon(imageVector = Icons.Default.CloudQueue, contentDescription = "Online Vorschau")
                                }
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
                            onFetchLastEvent = { /* fetchLastEventFromServer() is disabled */ }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            } ?: "N/A"
        } catch (e: PackageManager.NameNotFoundException) {
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
        secretKey.value = p.getString("secret_key", "") ?: ""
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

        val json = """{"event":"$kind","username":"${username.value}","source":"MOBILE","deviceId":"${deviceId()}","tz":"${now.zone.id}","ts":"$timestamp"}"""
        lifecycleScope.launch(Dispatchers.IO) {
            val event = TimeEvent(jsonPayload = json)
            timeEventDao.insert(event)
            syncPendingEvents(manualTrigger = false)
        }
    }

    private fun syncPendingEvents(manualTrigger: Boolean = false) {
        if (!isSyncing.compareAndSet(false, true)) return

        if (serverUrl.value.isBlank() || username.value.isBlank() || secretKey.value.isBlank()) {
            if (manualTrigger) statusText.value = "Fehler: URL/User/Key fehlt!"
            isSyncing.set(false)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var errorOccurred = false
            try {
                val events = timeEventDao.getAllEventsImmediate()
                if (events.isNotEmpty()) {
                    if (manualTrigger) launch(Dispatchers.Main) { statusText.value = "Synchronisiere..." }
                    
                    // Basis-URL vorbereiten (Sicherstellen, dass sie mit / endet)
                    val baseUrl = if (serverUrl.value.endsWith("/")) serverUrl.value else "${serverUrl.value}/"
                    val fullUrl = if (baseUrl.contains(".php")) baseUrl else "${baseUrl}timeevent.php"

                    for (event in events) {
                        val success = networkClient.sendEvent(fullUrl, secretKey.value, event.jsonPayload)
                        if (success) {
                            timeEventDao.delete(event)
                        } else {
                            errorOccurred = true
                            break
                        }
                    }
                    if (!errorOccurred) {
                        launch(Dispatchers.Main) { statusText.value = "Alle Events synchronisiert." }
                    }
                }
            } catch (e: Exception) {
                errorOccurred = true
                Log.e("SYNC_ERROR", "Sync failed: ${e.javaClass.simpleName} - ${e.message}")
            } finally {
                if (errorOccurred && manualTrigger) {
                    launch(Dispatchers.Main) { statusText.value = "Keine Verbindung.\nSync fehlgeschlagen." }
                }
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
                    val readableText = when (lastEventKind) {
                        "IN" -> "KOMMT"
                        "BREAK_START" -> "PAUSE START"
                        "BREAK_END" -> "PAUSE ENDE"
                        "OUT" -> "GEHT"
                        else -> lastEventKind
                    }
                    Text("Letzte Stempelung (lokal):", fontSize = 14.sp, color = Color.Gray)
                    Text(readableText, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(lastEventTimestamp.replace("T", " ").substringBefore("."), fontSize = 16.sp)
                }
            }
        }
        Text("v$version", modifier = Modifier.padding(8.dp), fontSize = 12.sp, color = Color.LightGray)
    }
}

@Composable
fun EventButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(0.7f).height(60.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(label, fontSize = 18.sp)
    }
}
