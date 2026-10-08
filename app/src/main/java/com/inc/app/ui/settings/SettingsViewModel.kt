package com.inc.app.ui.settings

import androidx.lifecycle.ViewModel
import com.inc.app.data.AppPreferences
import com.inc.app.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Settings screen state is just the preference flows exposed for Compose.
 * Kept in a ViewModel (rather than injecting AppPreferences into the
 * composable) so the screen stays testable and follows the same
 * UI → ViewModel pattern as every other screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode

    fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)
}
