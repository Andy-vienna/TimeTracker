package org.fx.timetracker

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Kunde aus facturaX (über getkunden.php), wird beim Kommen-Stempel mitgesendet. */
data class Kunde(val id: String, val name: String, val ort: String)

/** Lokale Kundenliste (offline nutzbar) und zuletzt gewählter Kunde in den SharedPreferences. */
object KundenSpeicher {
    private const val KEY_LISTE = "kunden_json"
    private const val KEY_LETZTER = "last_customer"

    fun liste(prefs: SharedPreferences): List<Kunde> = parse(prefs.getString(KEY_LISTE, "[]") ?: "[]")

    /** Speichert die Antwort von getkunden.php ({"data":[...]}); liefert die Anzahl. */
    fun speichere(prefs: SharedPreferences, antwort: String): Int {
        val daten = JSONObject(antwort).optJSONArray("data") ?: JSONArray()
        prefs.edit { putString(KEY_LISTE, daten.toString()) }
        return daten.length()
    }

    fun letzter(prefs: SharedPreferences): String? = prefs.getString(KEY_LETZTER, null)

    fun merke(prefs: SharedPreferences, kundeId: String?) = prefs.edit {
        if (kundeId == null) remove(KEY_LETZTER) else putString(KEY_LETZTER, kundeId)
    }

    fun parse(json: String): List<Kunde> {
        val daten = JSONArray(json)
        return (0 until daten.length()).map { i ->
            val obj = daten.getJSONObject(i)
            Kunde(obj.optString("id"), obj.optString("name"), obj.optString("ort"))
        }.filter { it.id.isNotBlank() }
    }
}

/** "Muster GmbH" bzw. "ohne Kunde" für eine Kundennummer. */
fun kundeText(kunden: List<Kunde>, kundeId: String?): String =
    if (kundeId == null) "ohne Kunde" else kunden.firstOrNull { it.id == kundeId }?.name ?: kundeId

/** Auswahl des Kunden; bestätigt mit der Kundennummer oder null (ohne Kunde). */
@Composable
fun KundeDialog(kunden: List<Kunde>, initialId: String?, title: String, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(initialId?.takeIf { id -> kunden.any { it.id == id } }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                item { KundeZeile("ohne Kunde", null, selected == null) { selected = null } }
                items(kunden) { kunde ->
                    KundeZeile(kunde.name, kunde.ort, kunde.id == selected) { selected = kunde.id }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun KundeZeile(name: String, ort: String?, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Column {
            Text(name)
            if (!ort.isNullOrBlank()) Text(ort, style = MaterialTheme.typography.bodySmall)
        }
    }
}
