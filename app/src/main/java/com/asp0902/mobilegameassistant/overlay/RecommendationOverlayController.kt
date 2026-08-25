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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
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
    private var label: TextView? = null
    private var effects: EffectView? = null

    fun show(text: String?, targets: List<OverlayTarget> = emptyList()) = mainHandler.post {
        if (!Settings.canDrawOverlays(context)) return@post
        val overlayRoot = root ?: FrameLayout(context).also { created ->
            val effectView = EffectView(context)
            created.addView(effectView, FrameLayout.LayoutParams(-1, -1))
            effects = effectView
            runCatching { windowManager.addView(created, layoutParams()) }
                .onSuccess { root = created }
                .onFailure { return@post }
        }
        if (text.isNullOrBlank()) {
            label?.let {
                overlayRoot.removeView(it)
                label = null
            }
        } else {
            val overlayLabel = label ?: TextView(context).also { created ->
                created.setTextColor(Color.WHITE)
                created.textSize = 14f
                created.setPadding(24, 16, 24, 16)
                created.background = GradientDrawable().apply {
                    setColor(0xDD1B2735.toInt())
                    cornerRadius = 20f
                }
                overlayRoot.addView(created, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                    topMargin = 160
                    rightMargin = 24
                })
                label = created
            }
            overlayLabel.text = text
        }
        effects?.targets = targets
    }

    fun hide() = mainHandler.post {
        root?.let { runCatching { windowManager.removeViewImmediate(it) } }
        root = null
        label = null
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

    private class EffectView(context: Context) : View(context) {
        var targets: List<OverlayTarget> = emptyList()
            set(value) {
                field = value
                invalidate()
            }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            targets.forEach { target ->
                val color = when (target.action) {
                    OverlayTarget.Action.SELECT -> Color.rgb(62, 213, 131)
                    OverlayTarget.Action.CONSIDER -> Color.rgb(255, 202, 64)
                    OverlayTarget.Action.SKIP -> Color.rgb(151, 163, 175)
                }
                val left = target.left * width
                val top = target.top * height
                val right = target.right * width
                val bottom = target.bottom * height
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(42, Color.red(color), Color.green(color), Color.blue(color))
                canvas.drawRoundRect(left, top, right, bottom, 28f, 28f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 7f
                paint.color = color
                canvas.drawRoundRect(left, top, right, bottom, 28f, 28f, paint)
            }
        }
    }
}
