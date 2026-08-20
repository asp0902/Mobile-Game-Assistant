package com.asp0902.mobilegameassistant.tracking

import androidx.lifecycle.ViewModel
import com.asp0902.mobilegameassistant.capture.CaptureSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val captureSession: CaptureSession,
) : ViewModel() {
    val state = captureSession.state
    val frame = captureSession.frame

    fun requestTracking() = captureSession.awaitConsent()

    fun cancelRequest() = captureSession.idle("화면 공유가 취소되었습니다.")
}
