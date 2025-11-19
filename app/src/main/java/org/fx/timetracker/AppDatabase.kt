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
                // Füge die explizite, manuelle Migration von Version 1 zu 2 hinzu.
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Definiert die manuelle Migration von der Datenbank-Version 1 auf 2.
         * Da wir den Primärschlüssel von Int auf Long ändern, ist eine einfache
         * ALTER TABLE Anweisung nicht möglich. Der sicherste Weg ist, die alte Tabelle
         * zu löschen und neu zu erstellen. Die lokalen Daten gehen dabei verloren, was
         * in diesem Fall akzeptabel ist, da die Events zum Server synchronisiert werden.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS pending_events")
                // Erstellt die neue Tabelle. Beachte: Ein `Long` in Kotlin Room wird zu `INTEGER` in SQLite.
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `jsonPayload` TEXT NOT NULL)")
            }
        }
    }
}
