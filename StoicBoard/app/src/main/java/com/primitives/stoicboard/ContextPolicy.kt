package com.primitives.stoicboard

import com.primitives.stoicboard.data.StoicSettings

object ContextPolicy {

    fun resolve(packageName: String?, settings: StoicSettings): ContextRules {
        val pkg = packageName.orEmpty()

        val isMessenger = pkg in settings.messengerPackages
        val isWork = pkg in settings.workAppPackages
        val isSafe = pkg in settings.safeZonePackages
            || (settings.defaultIsSafeZone && !isMessenger && !isWork)

        val (minMs, maxMs) = when {
            isSafe -> settings.safeZoneMinMs to settings.safeZoneMaxMs
            isMessenger -> settings.messengerMinMs to settings.messengerMaxMs
            isWork -> settings.workMinMs to settings.workMaxMs
            else -> settings.defaultMinMs to settings.defaultMaxMs
        }

        val typingLangs = settings.typingLanguages
            .mapNotNull { runCatching { KeyboardLanguage.valueOf(it) }.getOrNull() }
            .ifEmpty { listOf(KeyboardLanguage.EN) }

        return ContextRules(
            packageName = pkg,
            isMessenger = isMessenger,
            isWorkApp = isWork,
            isSafeZone = isSafe,
            clutchRequiredMs = minMs,
            clutchMaxMs = maxMs,
            blockDoubleExclamation = settings.blockDoubleExclamationInWorkApps && isWork,
            heatThresholdApm = settings.heatThresholdApm,
            heatSensitivity = settings.heatSensitivity,
            rageCooldownMs = settings.rageCooldownMs,
            backspaceActivationChars = settings.backspaceActivationChars,
            backspaceHoldMs = settings.backspaceHoldMs,
            backspaceRepeatMs = settings.backspaceRepeatMs,
            backspaceAbuseChars = settings.backspaceAbuseChars,
            heatBarEnabled = settings.heatBarEnabled,
            typingLanguages = typingLangs
        )
    }
}
