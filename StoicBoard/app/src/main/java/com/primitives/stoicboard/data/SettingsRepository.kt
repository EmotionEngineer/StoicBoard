package com.primitives.stoicboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.stoicDataStore: DataStore<Preferences> by preferencesDataStore(name = "stoic_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_DEFAULT_MIN = longPreferencesKey("default_min_ms")
        private val KEY_DEFAULT_MAX = longPreferencesKey("default_max_ms")
        private val KEY_MESSENGER_MIN = longPreferencesKey("messenger_min_ms")
        private val KEY_MESSENGER_MAX = longPreferencesKey("messenger_max_ms")
        private val KEY_WORK_MIN = longPreferencesKey("work_min_ms")
        private val KEY_WORK_MAX = longPreferencesKey("work_max_ms")
        private val KEY_SAFE_MIN = longPreferencesKey("safe_min_ms")
        private val KEY_SAFE_MAX = longPreferencesKey("safe_max_ms")
        private val KEY_HEAT_THRESHOLD = intPreferencesKey("heat_threshold_apm")
        private val KEY_HEAT_SENSITIVITY = floatPreferencesKey("heat_sensitivity")
        private val KEY_RAGE_COOLDOWN = longPreferencesKey("rage_cooldown_ms")
        private val KEY_BACKSPACE_ACT_CHARS = intPreferencesKey("backspace_act_chars")
        private val KEY_BACKSPACE_HOLD = longPreferencesKey("backspace_hold_ms")
        private val KEY_BACKSPACE_REPEAT = longPreferencesKey("backspace_repeat_ms")
        private val KEY_BACKSPACE_ABUSE = intPreferencesKey("backspace_abuse_chars")
        private val KEY_BLOCK_DOUBLE_EXCL = booleanPreferencesKey("block_double_excl")
        private val KEY_HEAT_BAR = booleanPreferencesKey("heat_bar")
        private val KEY_DEFAULT_IS_SAFE = booleanPreferencesKey("default_is_safe")
        private val KEY_SAFE_PACKAGES = stringPreferencesKey("safe_packages")
        private val KEY_MESSENGER_PACKAGES = stringPreferencesKey("messenger_packages")
        private val KEY_WORK_PACKAGES = stringPreferencesKey("work_packages")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_TYPING_LANGUAGES = stringPreferencesKey("typing_languages")

        private const val SEP = "|"
    }

    val settings: Flow<StoicSettings> = context.stoicDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            StoicSettings(
                defaultMinMs = prefs[KEY_DEFAULT_MIN] ?: 4_000L,
                defaultMaxMs = prefs[KEY_DEFAULT_MAX] ?: 7_000L,
                messengerMinMs = prefs[KEY_MESSENGER_MIN] ?: 5_000L,
                messengerMaxMs = prefs[KEY_MESSENGER_MAX] ?: 8_000L,
                workMinMs = prefs[KEY_WORK_MIN] ?: 4_000L,
                workMaxMs = prefs[KEY_WORK_MAX] ?: 7_000L,
                safeZoneMinMs = prefs[KEY_SAFE_MIN] ?: 0L,
                safeZoneMaxMs = prefs[KEY_SAFE_MAX] ?: 0L,
                heatThresholdApm = prefs[KEY_HEAT_THRESHOLD] ?: 240,
                heatSensitivity = prefs[KEY_HEAT_SENSITIVITY] ?: 1.0f,
                rageCooldownMs = prefs[KEY_RAGE_COOLDOWN] ?: 2_500L,
                backspaceActivationChars = prefs[KEY_BACKSPACE_ACT_CHARS] ?: 80,
                backspaceHoldMs = prefs[KEY_BACKSPACE_HOLD] ?: 320L,
                backspaceRepeatMs = prefs[KEY_BACKSPACE_REPEAT] ?: 55L,
                backspaceAbuseChars = prefs[KEY_BACKSPACE_ABUSE] ?: 18,
                blockDoubleExclamationInWorkApps = prefs[KEY_BLOCK_DOUBLE_EXCL] ?: true,
                heatBarEnabled = prefs[KEY_HEAT_BAR] ?: true,
                defaultIsSafeZone = prefs[KEY_DEFAULT_IS_SAFE] ?: true,
                safeZonePackages = prefs[KEY_SAFE_PACKAGES]?.splitToSet() ?: StoicSettings.DEFAULT_SAFE_ZONES,
                messengerPackages = prefs[KEY_MESSENGER_PACKAGES]?.splitToSet() ?: StoicSettings.DEFAULT_MESSENGERS,
                workAppPackages = prefs[KEY_WORK_PACKAGES]?.splitToSet() ?: StoicSettings.DEFAULT_WORK_APPS,
                appLanguage = prefs[KEY_APP_LANGUAGE] ?: "system",
                typingLanguages = prefs[KEY_TYPING_LANGUAGES]?.splitToSet() ?: setOf("EN", "RU")
            ).normalized()
        }

    suspend fun save(settings: StoicSettings) {
        val s = settings.normalized()
        context.stoicDataStore.edit { prefs ->
            prefs[KEY_DEFAULT_MIN] = s.defaultMinMs
            prefs[KEY_DEFAULT_MAX] = s.defaultMaxMs
            prefs[KEY_MESSENGER_MIN] = s.messengerMinMs
            prefs[KEY_MESSENGER_MAX] = s.messengerMaxMs
            prefs[KEY_WORK_MIN] = s.workMinMs
            prefs[KEY_WORK_MAX] = s.workMaxMs
            prefs[KEY_SAFE_MIN] = s.safeZoneMinMs
            prefs[KEY_SAFE_MAX] = s.safeZoneMaxMs
            prefs[KEY_HEAT_THRESHOLD] = s.heatThresholdApm
            prefs[KEY_HEAT_SENSITIVITY] = s.heatSensitivity
            prefs[KEY_RAGE_COOLDOWN] = s.rageCooldownMs
            prefs[KEY_BACKSPACE_ACT_CHARS] = s.backspaceActivationChars
            prefs[KEY_BACKSPACE_HOLD] = s.backspaceHoldMs
            prefs[KEY_BACKSPACE_REPEAT] = s.backspaceRepeatMs
            prefs[KEY_BACKSPACE_ABUSE] = s.backspaceAbuseChars
            prefs[KEY_BLOCK_DOUBLE_EXCL] = s.blockDoubleExclamationInWorkApps
            prefs[KEY_HEAT_BAR] = s.heatBarEnabled
            prefs[KEY_DEFAULT_IS_SAFE] = s.defaultIsSafeZone
            prefs[KEY_SAFE_PACKAGES] = s.safeZonePackages.joinToString(SEP)
            prefs[KEY_MESSENGER_PACKAGES] = s.messengerPackages.joinToString(SEP)
            prefs[KEY_WORK_PACKAGES] = s.workAppPackages.joinToString(SEP)
            prefs[KEY_APP_LANGUAGE] = s.appLanguage
            prefs[KEY_TYPING_LANGUAGES] = s.typingLanguages.joinToString(SEP)
        }
    }

    private fun String.splitToSet(): Set<String> =
        split(SEP).map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun StoicSettings.normalized(): StoicSettings {
        fun orderedPair(min: Long, max: Long): Pair<Long, Long> {
            if (min <= 0L && max <= 0L) return 0L to 0L
            val a = min.coerceIn(500L, 20_000L)
            val b = max.coerceIn(1_000L, 30_000L)
            return if (a <= b) a to b else b to a
        }

        val default = orderedPair(defaultMinMs, defaultMaxMs)
        val messenger = orderedPair(messengerMinMs, messengerMaxMs)
        val work = orderedPair(workMinMs, workMaxMs)

        val validLangs = typingLanguages
            .filter { it in setOf("EN", "RU", "ES", "UK") }
            .toSet()
            .ifEmpty { setOf("EN") }

        return copy(
            defaultMinMs = default.first,
            defaultMaxMs = default.second,
            messengerMinMs = messenger.first,
            messengerMaxMs = messenger.second,
            workMinMs = work.first,
            workMaxMs = work.second,
            safeZoneMinMs = 0L,
            safeZoneMaxMs = 0L,
            heatThresholdApm = heatThresholdApm.coerceIn(120, 700),
            heatSensitivity = heatSensitivity.coerceIn(0.5f, 2.5f),
            rageCooldownMs = rageCooldownMs.coerceIn(500L, 10_000L),
            backspaceActivationChars = backspaceActivationChars.coerceIn(0, 500),
            backspaceHoldMs = backspaceHoldMs.coerceIn(120L, 900L),
            backspaceRepeatMs = backspaceRepeatMs.coerceIn(35L, 140L),
            backspaceAbuseChars = backspaceAbuseChars.coerceIn(6, 60),
            typingLanguages = validLangs
        )
    }
}
