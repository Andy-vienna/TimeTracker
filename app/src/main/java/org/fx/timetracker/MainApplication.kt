package org.fx.timetracker

import android.app.Application
import okhttp3.OkHttpClient

class MainApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    // Create a single, app-wide instance of OkHttpClient
    val httpClient: OkHttpClient by lazy { OkHttpClient() }

}
