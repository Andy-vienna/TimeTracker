package org.fx.timetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_events")
data class TimeEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val jsonPayload: String // Wir speichern einfach den kompletten JSON-String
)

