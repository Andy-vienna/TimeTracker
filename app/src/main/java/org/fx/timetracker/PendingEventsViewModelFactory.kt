package org.fx.timetracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PendingEventsViewModelFactory(private val timeEventDao: TimeEventDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PendingEventsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PendingEventsViewModel(timeEventDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
