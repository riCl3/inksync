package com.inc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.inc.app.data.AppPreferences
import com.inc.app.ui.IncSyncRoot
import com.inc.app.ui.theme.IncSyncTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single activity (pure Compose navigation — no per-screen activities).
 *
 * Hilt injects [AppPreferences] directly into the activity because
 * [AndroidEntryPoint] supports field injection on activities; the theme mode
 * is read here so the whole tree recomposes when it changes on Settings.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: AppPreferences

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by preferences.themeMode.collectAsState()
            IncSyncTheme(themeMode = themeMode) {
                IncSyncRoot(windowSizeClass = calculateWindowSizeClass(this))
            }
        }
    }
}
