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
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private val statusText = mutableStateOf("Bereit.")
    private val serverUrl = mutableStateOf("")
    private val username = mutableStateOf("")
    private val secretKey = mutableStateOf("")
    private val pendingEventCount = mutableIntStateOf(0)
    private val lastEventKind = mutableStateOf("")
    private val lastEventTimestamp = mutableStateOf("")
    private val travelCountry = mutableStateOf(DEFAULT_REISELAND)
    private val travelSince = mutableStateOf("")
    private val kunden = mutableStateOf<List<Kunde>>(emptyList())
    private val lastCustomer = mutableStateOf<String?>(null)

    private val networkClient by lazy { NetworkClient() }

    private val timeEventDao by lazy { (application as MainApplication).database.timeEventDao() }

    private val isSyncing = AtomicBoolean(false)

    private val activityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val kind = result.data?.getStringExtra("MANUAL_EVENT_KIND")
            val timestampMillis = result.data?.getLongExtra("MANUAL_TIMESTAMP", -1L)
            val country = result.data?.getStringExtra("MANUAL_COUNTRY")
            val customer = result.data?.getStringExtra("MANUAL_CUSTOMER")
            if (kind != null && timestampMillis != null && timestampMillis != -1L) {
                val timestamp = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
                send(kind, manualTimestamp = timestamp, country = country, customer = customer)
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
                            travelCountry = travelCountry.value,
                            travelSince = travelSince.value,
                            kunden = kunden.value,
                            lastCustomer = lastCustomer.value,
                            onSendEvent = { eventType, country -> send(eventType, country = country) },
                            onKommt = { customer -> send(EventKind.IN, customer = customer) },
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
        refreshKunden()
    }

    /** Holt die Kundenliste aus der Online-DB; ohne Verbindung bleibt die gespeicherte Liste. */
    private fun refreshKunden() {
        if (serverUrl.value.isBlank() || secretKey.value.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val antwort = networkClient.fetch(phpUrl("getkunden.php"), secretKey.value) ?: return@launch
            try {
                KundenSpeicher.speichere(prefs(), antwort)
                val liste = KundenSpeicher.liste(prefs())
                launch(Dispatchers.Main) { kunden.value = liste }
            } catch (e: Exception) {
                Log.e("KUNDEN", "Kundenliste unlesbar: ${e.message}")
            }
        }
    }

    /** URL eines PHP-Skripts neben der eingestellten Server-URL. */
    private fun phpUrl(script: String): String {
        val url = serverUrl.value
        return when {
            url.contains(".php") -> url.substringBeforeLast("/") + "/" + script
            url.endsWith("/") -> url + script
            else -> "$url/$script"
        }
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
        travelCountry.value = p.getString("travel_country", DEFAULT_REISELAND) ?: DEFAULT_REISELAND
        travelSince.value = p.getString("travel_since", "") ?: ""
        kunden.value = KundenSpeicher.liste(p)
        lastCustomer.value = KundenSpeicher.letzter(p)
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

    private fun send(kind: String, manualTimestamp: ZonedDateTime? = null, country: String? = null, customer: String? = null) {
        statusText.value = if (manualTimestamp != null) "Manuelles Event..." else "Event..."
        val now = manualTimestamp ?: ZonedDateTime.now(ZoneId.systemDefault())
        val timestamp = now.toOffsetDateTime().toString()

        val readableText = when (kind) {
            "IN" -> "anwesend"
            "BREAK_START" -> "in Pause"
            "BREAK_END" -> "anwesend"
            "OUT" -> "abwesend"
            EventKind.TRAVEL_START -> "auf Reise"
            EventKind.TRAVEL_END -> "Reise beendet"
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

        if (kind == EventKind.TRAVEL_START && country != null) {
            prefs().edit { putString("travel_country", country) }
            travelCountry.value = country
        }
        if (manualTimestamp == null) updateTravelState(kind, timestamp)
        if (kind == EventKind.IN) {
            KundenSpeicher.merke(prefs(), customer)
            lastCustomer.value = customer
        }

        val json = JSONObject().apply {
            put("event", kind)
            put("username", username.value)
            put("source", "MOBILE")
            put("deviceId", deviceId())
            put("tz", now.zone.id)
            put("ts", timestamp)
            if (kind == EventKind.TRAVEL_START) put("country", country ?: DEFAULT_REISELAND)
            if (kind == EventKind.IN && customer != null) put("customer", customer)
        }.toString()
        lifecycleScope.launch(Dispatchers.IO) {
            val event = TimeEvent(jsonPayload = json)
            timeEventDao.insert(event)
            syncPendingEvents(manualTrigger = false)
        }
    }

    /** Merkt sich eine laufende Reise (nur Live-Stempel), damit sie am Startbildschirm sichtbar ist. */
    private fun updateTravelState(kind: String, timestamp: String) {
        when (kind) {
            EventKind.TRAVEL_START -> {
                prefs().edit { putString("travel_since", timestamp) }
                travelSince.value = timestamp
            }
            EventKind.TRAVEL_END -> {
                prefs().edit { remove("travel_since") }
                travelSince.value = ""
            }
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
    travelCountry: String,
    travelSince: String,
    kunden: List<Kunde>,
    lastCustomer: String?,
    onSendEvent: (String, String?) -> Unit,
    onKommt: (String?) -> Unit,
    onSync: () -> Unit,
    onFetchLastEvent: () -> Unit
) {
    val isUsernameSet = username.isNotBlank()
    var showReiselandDialog by remember { mutableStateOf(false) }
    var showKundeDialog by remember { mutableStateOf(false) }

    if (showKundeDialog) {
        KundeDialog(
            kunden = kunden,
            initialId = lastCustomer,
            title = "Kommt – Kunde",
            onConfirm = { id ->
                showKundeDialog = false
                onKommt(id)
            },
            onDismiss = { showKundeDialog = false }
        )
    }

    if (showReiselandDialog) {
        ReiselandDialog(
            initialCode = travelCountry,
            title = "Reise Start – Land",
            onConfirm = { code ->
                showReiselandDialog = false
                onSendEvent(EventKind.TRAVEL_START, code)
            },
            onDismiss = { showReiselandDialog = false }
        )
    }

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
                    EventButton("Kommt", { if (kunden.isEmpty()) onKommt(null) else showKundeDialog = true }, enabled = isUsernameSet)
                    EventButton("Pause Start", { onSendEvent(EventKind.BREAK_START, null) }, enabled = isUsernameSet)
                    EventButton("Pause Ende", { onSendEvent(EventKind.BREAK_END, null) }, enabled = isUsernameSet)
                    EventButton("Geht", { onSendEvent(EventKind.OUT, null) }, enabled = isUsernameSet)
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (travelSince.isNotEmpty()) {
                        Text(
                            "Auf Reise (${travelCountry}) seit ${travelSince.replace("T", " ").substringBefore(".").take(16)}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TravelButton("Reise Start", { showReiselandDialog = true }, enabled = isUsernameSet)
                    TravelButton("Reise Ende", { onSendEvent(EventKind.TRAVEL_END, null) }, enabled = isUsernameSet)
                }
            }
            item {
                Spacer(modifier = Modifier.height(48.dp))
                if (lastEventKind.isNotEmpty()) {
                    val readableText = EventKind.label(lastEventKind)
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

/** Reise-Buttons optisch von Kommen/Gehen abgesetzt (Reisezeit ist keine Arbeitszeit). */
@Composable
fun TravelButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(0.7f).height(52.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(label, fontSize = 18.sp)
    }
}
