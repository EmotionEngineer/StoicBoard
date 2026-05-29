package com.primitives.stoicboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.primitives.stoicboard.data.StoicSettings
import kotlin.math.max

class StoicKeyboardView(context: Context) : View(context) {

    interface Callback {
        fun onThinkSucceeded()
        fun onThinkFailedTooFast()
        fun onThinkOverheated()
        fun onKeyPressed(key: StoicKey)
        fun onBackspaceHoldStarted()
        fun onBackspaceHoldTick()
        fun onBackspaceHoldEnded()
    }

    var callback: Callback? = null

    var currentState: KeyboardState = KeyboardState.LOCKED
        private set

    var isBackspacePenaltyActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var language = KeyboardLanguage.EN
    private var layer = KeyboardLayer.LETTERS
    private var shift = false
    private var heat = 0f
    private var rules = ContextPolicy.resolve(null, StoicSettings())

    private val keys = ArrayList<StoicKey>(64)
    private val thinkRect = RectF()
    private val handler = Handler(Looper.getMainLooper())

    private var thinkDownAt = 0L
    private val activePointers = SparseArray<StoicKey>()
    private var backspacePointerId = MotionEvent.INVALID_POINTER_ID
    private var backspaceRepeating = false

    private val bgPaint = fill(Color.rgb(8, 8, 8))
    private val keyPaint = fill(Color.rgb(24, 24, 24))
    private val strokePaint = stroke(Color.argb(54, 255, 255, 255), 1f)
    private val textPaint = text(Color.WHITE, 16f)
    private val smallPaint = text(Color.rgb(148, 163, 184), 10f)
    private val accentPaint = fill(Color.rgb(16, 185, 129))
    private val dangerPaint = fill(Color.rgb(239, 68, 68))

    private val backspaceRunnable = object : Runnable {
        override fun run() {
            if (!backspaceRepeating) return
            val key = activePointers.get(backspacePointerId)
            if (key?.kind != KeyKind.BACKSPACE) return
            callback?.onBackspaceHoldTick()
            handler.postDelayed(this, rules.backspaceRepeatMs)
        }
    }

    private val overthinkRunnable = Runnable {
        if (currentState == KeyboardState.LOCKED && thinkDownAt > 0L) {
            thinkDownAt = 0L
            callback?.onThinkOverheated()
            invalidate()
        }
    }

    init {
        isHapticFeedbackEnabled = true
        isFocusable = true
    }

    fun setContextRules(value: ContextRules) {
        rules = value
        if (language !in rules.typingLanguages && rules.typingLanguages.isNotEmpty()) {
            language = rules.typingLanguages.first()
        }
        rebuildKeys()
        invalidate()
    }

    fun setState(value: KeyboardState) {
        currentState = value
        clearTouchState()
        rebuildKeys()
        invalidate()
    }

    fun setLayer(value: KeyboardLayer) {
        if (layer != value) {
            layer = value
            shift = false
            rebuildKeys()
            invalidate()
        }
    }

    fun setHeat(value: Float) {
        val next = value.coerceIn(0f, 1f)
        if (next != heat) {
            heat = next
            invalidate()
        }
    }

    fun toggleShift() {
        shift = !shift
        rebuildKeys()
        invalidate()
    }

    fun toggleLanguage() {
        if (rules.typingLanguages.isEmpty()) return
        val currentIdx = rules.typingLanguages.indexOf(language)
        val nextIdx = if (currentIdx == -1) 0 else (currentIdx + 1) % rules.typingLanguages.size
        language = rules.typingLanguages[nextIdx]
        layer = KeyboardLayer.LETTERS
        rebuildKeys()
        invalidate()
    }

    fun toggleLayer() {
        layer = if (layer == KeyboardLayer.LETTERS) KeyboardLayer.SYMBOLS else KeyboardLayer.LETTERS
        shift = false
        rebuildKeys()
        invalidate()
    }

    fun consumeShiftAfterCharacter() {
        if (shift) {
            shift = false
            rebuildKeys()
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (286f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildKeys()
    }

    override fun onDetachedFromWindow() {
        clearTouchState()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        when (currentState) {
            KeyboardState.LOCKED -> drawLocked(canvas)
            KeyboardState.COOLDOWN -> drawCooldown(canvas)
            KeyboardState.TYPING, KeyboardState.SAFE_ZONE -> drawKeyboard(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (currentState) {
            KeyboardState.LOCKED -> handleLockedTouch(event)
            KeyboardState.COOLDOWN -> return true
            KeyboardState.TYPING, KeyboardState.SAFE_ZONE -> handleKeyboardTouch(event)
        }
        return true
    }

    private fun handleLockedTouch(event: MotionEvent) {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (thinkRect.contains(event.getX(pointerIndex), event.getY(pointerIndex))) {
                    thinkDownAt = System.currentTimeMillis()
                    val maxMs = max(rules.clutchMaxMs, rules.clutchRequiredMs + 1_000L)
                    handler.removeCallbacks(overthinkRunnable)
                    handler.postDelayed(overthinkRunnable, maxMs + 700L)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (thinkDownAt <= 0L) {
                    clearTouchState()
                    return
                }

                val heldMs = System.currentTimeMillis() - thinkDownAt
                val maxMs = max(rules.clutchMaxMs, rules.clutchRequiredMs + 1_000L)

                clearTouchState()

                when {
                    heldMs < rules.clutchRequiredMs -> callback?.onThinkFailedTooFast()
                    heldMs <= maxMs -> callback?.onThinkSucceeded()
                    else -> callback?.onThinkOverheated()
                }
                invalidate()
            }
        }
    }

    private fun handleKeyboardTouch(event: MotionEvent) {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val key = findKey(event.getX(pointerIndex), event.getY(pointerIndex))
                if (key != null) {
                    activePointers.put(pointerId, key)
                    if (key.kind == KeyKind.BACKSPACE && !isBackspacePenaltyActive) {
                        backspacePointerId = pointerId
                        callback?.onBackspaceHoldStarted()
                        backspaceRepeating = true
                        handler.postDelayed(backspaceRunnable, rules.backspaceHoldMs)
                    }
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                var changed = false
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val key = findKey(event.getX(i), event.getY(i))
                    val oldKey = activePointers.get(pId)

                    if (oldKey != key) {
                        if (key != null) activePointers.put(pId, key) else activePointers.remove(pId)
                        if (pId == backspacePointerId && key?.kind != KeyKind.BACKSPACE) {
                            stopBackspaceRepeat()
                            callback?.onBackspaceHoldEnded()
                        }
                        changed = true
                    }
                }
                if (changed) invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val key = activePointers.get(pointerId)
                if (key != null) {
                    if (!(key.kind == KeyKind.BACKSPACE && isBackspacePenaltyActive)) {
                        callback?.onKeyPressed(key)
                    }
                    activePointers.remove(pointerId)
                }

                if (pointerId == backspacePointerId) {
                    stopBackspaceRepeat()
                    callback?.onBackspaceHoldEnded()
                }

                if (activePointers.size() == 0) clearTouchState() else invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                clearTouchState()
                callback?.onBackspaceHoldEnded()
            }
        }
    }

    private fun stopBackspaceRepeat() {
        backspaceRepeating = false
        backspacePointerId = MotionEvent.INVALID_POINTER_ID
        handler.removeCallbacks(backspaceRunnable)
    }

    private fun clearTouchState() {
        activePointers.clear()
        backspacePointerId = MotionEvent.INVALID_POINTER_ID
        thinkDownAt = 0L
        backspaceRepeating = false
        handler.removeCallbacks(backspaceRunnable)
        handler.removeCallbacks(overthinkRunnable)
        invalidate()
    }

    private fun drawLocked(canvas: Canvas) {
        val margin = 16f.dp()
        thinkRect.set(margin, 30f.dp(), width - margin, height - 30f.dp())

        keyPaint.color = Color.rgb(12, 12, 12)
        canvas.drawRoundRect(thinkRect, 16f.dp(), 16f.dp(), keyPaint)
        canvas.drawRoundRect(thinkRect, 16f.dp(), 16f.dp(), strokePaint)

        smallPaint.textSize = 10f.sp()
        smallPaint.color = Color.rgb(148, 163, 184)
        val profile = when {
            rules.isSafeZone -> context.getString(R.string.kb_safe_zone)
            rules.isMessenger -> context.getString(R.string.kb_messenger)
            rules.isWorkApp -> context.getString(R.string.kb_work)
            else -> context.getString(R.string.kb_standard)
        }
        canvas.drawText(profile, thinkRect.centerX(), thinkRect.top + 24f.dp(), smallPaint)

        textPaint.textSize = 34f.sp()
        textPaint.color = Color.WHITE
        canvas.drawText(context.getString(R.string.kb_think), thinkRect.centerX(), thinkRect.centerY() - 6f.dp(), textPaint)

        smallPaint.textSize = 11f.sp()
        smallPaint.color = Color.rgb(148, 163, 184)
        canvas.drawText(
            context.getString(R.string.kb_hold) + " ${(rules.clutchRequiredMs / 1000f).one()}–${(rules.clutchMaxMs / 1000f).one()} s",
            thinkRect.centerX(),
            thinkRect.centerY() + 20f.dp(),
            smallPaint
        )

        if (thinkDownAt > 0L) {
            val elapsed = System.currentTimeMillis() - thinkDownAt
            val maxMs = max(rules.clutchMaxMs, rules.clutchRequiredMs + 1_000L)
            val progress = (elapsed / maxMs.toFloat()).coerceIn(0f, 1f)

            val l = thinkRect.left + 24f.dp()
            val r = thinkRect.right - 24f.dp()
            val t = thinkRect.bottom - 24f.dp()
            val b = thinkRect.bottom - 14f.dp()

            canvas.drawRoundRect(l, t, r, b, 4f.dp(), 4f.dp(), strokePaint)
            accentPaint.color = if (elapsed >= rules.clutchRequiredMs) Color.rgb(16, 185, 129)
            else Color.rgb(100, 116, 139)
            canvas.drawRoundRect(l, t, l + (r - l) * progress, b, 4f.dp(), 4f.dp(), accentPaint)

            postInvalidateDelayed(33L)
        }
    }

    private fun drawCooldown(canvas: Canvas) {
        dangerPaint.color = Color.rgb(44, 10, 10)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dangerPaint)
        textPaint.textSize = 18f.sp()
        textPaint.color = Color.rgb(248, 113, 113)
        canvas.drawText(context.getString(R.string.kb_cooling_down), width / 2f, height / 2f - 6f.dp(), textPaint)
        smallPaint.textSize = 11f.sp()
        smallPaint.color = Color.rgb(203, 213, 225)
        canvas.drawText(context.getString(R.string.kb_pause), width / 2f, height / 2f + 18f.dp(), smallPaint)
    }

    private fun drawKeyboard(canvas: Canvas) {
        if (rules.heatBarEnabled && heat > 0.01f) {
            accentPaint.color = if (heat >= 0.75f) Color.rgb(239, 68, 68) else Color.rgb(245, 158, 11)
            canvas.drawRect(0f, 0f, width * heat, 4f.dp(), accentPaint)
        }

        for (key in keys) {
            keyPaint.color = keyColor(key)
            canvas.drawRoundRect(key.rect, 7f.dp(), 7f.dp(), keyPaint)
            canvas.drawRoundRect(key.rect, 7f.dp(), 7f.dp(), strokePaint)

            textPaint.textSize = when (key.kind) {
                KeyKind.SPACE -> 11f.sp()
                KeyKind.LANGUAGE, KeyKind.LAYER -> 12f.sp()
                KeyKind.SHIFT, KeyKind.BACKSPACE, KeyKind.ENTER -> 15f.sp()
                else -> 17f.sp()
            }
            textPaint.color = labelColor(key)

            val y = key.rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(displayLabel(key), key.rect.centerX(), y, textPaint)
        }

        smallPaint.textSize = 9f.sp()
        smallPaint.color = if (currentState == KeyboardState.SAFE_ZONE) Color.rgb(16, 185, 129)
        else Color.rgb(100, 116, 139)
        val modeLabel = if (currentState == KeyboardState.SAFE_ZONE) context.getString(R.string.kb_safe)
        else context.getString(R.string.kb_stoic)
        val langLabel = when (layer) {
            KeyboardLayer.SYMBOLS -> context.getString(R.string.kb_sym)
            KeyboardLayer.NUMBERS -> "123"
            else -> language.name
        }
        canvas.drawText("$modeLabel · $langLabel", width / 2f, 12f.dp(), smallPaint)
    }

    private fun isKeyPressed(key: StoicKey): Boolean {
        for (i in 0 until activePointers.size()) {
            if (activePointers.valueAt(i) == key) return true
        }
        return false
    }

    private fun keyColor(key: StoicKey): Int = when {
        isKeyPressed(key) -> Color.rgb(68, 68, 68)
        key.kind == KeyKind.BACKSPACE && isBackspacePenaltyActive -> Color.rgb(68, 12, 12)
        key.kind == KeyKind.BACKSPACE -> Color.rgb(38, 16, 16)
        key.kind == KeyKind.ENTER -> Color.rgb(13, 42, 26)
        key.kind == KeyKind.SHIFT && shift -> Color.rgb(18, 58, 42)
        key.kind == KeyKind.LANGUAGE || key.kind == KeyKind.LAYER -> Color.rgb(22, 22, 36)
        else -> Color.rgb(24, 24, 24)
    }

    private fun labelColor(key: StoicKey): Int = when {
        key.kind == KeyKind.BACKSPACE -> Color.rgb(248, 113, 113)
        key.kind == KeyKind.ENTER -> Color.rgb(52, 211, 153)
        key.kind == KeyKind.SHIFT && shift -> Color.rgb(52, 211, 153)
        key.kind == KeyKind.LANGUAGE || key.kind == KeyKind.LAYER -> Color.rgb(147, 197, 253)
        else -> Color.WHITE
    }

    private fun rebuildKeys() {
        keys.clear()
        if (width <= 0 || height <= 0) return
        if (currentState == KeyboardState.LOCKED || currentState == KeyboardState.COOLDOWN) return

        val rows = when (layer) {
            KeyboardLayer.SYMBOLS -> symbolRows()
            KeyboardLayer.NUMBERS -> numericRows()
            KeyboardLayer.LETTERS -> when (language) {
                KeyboardLanguage.EN -> englishRows()
                KeyboardLanguage.RU -> russianRows()
                KeyboardLanguage.ES -> spanishRows()
                KeyboardLanguage.UK -> ukrainianRows()
            }
        }

        val top = 18f.dp()
        val gap = 4f.dp()
        val hPad = 3f.dp()
        val rowH = (height - top - gap * (rows.size + 1)) / rows.size

        rows.forEachIndexed { rowIndex, row ->
            val totalWeight = row.sumOf { weight(it).toDouble() }.toFloat()
            val available = width - hPad * 2f - gap * (row.size - 1)
            var x = hPad
            val y = top + gap + rowIndex * (rowH + gap)

            row.forEach { key ->
                val w = available * weight(key) / totalWeight
                key.rect = RectF(x, y, x + w, y + rowH)
                keys.add(key)
                x += w + gap
            }
        }
    }

    private fun baseBottomRow(): List<StoicKey> {
        val nextLangLabel = if (rules.typingLanguages.size > 1) {
            val idx = rules.typingLanguages.indexOf(language)
            rules.typingLanguages[if (idx == -1) 0 else (idx + 1) % rules.typingLanguages.size].name
        } else {
            language.name
        }
        return listOf(
            StoicKey("?123", kind = KeyKind.LAYER),
            StoicKey(nextLangLabel, kind = KeyKind.LANGUAGE),
            StoicKey(",", output = ",", kind = KeyKind.SYMBOL),
            StoicKey("space", output = " ", kind = KeyKind.SPACE),
            StoicKey(".", output = ".", kind = KeyKind.SYMBOL),
            StoicKey("↵", kind = KeyKind.ENTER)
        )
    }

    private fun numericRows(): List<List<StoicKey>> = listOf(
        listOf("1", "2", "3", "-").map { symKey(it) },
        listOf("4", "5", "6", ",").map { symKey(it) },
        listOf("7", "8", "9").map { symKey(it) } + listOf(StoicKey("⌫", kind = KeyKind.BACKSPACE)),
        listOf(
            StoicKey("ABC", kind = KeyKind.LAYER),
            symKey("0"),
            symKey("."),
            StoicKey("↵", kind = KeyKind.ENTER)
        )
    )

    private fun englishRows(): List<List<StoicKey>> = listOf(
        "qwertyuiop".map { charKey(it.toString()) },
        "asdfghjkl".map { charKey(it.toString()) },
        listOf(StoicKey("⇧", kind = KeyKind.SHIFT)) +
            "zxcvbnm".map { charKey(it.toString()) } +
            listOf(StoicKey("⌫", kind = KeyKind.BACKSPACE)),
        baseBottomRow()
    )

    private fun russianRows(): List<List<StoicKey>> = listOf(
        listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х").map { charKey(it) },
        listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э").map { charKey(it) },
        listOf(StoicKey("⇧", kind = KeyKind.SHIFT)) +
            listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю").map { charKey(it) } +
            listOf(StoicKey("⌫", kind = KeyKind.BACKSPACE)),
        baseBottomRow()
    )

    private fun ukrainianRows(): List<List<StoicKey>> = listOf(
        listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ї").map { charKey(it) },
        listOf("ф", "і", "в", "а", "п", "р", "о", "л", "д", "ж", "є").map { charKey(it) },
        listOf(StoicKey("⇧", kind = KeyKind.SHIFT)) +
            listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "ґ").map { charKey(it) } +
            listOf(StoicKey("⌫", kind = KeyKind.BACKSPACE)),
        baseBottomRow()
    )

    private fun spanishRows(): List<List<StoicKey>> = listOf(
        "qwertyuiop".map { charKey(it.toString()) },
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ").map { charKey(it) },
        listOf(StoicKey("⇧", kind = KeyKind.SHIFT)) +
            "zxcvbnm".map { charKey(it.toString()) } +
            listOf(StoicKey("⌫", kind = KeyKind.BACKSPACE)),
        baseBottomRow()
    )

    private fun symbolRows(): List<List<StoicKey>> = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { symKey(it) },
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")").map { symKey(it) },
        listOf("*", "\"", "'", ":", ";", "!", "?", "/", "\\").map { symKey(it) } +
            listOf(StoicKey("⌫", kind = KeyKind.BACKSPACE)),
        listOf(
            StoicKey("ABC", kind = KeyKind.LAYER),
            symKey("%"),
            symKey("="),
            StoicKey("space", output = " ", kind = KeyKind.SPACE),
            symKey("."),
            StoicKey("↵", kind = KeyKind.ENTER)
        )
    )

    private fun charKey(value: String): StoicKey {
        val out = if (shift) value.uppercase() else value.lowercase()
        return StoicKey(label = out, output = out, kind = KeyKind.CHARACTER)
    }

    private fun symKey(value: String): StoicKey =
        StoicKey(label = value, output = value, kind = KeyKind.SYMBOL)

    private fun displayLabel(key: StoicKey): String =
        if (key.kind == KeyKind.SHIFT) (if (shift) "⇪" else "⇧") else key.label

    private fun weight(key: StoicKey): Float {
        if (layer == KeyboardLayer.NUMBERS) return 1f
        return when (key.kind) {
            KeyKind.SPACE -> 3.8f
            KeyKind.SHIFT, KeyKind.BACKSPACE -> 1.35f
            KeyKind.ENTER -> 1.45f
            KeyKind.LANGUAGE, KeyKind.LAYER -> 1.25f
            else -> 1f
        }
    }

    private fun findKey(x: Float, y: Float): StoicKey? {
        var best: StoicKey? = null
        var minDistSq = Float.MAX_VALUE

        for (key in keys) {
            if (key.rect.contains(x, y)) return key

            val cx = x.coerceIn(key.rect.left, key.rect.right)
            val cy = y.coerceIn(key.rect.top, key.rect.bottom)
            val distSq = (x - cx) * (x - cx) + (y - cy) * (y - cy)
            if (distSq < minDistSq) {
                minDistSq = distSq
                best = key
            }
        }
        return best
    }

    private fun fill(color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = color
            it.style = Paint.Style.FILL
        }

    private fun stroke(color: Int, width: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = color
            it.style = Paint.Style.STROKE
            it.strokeWidth = width * resources.displayMetrics.density
        }

    private fun text(color: Int, sp: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = color
            it.textAlign = Paint.Align.CENTER
            it.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            it.textSize = sp.sp()
        }

    private fun Float.dp(): Float = this * resources.displayMetrics.density
    private fun Float.sp(): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        this,
        resources.displayMetrics
    )
    private fun Float.one(): String = String.format("%.1f", this)
}
