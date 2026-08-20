package com.asp0902.mobilegameassistant.tracking

import androidx.lifecycle.ViewModel
import com.asp0902.mobilegameassistant.capture.CaptureSession
import com.asp0902.mobilegameassistant.formation.FormationTemplate
import com.asp0902.mobilegameassistant.formation.FormationTemplateId
import com.asp0902.mobilegameassistant.formation.FormationTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val captureSession: CaptureSession,
) : ViewModel() {
    val state = captureSession.state
    val frame = captureSession.frame
    private val mutableTemplate = MutableStateFlow<FormationTemplate?>(null)
    val template = mutableTemplate.asStateFlow()

    fun requestTracking() = captureSession.awaitConsent()

    fun cancelRequest() = captureSession.idle("화면 공유가 취소되었습니다.")

    fun selectFormationTemplate(id: FormationTemplateId) {
        mutableTemplate.value = FormationTemplates.fromId(id)
    }
}
