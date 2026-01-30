package org.fx.timetracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TimeEvent::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timeEventDao(): TimeEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timetracker_database"
                )
                .addMigrations(MIGRATION_1_2)
                // Add this as a safety net to prevent crashes on unexpected schema changes.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS pending_events")
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `jsonPayload` TEXT NOT NULL)")
            }
        }
    }
}
