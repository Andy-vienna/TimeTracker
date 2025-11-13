package org.fx.timetracker

import android.app.Application

class MainApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

}
