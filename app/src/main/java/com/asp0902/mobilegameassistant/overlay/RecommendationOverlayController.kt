package com.asp0902.mobilegameassistant.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
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
            val effectView = EffectView(context, windowManager)
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
    }

// A recognition miss must not detach/recreate the floating control.
    fun clearTargets() = mainHandler.post { scheduleClear() }

    fun hide() = mainHandler.post {
        mainHandler.removeCallbacks(clearHighlight)
        clearPending = false
        root?.let { runCatching { windowManager.removeViewImmediate(it) } }
        root = null
        effects = null
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

    private class EffectView(
        context: Context,
        private val windowManager: WindowManager,
    ) : View(context) {
        var targets: List<OverlayTarget> = emptyList()
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val location = IntArray(2)

        // The overlay window can be inset from screen (0,0) by the system (status/nav bars),
        // but normalized target coordinates are relative to the full screen the capture used.
        // Convert with the real screen size, then subtract this view's on-screen offset.
        private fun screenSize(): android.util.Size = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            android.util.Size(bounds.width(), bounds.height())
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            android.util.Size(metrics.widthPixels, metrics.heightPixels)
        }

        override fun onDraw(canvas: Canvas) {
            if (targets.isEmpty()) return
            val screen = screenSize()
            getLocationOnScreen(location)
            targets.forEach { target ->
                val color = when (target.action) {
                    OverlayTarget.Action.SELECT -> Color.rgb(255, 48, 48)
                    OverlayTarget.Action.CONSIDER -> Color.rgb(255, 202, 64)
                    OverlayTarget.Action.SKIP -> Color.rgb(151, 163, 175)
                }
                val left = target.left * screen.width - location[0]
                val top = target.top * screen.height - location[1]
                val right = target.right * screen.width - location[0]
                val bottom = target.bottom * screen.height - location[1]
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 7f
                paint.color = color
                canvas.drawRoundRect(left, top, right, bottom, 28f, 28f, paint)
            }
        }
    }
}
