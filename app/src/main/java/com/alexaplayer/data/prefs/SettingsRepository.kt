package com.alexaplayer.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alexaplayer.core.model.AudioFocusBehaviour
import com.alexaplayer.core.model.SortOrder
import com.alexaplayer.core.model.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Everything the user can configure, in one immutable snapshot. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = false,
    val gaplessPlayback: Boolean = false,
    val resumePlayback: Boolean = true,
    val audioFocusBehaviour: AudioFocusBehaviour = AudioFocusBehaviour.PAUSE,
    val sortOrder: SortOrder = SortOrder.TITLE,
    val mediaNotificationEnabled: Boolean = true,
    /** Max resolution used when a stream has video (480/720/1080/2160). */
    val videoQuality: Int = 720,
    val excludedFolders: Set<String> = emptySet(),
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "alexa_settings")

/**
 * Settings live in DataStore rather than Room: they are a handful of scalars that are
 * read on almost every frame of the UI, and DataStore's flow is cheaper for that shape.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data
        .catch { throwable ->
            // A corrupt preferences file must not take the app down.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            Settings(
                themeMode = ThemeMode.fromKey(prefs[KEY_THEME]),
                amoled = prefs[KEY_AMOLED] ?: false,
                dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
                gaplessPlayback = prefs[KEY_GAPLESS] ?: false,
                resumePlayback = prefs[KEY_RESUME] ?: true,
                audioFocusBehaviour = AudioFocusBehaviour.fromKey(prefs[KEY_AUDIO_FOCUS]),
                sortOrder = SortOrder.fromKey(prefs[KEY_SORT_ORDER]),
                mediaNotificationEnabled = prefs[KEY_NOTIFICATION] ?: true,
                videoQuality = prefs[KEY_VIDEO_QUALITY] ?: 720,
                excludedFolders = prefs[KEY_EXCLUDED_FOLDERS] ?: emptySet(),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME] = mode.key }

    suspend fun setAmoled(enabled: Boolean) = edit { it[KEY_AMOLED] = enabled }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[KEY_DYNAMIC_COLOR] = enabled }

    suspend fun setGaplessPlayback(enabled: Boolean) = edit { it[KEY_GAPLESS] = enabled }

    suspend fun setResumePlayback(enabled: Boolean) = edit { it[KEY_RESUME] = enabled }

    suspend fun setAudioFocusBehaviour(value: AudioFocusBehaviour) = edit { it[KEY_AUDIO_FOCUS] = value.key }

    suspend fun setSortOrder(value: SortOrder) = edit { it[KEY_SORT_ORDER] = value.key }

    suspend fun setMediaNotificationEnabled(enabled: Boolean) = edit { it[KEY_NOTIFICATION] = enabled }

    suspend fun setVideoQuality(height: Int) = edit { it[KEY_VIDEO_QUALITY] = height }

    suspend fun setExcludedFolders(folders: Set<String>) = edit { it[KEY_EXCLUDED_FOLDERS] = folders }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            store.edit(block)
        } catch (io: IOException) {
            // Losing a preference write is not worth surfacing to the user.
        }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_AMOLED = booleanPreferencesKey("amoled")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_GAPLESS = booleanPreferencesKey("gapless")
        val KEY_RESUME = booleanPreferencesKey("resume_playback")
        val KEY_AUDIO_FOCUS = stringPreferencesKey("audio_focus")
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        val KEY_NOTIFICATION = booleanPreferencesKey("media_notification")
        val KEY_VIDEO_QUALITY = intPreferencesKey("video_quality")
        val KEY_EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
    }
}
