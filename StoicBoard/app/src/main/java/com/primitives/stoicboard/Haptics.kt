package com.primitives.stoicboard

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

object Haptics {

    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun success(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        vibrate(view.context, 45L, 150)
    }

    fun warning(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        vibrate(view.context, 90L, 210)
    }

    fun strong(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        pattern(view.context, longArrayOf(0L, 90L, 45L, 130L))
    }

    private fun vibrate(context: Context, ms: Long, amp: Int) {
        val v = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    private fun pattern(context: Context, p: LongArray) {
        val v = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(p, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(p, -1)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
