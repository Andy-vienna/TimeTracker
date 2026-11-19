package org.fx.timetracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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

/** Stempelarten, wie sie an die Online-DB gesendet werden. */
object EventKind {
    const val IN = "IN"
    const val OUT = "OUT"
    const val BREAK_START = "BREAK_START"
    const val BREAK_END = "BREAK_END"
    const val TRAVEL_START = "TRAVEL_START"
    const val TRAVEL_END = "TRAVEL_END"

    /** Anzeigetext einer Stempelart (Großschrift wie in der Übersicht). */
    fun label(kind: String): String = when (kind) {
        IN -> "KOMMT"
        BREAK_START -> "PAUSE START"
        BREAK_END -> "PAUSE ENDE"
        OUT -> "GEHT"
        TRAVEL_START -> "REISE START"
        TRAVEL_END -> "REISE ENDE"
        else -> kind
    }
}

/** Reiseland mit ISO-Code, identisch mit dem Land-Enum in facturaX. */
data class Reiseland(val code: String, val name: String)

val REISELAENDER = listOf(
    Reiseland("AT", "Österreich"), Reiseland("DE", "Deutschland"), Reiseland("CH", "Schweiz"),
    Reiseland("BE", "Belgien"), Reiseland("BG", "Bulgarien"), Reiseland("CY", "Zypern"),
    Reiseland("CZ", "Tschechien"), Reiseland("DK", "Dänemark"), Reiseland("EE", "Estland"),
    Reiseland("ES", "Spanien"), Reiseland("FI", "Finnland"), Reiseland("FR", "Frankreich"),
    Reiseland("GR", "Griechenland"), Reiseland("HR", "Kroatien"), Reiseland("HU", "Ungarn"),
    Reiseland("IE", "Irland"), Reiseland("IT", "Italien"), Reiseland("LT", "Litauen"),
    Reiseland("LU", "Luxemburg"), Reiseland("LV", "Lettland"), Reiseland("MT", "Malta"),
    Reiseland("NL", "Niederlande"), Reiseland("PL", "Polen"), Reiseland("PT", "Portugal"),
    Reiseland("RO", "Rumänien"), Reiseland("SE", "Schweden"), Reiseland("SI", "Slowenien"),
    Reiseland("SK", "Slowakei")
)

const val DEFAULT_REISELAND = "AT"

/** "AT – Österreich" für einen Ländercode. */
fun reiselandText(code: String): String =
    REISELAENDER.firstOrNull { it.code == code }?.let { "${it.code} – ${it.name}" } ?: code

/** Auswahl des Reiselands; bestätigt mit dem gewählten ISO-Code. */
@Composable
fun ReiselandDialog(initialCode: String, title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(initialCode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(REISELAENDER) { land ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selected = land.code }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = land.code == selected, onClick = { selected = land.code })
                        Text("${land.code} – ${land.name}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
