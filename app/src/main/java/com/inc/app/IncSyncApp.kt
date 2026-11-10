package com.inc.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for Hilt.
 *
 * Implements [Configuration.Provider] so WorkManager uses Hilt's
 * [androidx.hilt.work.HiltWorkerFactory] and can inject [com.inc.app.data.sync.SyncWorker].
 */
@HiltAndroidApp
class IncSyncApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Ensure WorkManager is initialized with the Hilt configuration.
        WorkManager.getInstance(this)
    }
}
