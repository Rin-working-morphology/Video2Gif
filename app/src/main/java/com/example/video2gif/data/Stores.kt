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
        if (!prefs.edit().putString(KEY_RUN, JsonCodec.runToJson(run)).commit()) {
            throw IOException("无法保存任务状态，请检查存储空间")
        }
    }

    fun load(): RunState? {
        val raw = prefs.getString(KEY_RUN, null)
        return try {
            JsonCodec.runFromJson(raw)
        } catch (e: IOException) {
            // 保留损坏或高版本原文，不能在下一次转换时把唯一恢复资料覆盖掉。
            if (!prefs.edit().putString("unreadable_run_${System.currentTimeMillis()}", raw)
                    .remove(KEY_RUN).commit()) throw e
            RunState("unreadable", System.currentTimeMillis(), emptyList(),
                isRunning = false, stopReason = e.message)
        }
    }

    fun clear() {
        if (!prefs.edit().remove(KEY_RUN).commit()) throw IOException("无法清除任务状态")
    }

    private companion object {
        const val KEY_RUN = "run_state"
    }
}
