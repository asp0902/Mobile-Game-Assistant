package com.asp0902.mobilegameassistant.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
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
    private var view: TextView? = null

    fun show(text: String) = mainHandler.post {
        if (!Settings.canDrawOverlays(context)) return@post
        val overlay = view ?: TextView(context).also { created ->
            created.setTextColor(Color.WHITE)
            created.textSize = 14f
            created.setPadding(24, 16, 24, 16)
            created.background = GradientDrawable().apply {
                setColor(0xDD1B2735.toInt())
                cornerRadius = 20f
            }
            runCatching { windowManager.addView(created, layoutParams()) }
                .onSuccess { view = created }
                .onFailure { return@post }
        }
        overlay.text = text
    }

    fun hide() = mainHandler.post {
        view?.let { runCatching { windowManager.removeViewImmediate(it) } }
        view = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 24
        y = 160
    }
}
