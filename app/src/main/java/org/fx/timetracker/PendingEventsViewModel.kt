package org.fx.timetracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class ParsedEvent(val id: Long, val display: String, val originalEvent: TimeEvent)

class PendingEventsViewModel(private val timeEventDao: TimeEventDao) : ViewModel() {

    val pendingEvents: StateFlow<List<ParsedEvent>> = timeEventDao.getAllEvents()
        .map { events -> events.map { parseEvent(it) } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteEvent(event: TimeEvent) {
        viewModelScope.launch {
            timeEventDao.delete(event)
        }
    }

    private fun parseEvent(event: TimeEvent): ParsedEvent {
        return try {
            val json = JSONObject(event.jsonPayload)
            val eventType = json.getString("event")
            val timestamp = json.getString("ts")

            val odt = OffsetDateTime.parse(timestamp)
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            val formattedTimestamp = odt.format(formatter)

            val displayText = "$formattedTimestamp - $eventType"
            ParsedEvent(event.id, displayText, event)
        } catch (e: Exception) {
            ParsedEvent(event.id, "Fehlerhaftes Event: ${event.jsonPayload}", event)
        }
    }
}
