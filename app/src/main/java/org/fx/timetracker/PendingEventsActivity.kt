package org.fx.timetracker

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
// KORRIGIERTER IMPORT
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import org.fx.timetracker.ui.theme.TimeTrackerTheme

class PendingEventsActivity : ComponentActivity() {

    private val db by lazy { AppDatabase.getDatabase(this) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TimeTrackerTheme {
                // ---- Steuerung der System-UI (Statusleiste), genau wie in MainActivity ----
                val darkIcons = !isSystemInDarkTheme()
                DisposableEffect(darkIcons) {
                    val window = (this@PendingEventsActivity as Activity).window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = darkIcons
                    onDispose {}
                }
                // ---------------------------------------------------------------------

                // Wir beobachten den Flow aus der Datenbank live
                val pendingEvents by db.timeEventDao().getAllEvents().collectAsState(initial = emptyList())

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Wartende Events (${pendingEvents.size})") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            navigationIcon = {
                                IconButton(onClick = { finish() }) { // 'finish()' schließt die Activity
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack, // KORRIGIERT
                                        contentDescription = "Zurück"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (pendingEvents.isEmpty()) {
                            // Zeige eine Nachricht, wenn nichts zu senden ist
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Keine wartenden Events.", modifier = Modifier.padding(16.dp))
                            }
                        } else {
                            // Zeige die Liste der Events
                            LazyColumn {
                                items(pendingEvents) { event ->
                                    Text(
                                        text = event.jsonPayload,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    // Die neue, korrekte Divider-Komponente aus Material 3
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
