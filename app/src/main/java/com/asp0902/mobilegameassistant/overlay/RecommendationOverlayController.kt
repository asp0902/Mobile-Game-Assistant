package com.asp0902.mobilegameassistant.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationOverlayController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var effects: EffectView? = null
    private var toggle: ImageButton? = null
    private val preferences = context.getSharedPreferences("tracker-overlay", Context.MODE_PRIVATE)
    private var collapsed = preferences.getBoolean("collapsed", false)
    private var clearPending = false
    private val clearHighlight = Runnable {
        clearPending = false
        effects?.targets = emptyList()
    }

    private fun scheduleClear() {
        // Bounded grace period: repeated misses cannot extend stale highlights forever.
        if (!clearPending) {
            clearPending = true
            mainHandler.postDelayed(clearHighlight, 1500L)
        }
    }

    fun show(text: String?, targets: List<OverlayTarget> = emptyList()) = mainHandler.post {
        if (!Settings.canDrawOverlays(context)) return@post
        root ?: FrameLayout(context).also { created ->
            val effectView = EffectView(context)
            created.addView(effectView, FrameLayout.LayoutParams(-1, -1))
            effects = effectView
            runCatching { windowManager.addView(created, layoutParams()) }
                .onSuccess { root = created }
                .onFailure { return@post }
        }
        // Diagnostics stay in the app, never over the game's OCR input.
        val selected = targets.filter { it.action == OverlayTarget.Action.SELECT }.let {
            if (it.size == 1) it else emptyList()
        }
        if (selected.isEmpty()) scheduleClear() else {
            mainHandler.removeCallbacks(clearHighlight)
            clearPending = false
            effects?.targets = selected
        }
        ensureToggle()
        updateVisibility()
    }

    private fun ensureToggle() {
        if (toggle != null) return
        val density = context.resources.displayMetrics.density
        val button = ImageButton(context).apply {
            setImageResource(context.applicationInfo.icon)
            minimumWidth = (48 * density).toInt()
            minimumHeight = (48 * density).toInt()
            val inset = (8 * density).toInt()
            setPadding(inset, inset, inset, inset)
            background = GradientDrawable().apply {
                setColor(0xEE1B2735.toInt())
                shape = GradientDrawable.OVAL
            }
            clipToOutline = true
            setOnClickListener {
                collapsed = !collapsed
                preferences.edit().putBoolean("collapsed", collapsed).apply()
                updateVisibility()
            }
        }
        val params = WindowManager.LayoutParams(
            (48 * density).toInt(),
            (48 * density).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = preferences.getInt("x", context.resources.displayMetrics.widthPixels - width - (12 * density).toInt())
            y = preferences.getInt("y", (40 * density).toInt())
        }
        fun clampPosition() {
            val metrics = context.resources.displayMetrics
            params.x = params.x.coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
            params.y = params.y.coerceIn(0, (metrics.heightPixels - params.height - (48 * density).toInt()).coerceAtLeast(0))
        }
        clampPosition()
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dx * dx + dy * dy > slop * slop) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        clampPosition()
                        runCatching { windowManager.updateViewLayout(button, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) preferences.edit().putInt("x", params.x).putInt("y", params.y).apply()
                    else view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> { dragging = false; true }
                else -> false
            }
        }
        // Only this small window is touchable; the full-screen recommendation remains pass-through.
        runCatching { windowManager.addView(button, params) }
            .onSuccess { toggle = button }
            .onFailure { collapsed = false }
    }

    private fun updateVisibility() {
        root?.visibility = if (collapsed) View.GONE else View.VISIBLE
        toggle?.contentDescription = if (collapsed) "최신 트래커 추천 펼치기" else "트래커 추천과 강조 표시 숨기기"
    }

    // A recognition miss must not detach/recreate the floating control.
    fun clearTargets() = mainHandler.post { scheduleClear() }

    fun hide() = mainHandler.post {
        mainHandler.removeCallbacks(clearHighlight)
        clearPending = false
        root?.let { runCatching { windowManager.removeViewImmediate(it) } }
        toggle?.let { runCatching { windowManager.removeViewImmediate(it) } }
        root = null
        effects = null
        toggle = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    data class OverlayTarget(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val action: Action,
    ) {
        enum class Action { SELECT, CONSIDER, SKIP }
    }

    private class EffectView(context: Context) : View(context) {
        var targets: List<OverlayTarget> = emptyList()
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            targets.forEach { target ->
                val color = when (target.action) {
                    OverlayTarget.Action.SELECT -> Color.rgb(255, 48, 48)
                    OverlayTarget.Action.CONSIDER -> Color.rgb(255, 202, 64)
                    OverlayTarget.Action.SKIP -> Color.rgb(151, 163, 175)
                }
                val left = target.left * width
                val top = target.top * height
                val right = target.right * width
                val bottom = target.bottom * height
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 7f
                paint.color = color
                canvas.drawRoundRect(left, top, right, bottom, 28f, 28f, paint)
            }
        }
    }
}
