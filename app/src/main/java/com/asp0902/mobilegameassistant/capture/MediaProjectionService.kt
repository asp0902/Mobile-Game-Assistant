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
import android.graphics.Color
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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.asp0902.mobilegameassistant.overlay.RecommendationOverlayController
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs
import kotlin.math.max
import javax.inject.Inject

@AndroidEntryPoint
class MediaProjectionService : Service() {
    @Inject lateinit var captureSession: CaptureSession
    @Inject lateinit var overlayController: RecommendationOverlayController

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var contentWidth = 0
    private var contentHeight = 0
    private var captureInProgress = false
    private var hasPendingFrameChange = false
    private var pendingChangeAtMs = 0L
    private var isStopping = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoCapture = object : Runnable {
        override fun run() {
            if (!isStopping && mediaProjection != null) {
                captureFrame()
                mainHandler.postDelayed(this, AUTO_CAPTURE_INTERVAL_MS)
            }
        }
    }
    private var lastObservedSignature: IntArray? = null
    private var lastPublishedSignature: IntArray? = null
    private var stableFrames = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopTracking("화면 공유가 종료되었습니다.")
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            contentWidth = width
            contentHeight = height
            virtualDisplay?.resize(width, height, resources.configuration.densityDpi)
            recreateImageReader()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking(
                intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED),
                intent.parcelableIntent(EXTRA_RESULT_DATA),
            )

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

        isStopping = false
        lastObservedSignature = null
        lastPublishedSignature = null
        stableFrames = 0
        hasPendingFrameChange = false
        pendingChangeAtMs = 0L
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
            imageReader = createImageReader(contentWidth, contentHeight)
            virtualDisplay = projection.createVirtualDisplay(
                "AFK Tracker",
                contentWidth,
                contentHeight,
                resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null,
            ) ?: error("VirtualDisplay를 만들 수 없습니다.")
            captureSession.tracking()
            mainHandler.post(autoCapture)
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
        mainHandler.removeCallbacks(autoCapture)
        overlayController.hide()

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
        val reader = imageReader ?: return

        captureInProgress = true
        try {
            val image = reader.latestImage() ?: return
            val bitmap = try {
                image.toBitmap()
            } finally {
                image.close()
            }
            publishIfStable(bitmap)
        } catch (_: IllegalStateException) {
            releaseImageReader()
        } finally {
            captureInProgress = false
        }
    }

    private fun releaseImageReader() {
        virtualDisplay?.surface = null
        imageReader?.close()
        imageReader = null
        captureInProgress = false
        hasPendingFrameChange = false
        pendingChangeAtMs = 0L
        lastObservedSignature = null
        lastPublishedSignature = null
        stableFrames = 0
    }

    private fun recreateImageReader() {
        virtualDisplay?.surface = null
        releaseImageReader()
        imageReader = createImageReader(contentWidth, contentHeight)
        virtualDisplay?.surface = imageReader?.surface
    }

    private fun publishIfStable(bitmap: Bitmap) {
        val signature = signature(bitmap)
        stableFrames = if (lastObservedSignature?.let { difference(it, signature) < STABLE_DIFFERENCE } != false) stableFrames + 1 else 0
        lastObservedSignature = signature
        val now = SystemClock.elapsedRealtime()
        val delta = lastPublishedSignature?.let { signatureDelta(it, signature) } ?: SignatureDelta(0f, 0, 0)
        val changedSincePublished = isMeaningfulChange(delta)

        if (changedSincePublished) {
            hasPendingFrameChange = true
            pendingChangeAtMs = if (pendingChangeAtMs == 0L) now else pendingChangeAtMs
        }

        val shouldForcePublish = hasPendingFrameChange && now - pendingChangeAtMs >= FORCE_PUBLISH_INTERVAL_MS
        if (lastPublishedSignature == null || (stableFrames >= REQUIRED_STABLE_FRAMES && changedSincePublished) || shouldForcePublish) {
            lastPublishedSignature = signature
            hasPendingFrameChange = false
            pendingChangeAtMs = 0L
            captureSession.publishFrame(bitmap)
        } else {
            bitmap.recycle()
        }
    }

    private fun signature(bitmap: Bitmap): IntArray = IntArray(64) { index ->
        val x = (index % 8) * (bitmap.width - 1) / 7
        val y = (index / 8) * (bitmap.height - 1) / 7
        val color = bitmap.getPixel(x, y)
        (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
    }

    private fun difference(left: IntArray, right: IntArray): Float = signatureDelta(left, right).averageDelta

    private fun signatureDelta(left: IntArray, right: IntArray): SignatureDelta {
        var total = 0f
        var maxDelta = 0
        var changedCells = 0
        for (index in left.indices) {
            val delta = abs(left[index] - right[index])
            total += delta
            maxDelta = max(maxDelta, delta)
            if (delta >= CHANGED_CELL_DELTA) changedCells++
        }
        return SignatureDelta(
            averageDelta = total / left.size,
            maxDelta = maxDelta,
            changedCells = changedCells,
        )
    }

    private fun isMeaningfulChange(delta: SignatureDelta): Boolean =
        delta.averageDelta >= PUBLISHED_DIFFERENCE ||
            delta.maxDelta >= PUBLISHED_MAX_DELTA ||
            delta.changedCells >= PUBLISHED_CHANGED_CELLS

    private data class SignatureDelta(
        val averageDelta: Float,
        val maxDelta: Int,
        val changedCells: Int,
    )

    private fun createImageReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2,
        )

    private fun ImageReader.latestImage(): Image? {
        var latest: Image? = null
        while (true) {
            val image = acquireLatestImage() ?: break
            latest?.close()
            latest = image
        }
        return latest
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("AFK 트래킹 중")
            .setContentText("화면 변화 감지 후 자동 분석 중")
            .setOngoing(true)
            .addAction(0, "트래킹 중지", stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.asp0902.mobilegameassistant.START_TRACKING"
        private const val ACTION_STOP = "com.asp0902.mobilegameassistant.STOP_TRACKING"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val AUTO_CAPTURE_INTERVAL_MS = 500L
        private const val STABLE_DIFFERENCE = 5f
        private const val PUBLISHED_DIFFERENCE = 3f
        private const val REQUIRED_STABLE_FRAMES = 1
        private const val FORCE_PUBLISH_INTERVAL_MS = 1_200L
        private const val PUBLISHED_MAX_DELTA = 24
        private const val CHANGED_CELL_DELTA = 20
        private const val PUBLISHED_CHANGED_CELLS = 3

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

    }
}
