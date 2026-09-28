package com.asp0902.mobilegameassistant.capture

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureSession @Inject constructor() {
    private val mutableState = MutableStateFlow<TrackingState>(TrackingState.Idle())
    private val mutableFrame = MutableStateFlow<Bitmap?>(null)
    private val mutableDiagnostic = MutableStateFlow<String?>(null)

    val state = mutableState.asStateFlow()
    val frame = mutableFrame.asStateFlow()
    val diagnostic = mutableDiagnostic.asStateFlow()

    fun awaitConsent() {
        mutableState.value = TrackingState.AwaitingConsent
    }

    fun starting() {
        mutableState.value = TrackingState.Starting
    }

    fun tracking() {
        mutableState.value = TrackingState.Tracking
    }

    fun idle(message: String? = null) {
        mutableState.value = TrackingState.Idle(message)
        mutableFrame.value = null
    }

    fun pause() {
        if (mutableState.value == TrackingState.Tracking) {
            mutableState.value = TrackingState.Paused
        }
    }

    fun publishFrame(bitmap: Bitmap) {
        if (mutableState.value != TrackingState.Tracking) {
            bitmap.recycle()
            return
        }
        mutableFrame.value = bitmap
    }

    fun updateDiagnostic(message: String?) {
        if (message != mutableDiagnostic.value) {
            mutableDiagnostic.value = message
        }
    }
}
