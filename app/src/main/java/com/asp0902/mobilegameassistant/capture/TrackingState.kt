package com.asp0902.mobilegameassistant.capture

sealed interface TrackingState {
    data class Idle(val message: String? = null) : TrackingState
    data object AwaitingConsent : TrackingState
    data object Starting : TrackingState
    data object Tracking : TrackingState
}
