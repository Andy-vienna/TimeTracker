package org.fx.timetracker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import org.fx.timetracker.ui.theme.TimeTrackerTheme
import androidx.core.content.edit

class SettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aktiviert den "Edge-to-Edge"-Modus
        enableEdgeToEdge()

        setContent {
            TimeTrackerTheme {
                // ---- Steuerung der System-UI (Statusleiste) ----
                val darkIcons = !isSystemInDarkTheme()
                DisposableEffect(darkIcons) {
                    val window = (this@SettingsActivity as Activity).window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = darkIcons
                    onDispose {}
                }
                // ---------------------------------------------

                // Wir verwenden Scaffold, um eine konsistente TopAppBar zu haben
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Einstellungen") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding), // Padding vom Scaffold anwenden
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Die Logik für den Screen bleibt dieselbe
                        SettingsScreen(
                            context = this,
                            onSave = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(context: Context, onSave: () -> Unit) {
    val prefs = context.getSharedPreferences("fx", Context.MODE_PRIVATE)
    val serverUrl = remember { mutableStateOf(prefs.getString("server_url", "") ?: "") }
    val username = remember { mutableStateOf(prefs.getString("username", "") ?: "") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Der Titel "Einstellungen" ist jetzt in der TopAppBar, daher können wir ihn hier entfernen.
        /* item {
            Text(
                "Einstellungen",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        } */

        item {
            OutlinedTextField(
                value = serverUrl.value,
                onValueChange = { serverUrl.value = it },
                label = { Text("Server URL (z.B. https://192.168.1.10:8112)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            OutlinedTextField(
                value = username.value,
                onValueChange = { username.value = it },
                label = { Text("Benutzername") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Button(
                onClick = {
                    prefs.edit {
                        putString("server_url", serverUrl.value)
                            .putString("username", username.value)
                    }
                    onSave()
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp)
            ) {
                Text("Speichern")
            }
        }
    }
}
