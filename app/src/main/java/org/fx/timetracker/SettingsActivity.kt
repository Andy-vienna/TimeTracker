package org.fx.timetracker

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.fx.timetracker.ui.theme.TimeTrackerTheme

/** Daten aus dem Einrichtungs-QR-Code von facturaX. */
data class Einrichtung(val server: String, val username: String, val key: String)

/**
 * Liest den Link aus dem QR-Code in facturaX (Benutzerverwaltung → Zeiterfassungs-Benutzer):
 * fxtime://setup?server=…&user=…&key=… – null bei allem anderen.
 */
fun leseEinrichtung(text: String?): Einrichtung? {
    if (text.isNullOrBlank()) return null
    val uri = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "fxtime" || uri.host != "setup") return null
    val server = uri.getQueryParameter("server")?.trim().orEmpty()
    val user = uri.getQueryParameter("user")?.trim().orEmpty()
    val key = uri.getQueryParameter("key")?.trim().orEmpty()
    if (!server.startsWith("https://") || user.isEmpty() || key.isEmpty()) return null
    return Einrichtung(server, user, key)
}

class SettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            TimeTrackerTheme {
                val darkIcons = !isSystemInDarkTheme()
                DisposableEffect(darkIcons) {
                    val window = (this@SettingsActivity as Activity).window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = darkIcons
                    onDispose {}
                }

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
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SettingsScreen(
                            onSave = { finish() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onSave: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fx", Context.MODE_PRIVATE) }
    var serverUrl by remember { mutableStateOf(prefs.getString("server_url", "") ?: "") }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var secretKey by remember { mutableStateOf(prefs.getString("secret_key", "") ?: "") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server Basis-URL (z.B. https://www.aw-net.eu/fXtime)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://...") }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Benutzername") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            OutlinedTextField(
                value = secretKey,
                onValueChange = { secretKey = it },
                label = { Text("Secret Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item {
            OutlinedButton(
                onClick = {
                    val optionen = GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                    GmsBarcodeScanning.getClient(context, optionen).startScan()
                        .addOnSuccessListener { code ->
                            val e = leseEinrichtung(code.rawValue)
                            if (e == null) {
                                Toast.makeText(context, "Kein Einrichtungs-Code von facturaX", Toast.LENGTH_LONG).show()
                            } else {
                                serverUrl = e.server
                                username = e.username
                                secretKey = e.key
                                Toast.makeText(context, "Übernommen – bitte prüfen und speichern", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "QR-Scanner nicht verfügbar: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp)
            ) {
                Text("Per QR-Code einrichten")
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            Button(
                onClick = {
                    prefs.edit(commit = true) {
                        putString("server_url", serverUrl)
                        putString("username", username)
                        putString("secret_key", secretKey)
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
