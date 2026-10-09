package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.core.model.AudioFocusBehaviour
import com.alexaplayer.core.model.ThemeMode
import com.alexaplayer.data.prefs.Settings
import com.alexaplayer.data.prefs.SettingsRepository
import com.alexaplayer.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings write straight through to DataStore. There is no save button because there is
 * nothing to confirm.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Settings())

    fun setThemeMode(mode: ThemeMode) = edit { settingsRepository.setThemeMode(mode) }

    fun setAmoled(enabled: Boolean) = edit { settingsRepository.setAmoled(enabled) }

    fun setDynamicColor(enabled: Boolean) = edit { settingsRepository.setDynamicColor(enabled) }

    fun setGapless(enabled: Boolean) = edit { settingsRepository.setGaplessPlayback(enabled) }

    fun setResumePlayback(enabled: Boolean) = edit { settingsRepository.setResumePlayback(enabled) }

    fun setAudioFocusBehaviour(value: AudioFocusBehaviour) =
        edit { settingsRepository.setAudioFocusBehaviour(value) }

    fun setMediaNotificationEnabled(enabled: Boolean) =
        edit { settingsRepository.setMediaNotificationEnabled(enabled) }

    fun setVideoQuality(height: Int) =
        edit { settingsRepository.setVideoQuality(height) }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(settingsRepository = container.settingsRepository) }
        }
    }
}