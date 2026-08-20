package com.asp0902.mobilegameassistant.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.asp0902.mobilegameassistant.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MediaProjectionService : Service() {
    @Inject lateinit var captureSession: CaptureSession

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var contentWidth = 0
    private var contentHeight = 0
    private var captureInProgress = false
    private var isStopping = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureTimeout = Runnable { releaseImageReader() }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopTracking("화면 공유가 종료되었습니다.")
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            contentWidth = width
            contentHeight = height
            virtualDisplay?.resize(width, height, resources.configuration.densityDpi)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking(
                intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED),
                intent.parcelableIntent(EXTRA_RESULT_DATA),
            )

            ACTION_CAPTURE -> captureFrame()
            ACTION_STOP -> stopTracking()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    private fun startTracking(resultCode: Int, resultData: Intent?) {
        if (mediaProjection != null || resultCode != Activity.RESULT_OK || resultData == null) {
            if (mediaProjection == null) {
                captureSession.idle("화면 공유 권한이 필요합니다.")
                stopSelf()
            }
            return
        }

        captureSession.starting()
        startProjectionForeground()

        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val projection = manager.getMediaProjection(resultCode, resultData)
                ?: error("MediaProjection을 시작할 수 없습니다.")

            projection.registerCallback(projectionCallback, mainHandler)
            mediaProjection = projection
            contentWidth = displayWidth()
            contentHeight = displayHeight()
            virtualDisplay = projection.createVirtualDisplay(
                "AFK Tracker",
                contentWidth,
                contentHeight,
                resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null,
                null,
                null,
            ) ?: error("VirtualDisplay를 만들 수 없습니다.")
            captureSession.tracking()
        } catch (error: SecurityException) {
            stopTracking("화면 공유 권한이 만료되었습니다.")
        } catch (error: IllegalStateException) {
            stopTracking("화면 공유 세션을 시작할 수 없습니다.")
        }
    }

    private fun stopTracking(message: String? = null) {
        if (isStopping) return
        isStopping = true

        releaseImageReader()

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.let { projection ->
            projection.unregisterCallback(projectionCallback)
            projection.stop()
        }
        mediaProjection = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        captureSession.idle(message)
        stopSelf()
    }

    private fun captureFrame() {
        if (captureInProgress || mediaProjection == null || virtualDisplay == null) return

        captureInProgress = true
        try {
            val reader = ImageReader.newInstance(
                contentWidth,
                contentHeight,
                PixelFormat.RGBA_8888,
                2,
            )
            imageReader = reader
            reader.setOnImageAvailableListener({ availableReader ->
                val image = availableReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val bitmap = try {
                    image.toBitmap()
                } finally {
                    image.close()
                    releaseImageReader()
                }
                captureSession.publishFrame(bitmap)
            }, mainHandler)
            virtualDisplay?.surface = reader.surface
            mainHandler.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MS)
        } catch (_: IllegalStateException) {
            releaseImageReader()
        }
    }

    private fun releaseImageReader() {
        mainHandler.removeCallbacks(captureTimeout)
        virtualDisplay?.surface = null
        imageReader?.close()
        imageReader = null
        captureInProgress = false
    }

    private fun startProjectionForeground() {
        val notification = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "AFK 트래킹",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MediaProjectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val analyzeIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaProjectionService::class.java).setAction(ACTION_CAPTURE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("AFK 트래킹 중")
            .setContentText("사용자가 요청한 화면 공유 세션입니다.")
            .setOngoing(true)
            .addAction(0, "AFK 분석", analyzeIntent)
            .addAction(0, "트래킹 중지", stopIntent)
            .build()
    }

    private fun displayWidth(): Int = resources.displayMetrics.widthPixels

    private fun displayHeight(): Int = resources.displayMetrics.heightPixels

    private fun Image.toBitmap(): Bitmap {
        val plane = planes.first()
        val rowPadding = plane.rowStride - plane.pixelStride * width
        val padded = Bitmap.createBitmap(
            width + rowPadding / plane.pixelStride,
            height,
            Bitmap.Config.ARGB_8888,
        )
        padded.copyPixelsFromBuffer(plane.buffer)
        val bitmap = Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        return bitmap
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableIntent(key: String): Intent? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, Intent::class.java)
        else -> getParcelableExtra(key)
    }

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1001
        private const val CAPTURE_TIMEOUT_MS = 2_000L
        private const val ACTION_START = "com.asp0902.mobilegameassistant.START_TRACKING"
        private const val ACTION_STOP = "com.asp0902.mobilegameassistant.STOP_TRACKING"
        private const val ACTION_CAPTURE = "com.asp0902.mobilegameassistant.CAPTURE_FRAME"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MediaProjectionService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, resultData),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MediaProjectionService::class.java).setAction(ACTION_STOP),
            )
        }

        fun capture(context: Context) {
            context.startService(
                Intent(context, MediaProjectionService::class.java).setAction(ACTION_CAPTURE),
            )
        }
    }
}
