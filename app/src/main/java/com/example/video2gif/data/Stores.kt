package com.example.video2gif.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.video2gif.domain.AppSettings
import com.example.video2gif.domain.RunState
import com.example.video2gif.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appDataStore by preferencesDataStore(name = "video2gif")

class SettingsStore(private val context: Context) {
    private val dataStore = context.applicationContext.appDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            AppSettings(
                themeMode = runCatching {
                    ThemeMode.valueOf(prefs[THEME_MODE] ?: ThemeMode.System.name)
                }.getOrDefault(ThemeMode.System),
                dynamicColor = prefs[DYNAMIC_COLOR] ?: false,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}

/** 任务快照用同步写入，进程被终止前也能保留已完成项与可恢复状态。 */
class RunStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("conversion_run", Context.MODE_PRIVATE)

    fun save(run: RunState) {
        prefs.edit().putString(KEY_RUN, JsonCodec.runToJson(run)).apply()
    }

    fun load(): RunState? = JsonCodec.runFromJson(prefs.getString(KEY_RUN, null))

    fun clear() {
        prefs.edit().remove(KEY_RUN).apply()
    }

    private companion object {
        const val KEY_RUN = "run_state"
    }
}
