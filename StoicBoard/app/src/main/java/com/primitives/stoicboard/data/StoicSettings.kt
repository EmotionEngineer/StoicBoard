package com.primitives.stoicboard.data

data class StoicSettings(
    val defaultMinMs: Long = 4_000L,
    val defaultMaxMs: Long = 7_000L,
    val messengerMinMs: Long = 5_000L,
    val messengerMaxMs: Long = 8_000L,
    val workMinMs: Long = 4_000L,
    val workMaxMs: Long = 7_000L,
    val safeZoneMinMs: Long = 0L,
    val safeZoneMaxMs: Long = 0L,
    val heatThresholdApm: Int = 240,
    val heatSensitivity: Float = 1.0f,
    val rageCooldownMs: Long = 2_500L,
    val backspaceActivationChars: Int = 80,
    val backspaceHoldMs: Long = 320L,
    val backspaceRepeatMs: Long = 55L,
    val backspaceAbuseChars: Int = 18,
    val blockDoubleExclamationInWorkApps: Boolean = true,
    val heatBarEnabled: Boolean = true,
    val defaultIsSafeZone: Boolean = true,
    val safeZonePackages: Set<String> = DEFAULT_SAFE_ZONES,
    val messengerPackages: Set<String> = DEFAULT_MESSENGERS,
    val workAppPackages: Set<String> = DEFAULT_WORK_APPS,
    val appLanguage: String = "system",
    val typingLanguages: Set<String> = setOf("EN", "RU")
) {
    companion object {
        val DEFAULT_SAFE_ZONES = setOf(
            "com.google.android.keep",
            "com.google.android.apps.keep",
            "com.google.android.googlequicksearchbox",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.android.browser"
        )

        val DEFAULT_MESSENGERS = setOf(
            "org.telegram.messenger",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",
            "com.viber.voip",
            "com.discord"
        )

        val DEFAULT_WORK_APPS = setOf(
            "com.google.android.gm",
            "com.slack",
            "com.microsoft.teams",
            "com.microsoft.office.outlook",
            "com.google.android.apps.docs.editors.docs"
        )
    }
}
