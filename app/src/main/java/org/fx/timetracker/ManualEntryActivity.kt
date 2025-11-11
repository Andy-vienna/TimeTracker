package org.fx.timetracker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
// KORRIGIERTER IMPORT
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import org.fx.timetracker.ui.theme.TimeTrackerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
class ManualEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TimeTrackerTheme {
                val darkIcons = !isSystemInDarkTheme()
                DisposableEffect(darkIcons) {
                    val window = (this@ManualEntryActivity as Activity).window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = darkIcons
                    onDispose {}
                }

                ManualEntryScreen(
                    onGoBack = { finish() },
                    onSave = { kind, dateTime ->
                        val intent = Intent().apply {
                            putExtra("MANUAL_EVENT_KIND", kind)
                            putExtra("MANUAL_TIMESTAMP", dateTime.toInstant().toEpochMilli())
                        }
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(onGoBack: () -> Unit, onSave: (String, ZonedDateTime) -> Unit) {
    val context = LocalContext.current

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTime by remember { mutableStateOf(LocalTime.now()) }
    var selectedEventKind by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = ZonedDateTime.of(selectedDate, selectedTime, ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    val timePickerState = rememberTimePickerState(
        initialHour = selectedTime.hour,
        initialMinute = selectedTime.minute,
        is24Hour = true
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manuelle Nachstempelung") },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") // KORRIGIERT
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Datum: ${selectedDate.format(dateFormatter)}")
            }

            OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Uhrzeit: ${selectedTime.format(timeFormatter)}")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Ereignis auswählen:", style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventTypeButton(text = "Kommt", kind = "IN", selectedKind = selectedEventKind, onClick = { selectedEventKind = "IN" }, modifier = Modifier.weight(1f))
                EventTypeButton(text = "Geht", kind = "OUT", selectedKind = selectedEventKind, onClick = { selectedEventKind = "OUT" }, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventTypeButton(text = "Pause Start", kind = "BREAK_START", selectedKind = selectedEventKind, onClick = { selectedEventKind = "BREAK_START" }, modifier = Modifier.weight(1f))
                EventTypeButton(text = "Pause Ende", kind = "BREAK_END", selectedKind = selectedEventKind, onClick = { selectedEventKind = "BREAK_END" }, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (selectedEventKind == null) {
                        Toast.makeText(context, "Bitte ein Ereignis auswählen", Toast.LENGTH_SHORT).show()
                    } else {
                        val finalDateTime = ZonedDateTime.of(selectedDate, selectedTime, ZoneId.systemDefault())
                        onSave(selectedEventKind!!, finalDateTime)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Nachstempeln")
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") }
            }
        ) {
            TimeInput(state = timePickerState, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun EventTypeButton(text: String, kind: String, selectedKind: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isSelected = kind == selectedKind
    if (isSelected) {
        Button(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(text, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(text, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
private fun TimePickerDialog(
    title: @Composable () -> Unit = { Text("Uhrzeit auswählen") },
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.width(IntrinsicSize.Min)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                title()
                Spacer(modifier = Modifier.height(24.dp))
                content()
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton()
                    Spacer(modifier = Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}
