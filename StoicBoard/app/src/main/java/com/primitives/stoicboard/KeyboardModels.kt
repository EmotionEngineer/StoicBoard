package com.primitives.stoicboard

import android.graphics.RectF

enum class KeyboardState { LOCKED, TYPING, COOLDOWN, SAFE_ZONE }
enum class KeyboardLanguage { EN, RU, ES, UK }
enum class KeyboardLayer { LETTERS, SYMBOLS, NUMBERS }
enum class KeyKind { CHARACTER, SPACE, BACKSPACE, ENTER, SHIFT, LANGUAGE, LAYER, SYMBOL }

data class StoicKey(
    val label: String,
    val output: String = label,
    val kind: KeyKind = KeyKind.CHARACTER,
    var rect: RectF = RectF()
)

data class ContextRules(
    val packageName: String,
    val isMessenger: Boolean,
    val isWorkApp: Boolean,
    val isSafeZone: Boolean,
    val clutchRequiredMs: Long,
    val clutchMaxMs: Long,
    val blockDoubleExclamation: Boolean,
    val heatThresholdApm: Int,
    val heatSensitivity: Float,
    val rageCooldownMs: Long,
    val backspaceActivationChars: Int,
    val backspaceHoldMs: Long,
    val backspaceRepeatMs: Long,
    val backspaceAbuseChars: Int,
    val heatBarEnabled: Boolean,
    val typingLanguages: List<KeyboardLanguage>
)
