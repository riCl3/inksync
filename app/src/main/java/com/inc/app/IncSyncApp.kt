package com.inc.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point for Hilt.
 *
 * One [android.app.Application] subclass is all Hilt needs: every component
 * (activity, ViewModel, worker) is generated from this root.
 */
@HiltAndroidApp
class IncSyncApp : Application()
