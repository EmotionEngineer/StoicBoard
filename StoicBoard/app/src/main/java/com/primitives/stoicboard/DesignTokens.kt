package com.primitives.stoicboard

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0A0A0A)
val Surface1 = Color(0xFF141414)
val Surface2 = Color(0xFF1E1E1E)
val Surface3 = Color(0xFF252525)
val Border = Color(0x1FFFFFFF)

val StoicGreen = Color(0xFF10B981)
val StoicRed = Color(0xFFEF4444)
val StoicAmber = Color(0xFFF59E0B)
val StoicBlue = Color(0xFF3B82F6)
val StoicPurple = Color(0xFF8B5CF6)

val TextPrimary = Color.White
val TextSecondary = Color(0xFFCBD5E1)
val TextMuted = Color(0xFF64748B)

fun vibrateDevice(context: Context, ms: Long = 35L, amp: Int = 120) {
    val a = amp.coerceIn(1, 255)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
            ?.vibrate(VibrationEffect.createOneShot(ms, a))
    } else {
        @Suppress("DEPRECATION")
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, a))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }
}
