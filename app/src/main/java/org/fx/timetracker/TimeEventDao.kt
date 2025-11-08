package org.fx.timetracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEventDao {
    @Insert
    suspend fun insert(event: TimeEvent)

    @Query("SELECT * FROM pending_events ORDER BY id ASC")
    fun getAllEvents(): Flow<List<TimeEvent>> // Flow sorgt für automatische UI-Updates

    @Query("SELECT * FROM pending_events ORDER BY id ASC LIMIT 1")
    suspend fun getOldestEvent(): TimeEvent?

    @Delete
    suspend fun delete(event: TimeEvent)
}
