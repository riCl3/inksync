package com.inc.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Theme appearance preference chosen on the Settings screen. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * In-memory user preferences holder for Milestone 1.
 *
 * It only owns UI preferences that are safe to lose (theme mode). Anything
 * security- or data-sensitive (auth session, sync settings) will use
 * EncryptedSharedPreferences/DataStore in later milestones instead — a plain
 * singleton is deliberately not good enough for those.
 */
@Singleton
class AppPreferences @Inject constructor() {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }
}
