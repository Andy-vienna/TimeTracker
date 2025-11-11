package org.fx.timetracker

import android.app.Application

/**
 * Eine eigene Application-Klasse, um App-weite Komponenten wie die Datenbank
 * einmalig zu initialisieren und bereitzustellen.
 * Diese Klasse wird vor allen Activities gestartet (deklariert in AndroidManifest.xml).
 */
class MainApplication : Application() {

    // Die Datenbank-Instanz, auf die die ganze App zugreifen kann.
    // Die Initialisierung über 'lazy' stellt sicher, dass sie erst bei der
    // ersten Verwendung und nur einmal erstellt wird.
    // WICHTIG: Room's .databaseBuilder() blockiert den UI-Thread nicht, solange
    // keine Abfragen gemacht werden, daher ist 'lazy' hier sicher.
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
