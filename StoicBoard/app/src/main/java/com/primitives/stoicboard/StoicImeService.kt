package com.primitives.stoicboard

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.primitives.stoicboard.data.SettingsRepository
import com.primitives.stoicboard.data.StoicSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class StoicImeService : InputMethodService(), StoicKeyboardView.Callback {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var keyboardView: StoicKeyboardView
    private lateinit var settingsRepository: SettingsRepository

    private var rules: ContextRules = ContextPolicy.resolve(null, StoicSettings())
    private var currentPackage: String? = null

    private var typedCharsInSession = 0
    private var deletedInCurrentHold = 0
    private var lastExclamation = false

    private val tapTimes = LongArray(240)
    private var tapHead = 0
    private var tapCount = 0

    private var pendingCooldownRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
    }

    private fun buildLocalizedContext(lang: String): Context {
        val sysLocale = Resources.getSystem().configuration.locales[0]
        val locale = if (lang == "system") sysLocale else Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        return createConfigurationContext(config)
    }

    override fun onCreateInputView(): View {
        keyboardView = StoicKeyboardView(this)
        keyboardView.callback = this
        keyboardView.setContextRules(rules)
        return keyboardView
    }

    private fun isNumericInput(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: 0
        val cls = type and InputType.TYPE_MASK_CLASS
        return cls == InputType.TYPE_CLASS_NUMBER ||
               cls == InputType.TYPE_CLASS_PHONE ||
               cls == InputType.TYPE_CLASS_DATETIME
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentPackage = info?.packageName
        resetSession()

        val isNum = isNumericInput(info)
        val applyOverrides = { base: ContextRules ->
            if (isNum) base.copy(
                isSafeZone = true,
                heatThresholdApm = 9999,
                rageCooldownMs = 0L,
                clutchRequiredMs = 0L,
                clutchMaxMs = 0L,
                backspaceAbuseChars = 9999
            ) else base
        }

        val syncBaseRules = ContextPolicy.resolve(currentPackage, StoicSettings())
        rules = applyOverrides(syncBaseRules)
        
        keyboardView.setContextRules(rules)
        keyboardView.setLayer(if (isNum) KeyboardLayer.NUMBERS else KeyboardLayer.LETTERS)
        keyboardView.setHeat(0f)
        keyboardView.setState(if (rules.isSafeZone) KeyboardState.SAFE_ZONE else KeyboardState.LOCKED)

        serviceScope.launch {
            val settings = settingsRepository.settings.first()
            val localizedCtx = buildLocalizedContext(settings.appLanguage)

            if (keyboardView.context.resources.configuration.locales[0]
                != localizedCtx.resources.configuration.locales[0]
            ) {
                setInputView(createKeyboardView(localizedCtx))
            }

            rules = applyOverrides(ContextPolicy.resolve(currentPackage, settings))
            keyboardView.setContextRules(rules)
            keyboardView.setLayer(if (isNum) KeyboardLayer.NUMBERS else KeyboardLayer.LETTERS)
            keyboardView.setHeat(0f)
            keyboardView.setState(if (rules.isSafeZone) KeyboardState.SAFE_ZONE else KeyboardState.LOCKED)
        }
    }

    private fun createKeyboardView(ctx: Context): View {
        keyboardView = StoicKeyboardView(ctx)
        keyboardView.callback = this
        return keyboardView
    }

    override fun onDestroy() {
        pendingCooldownRunnable?.let { mainHandler.removeCallbacks(it) }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onThinkSucceeded() {
        keyboardView.setState(KeyboardState.TYPING)
        Haptics.success(keyboardView)
    }

    override fun onThinkFailedTooFast() {
        Haptics.warning(keyboardView)
        temporaryLock(1_200L)
    }

    override fun onThinkOverheated() {
        Haptics.strong(keyboardView)
        temporaryLock(1_500L)
    }

    override fun onKeyPressed(key: StoicKey) {
        val state = keyboardView.currentState
        if (state == KeyboardState.LOCKED || state == KeyboardState.COOLDOWN) return

        Haptics.tap(keyboardView)

        when (key.kind) {
            KeyKind.CHARACTER -> {
                commitText(key.output)
                keyboardView.consumeShiftAfterCharacter()
            }
            KeyKind.SYMBOL -> commitText(key.output)
            KeyKind.SPACE -> commitText(" ")
            KeyKind.ENTER -> sendEnter()
            KeyKind.BACKSPACE -> deleteOne()
            KeyKind.SHIFT -> keyboardView.toggleShift()
            KeyKind.LANGUAGE -> keyboardView.toggleLanguage()
            KeyKind.LAYER -> keyboardView.toggleLayer()
        }
    }

    override fun onBackspaceHoldStarted() {
        deletedInCurrentHold = 0
    }

    override fun onBackspaceHoldTick() {
        if (keyboardView.isBackspacePenaltyActive) return

        val activationLimit = rules.backspaceActivationChars
        val guardActive = activationLimit == 0 || typedCharsInSession >= activationLimit

        if (!rules.isSafeZone && guardActive && deletedInCurrentHold >= rules.backspaceAbuseChars) {
            activateBackspacePenalty()
            return
        }

        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1)
        else ic.deleteSurroundingText(1, 0)

        deletedInCurrentHold++
        lastExclamation = false
    }

    override fun onBackspaceHoldEnded() {
        deletedInCurrentHold = 0
    }

    private fun commitText(value: String) {
        if (value == "!" && rules.blockDoubleExclamation && lastExclamation) {
            Haptics.warning(keyboardView)
            return
        }
        currentInputConnection?.commitText(value, 1)
        typedCharsInSession += value.length
        lastExclamation = value == "!"
        trackHeat()
    }

    private fun sendEnter() {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        lastExclamation = false
        trackHeat()
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1)
        else ic.deleteSurroundingText(1, 0)
        lastExclamation = false
    }

    private fun trackHeat() {
        val now = System.currentTimeMillis()
        tapTimes[tapHead % tapTimes.size] = now
        tapHead++
        if (tapCount < tapTimes.size) tapCount++

        val windowMs = 10_000L
        var recentTaps = 0
        for (i in 0 until tapCount) {
            if (now - tapTimes[i] <= windowMs) recentTaps++
        }

        val apm = recentTaps * 6f
        val normalizedHeat = ((apm / rules.heatThresholdApm) * rules.heatSensitivity).coerceIn(0f, 1f)
        keyboardView.setHeat(normalizedHeat)

        if (!rules.isSafeZone && normalizedHeat >= 1f && keyboardView.currentState != KeyboardState.COOLDOWN) {
            Haptics.strong(keyboardView)
            temporaryLock(rules.rageCooldownMs)
        }
    }

    private fun activateBackspacePenalty() {
        keyboardView.isBackspacePenaltyActive = true
        Haptics.warning(keyboardView)
        mainHandler.postDelayed({ keyboardView.isBackspacePenaltyActive = false }, 2_500L)
    }

    private fun temporaryLock(ms: Long) {
        pendingCooldownRunnable?.let { mainHandler.removeCallbacks(it) }
        keyboardView.setState(KeyboardState.COOLDOWN)
        val runnable = Runnable {
            keyboardView.setHeat(0f)
            keyboardView.setState(if (rules.isSafeZone) KeyboardState.SAFE_ZONE else KeyboardState.LOCKED)
        }
        pendingCooldownRunnable = runnable
        mainHandler.postDelayed(runnable, ms)
    }

    private fun resetSession() {
        typedCharsInSession = 0
        deletedInCurrentHold = 0
        lastExclamation = false
        tapHead = 0
        tapCount = 0
        keyboardViewOrNull()?.isBackspacePenaltyActive = false
    }

    private fun keyboardViewOrNull(): StoicKeyboardView? =
        if (::keyboardView.isInitialized) keyboardView else null
}
