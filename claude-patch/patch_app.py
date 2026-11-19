"""Kundenauswahl beim Kommen in MainActivity/ManualEntryActivity einbauen (einmalig ausführen)."""
import pathlib, sys

base = pathlib.Path(sys.argv[1])

def patch(name, reps):
    p = base / name
    s = p.read_text(encoding="utf-8")
    for a, b in reps:
        if a not in s:
            sys.exit(f"{name}: Stelle nicht gefunden:\n{a}")
        s = s.replace(a, b, 1)
    p.write_text(s, encoding="utf-8")
    print("ok", name)

patch("MainActivity.kt", [
("""    private val travelSince = mutableStateOf("")
""", """    private val travelSince = mutableStateOf("")
    private val kunden = mutableStateOf<List<Kunde>>(emptyList())
    private val lastCustomer = mutableStateOf<String?>(null)
"""),
("""            val country = result.data?.getStringExtra("MANUAL_COUNTRY")
            if (kind != null && timestampMillis != null && timestampMillis != -1L) {
                val timestamp = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
                send(kind, manualTimestamp = timestamp, country = country)""",
"""            val country = result.data?.getStringExtra("MANUAL_COUNTRY")
            val customer = result.data?.getStringExtra("MANUAL_CUSTOMER")
            if (kind != null && timestampMillis != null && timestampMillis != -1L) {
                val timestamp = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
                send(kind, manualTimestamp = timestamp, country = country, customer = customer)"""),
("""                            travelSince = travelSince.value,
                            onSendEvent = { eventType, country -> send(eventType, country = country) },""",
"""                            travelSince = travelSince.value,
                            kunden = kunden.value,
                            lastCustomer = lastCustomer.value,
                            onSendEvent = { eventType, country -> send(eventType, country = country) },
                            onKommt = { customer -> send(EventKind.IN, customer = customer) },"""),
("""        loadSettings()
        syncPendingEvents(manualTrigger = false)
    }

    private fun getAppVersionName""",
"""        loadSettings()
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

    private fun getAppVersionName"""),
("""        travelSince.value = p.getString("travel_since", "") ?: ""
    }""",
"""        travelSince.value = p.getString("travel_since", "") ?: ""
        kunden.value = KundenSpeicher.liste(p)
        lastCustomer.value = KundenSpeicher.letzter(p)
    }"""),
("""    private fun send(kind: String, manualTimestamp: ZonedDateTime? = null, country: String? = null) {""",
"""    private fun send(kind: String, manualTimestamp: ZonedDateTime? = null, country: String? = null, customer: String? = null) {"""),
("""        if (manualTimestamp == null) updateTravelState(kind, timestamp)
""",
"""        if (manualTimestamp == null) updateTravelState(kind, timestamp)
        if (kind == EventKind.IN) {
            KundenSpeicher.merke(prefs(), customer)
            lastCustomer.value = customer
        }
"""),
("""            if (kind == EventKind.TRAVEL_START) put("country", country ?: DEFAULT_REISELAND)
""",
"""            if (kind == EventKind.TRAVEL_START) put("country", country ?: DEFAULT_REISELAND)
            if (kind == EventKind.IN && customer != null) put("customer", customer)
"""),
("""    travelSince: String,
    onSendEvent: (String, String?) -> Unit,""",
"""    travelSince: String,
    kunden: List<Kunde>,
    lastCustomer: String?,
    onSendEvent: (String, String?) -> Unit,
    onKommt: (String?) -> Unit,"""),
("""    var showReiselandDialog by remember { mutableStateOf(false) }
""",
"""    var showReiselandDialog by remember { mutableStateOf(false) }
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
"""),
("""                    EventButton("Kommt", { onSendEvent(EventKind.IN, null) }, enabled = isUsernameSet)""",
"""                    EventButton("Kommt", { if (kunden.isEmpty()) onKommt(null) else showKundeDialog = true }, enabled = isUsernameSet)"""),
])

patch("ManualEntryActivity.kt", [
("""                    onSave = { kind, dateTime, country ->
                        val intent = Intent().apply {
                            putExtra("MANUAL_EVENT_KIND", kind)
                            putExtra("MANUAL_TIMESTAMP", dateTime.toInstant().toEpochMilli())
                            country?.let { putExtra("MANUAL_COUNTRY", it) }""",
"""                    onSave = { kind, dateTime, country, customer ->
                        val intent = Intent().apply {
                            putExtra("MANUAL_EVENT_KIND", kind)
                            putExtra("MANUAL_TIMESTAMP", dateTime.toInstant().toEpochMilli())
                            country?.let { putExtra("MANUAL_COUNTRY", it) }
                            customer?.let { putExtra("MANUAL_CUSTOMER", it) }"""),
("""fun ManualEntryScreen(onGoBack: () -> Unit, onSave: (String, ZonedDateTime, String?) -> Unit) {""",
"""fun ManualEntryScreen(onGoBack: () -> Unit, onSave: (String, ZonedDateTime, String?, String?) -> Unit) {"""),
("""    var showCountryDialog by remember { mutableStateOf(false) }
""",
"""    var showCountryDialog by remember { mutableStateOf(false) }
    val kunden = remember { KundenSpeicher.liste(prefs) }
    var selectedCustomer by remember { mutableStateOf(KundenSpeicher.letzter(prefs)) }
    var showKundeDialog by remember { mutableStateOf(false) }
"""),
("""            if (selectedEventKind == EventKind.TRAVEL_START) {""",
"""            if (selectedEventKind == EventKind.IN && kunden.isNotEmpty()) {
                OutlinedButton(onClick = { showKundeDialog = true }, modifier = Modifier.fillMaxWidth(), enabled = isUsernameSet) {
                    Text("Kunde: ${kundeText(kunden, selectedCustomer)}")
                }
            }
            if (selectedEventKind == EventKind.TRAVEL_START) {"""),
("""                        onSave(kind, finalDateTime, if (kind == EventKind.TRAVEL_START) selectedCountry else null)""",
"""                        onSave(
                            kind,
                            finalDateTime,
                            if (kind == EventKind.TRAVEL_START) selectedCountry else null,
                            if (kind == EventKind.IN) selectedCustomer else null
                        )"""),
("""    if (showCountryDialog) {""",
"""    if (showKundeDialog) {
        KundeDialog(
            kunden = kunden,
            initialId = selectedCustomer,
            title = "Kunde",
            onConfirm = { id -> selectedCustomer = id; showKundeDialog = false },
            onDismiss = { showKundeDialog = false }
        )
    }

    if (showCountryDialog) {"""),
])

patch("NetworkClient.kt", [
("""    @Suppress("unused") // This function is currently not used, but kept for future features.
""", ""),
])

gradle = base.parents[5] / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
for a, b in [("versionCode = 7", "versionCode = 8"), ('versionName = "3.0.1.51"', 'versionName = "3.0.1.52"')]:
    if a not in s:
        sys.exit("build.gradle.kts: " + a + " nicht gefunden")
    s = s.replace(a, b, 1)
gradle.write_text(s, encoding="utf-8")
print("ok build.gradle.kts")
