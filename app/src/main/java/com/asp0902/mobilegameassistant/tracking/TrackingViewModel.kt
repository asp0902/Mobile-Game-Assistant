package com.asp0902.mobilegameassistant.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalyzer
import com.asp0902.mobilegameassistant.analysis.ShopDetailReconciler
import com.asp0902.mobilegameassistant.capture.CaptureSession
import com.asp0902.mobilegameassistant.formation.FormationTemplate
import com.asp0902.mobilegameassistant.formation.FormationTemplateId
import com.asp0902.mobilegameassistant.formation.FormationTemplates
import com.asp0902.mobilegameassistant.rules.HonorDuelRuleEngine
import com.asp0902.mobilegameassistant.rules.ShopRecommendation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val captureSession: CaptureSession,
    private val shopAnalyzer: HonorDuelShopAnalyzer,
    private val ruleEngine: HonorDuelRuleEngine,
) : ViewModel() {
    val state = captureSession.state
    val frame = captureSession.frame
    private val mutableTemplate = MutableStateFlow<FormationTemplate?>(null)
    val template = mutableTemplate.asStateFlow()
    private val mutableAnalysis = MutableStateFlow<ShopAnalysisUiState>(ShopAnalysisUiState.Idle)
    val analysis = mutableAnalysis.asStateFlow()
    private val mutableDetailSlot = MutableStateFlow<Int?>(null)
    val detailSlot = mutableDetailSlot.asStateFlow()
    private var lastShopAnalysis: HonorDuelShopAnalysis? = null

    init {
        viewModelScope.launch {
            captureSession.frame.filterNotNull().collectLatest { bitmap ->
                mutableAnalysis.value = ShopAnalysisUiState.Analyzing
                runCatching {
                    withContext(Dispatchers.Default) {
                        val result = shopAnalyzer.analyze(bitmap)
                        result to ruleEngine.recommend(result)
                    }
                }.onSuccess { (result, recommendations) ->
                    val detail = result.heroDetail
                    val priorShop = lastShopAnalysis
                    if (detail != null && mutableDetailSlot.value != null && priorShop != null) {
                        val reconciled = priorShop.copy(
                            shopItems = ShopDetailReconciler.apply(priorShop.shopItems, mutableDetailSlot.value!!, detail),
                        )
                        lastShopAnalysis = reconciled
                        mutableAnalysis.value = ShopAnalysisUiState.Result(reconciled, ruleEngine.recommend(reconciled))
                    } else {
                        if (result.shopItems.isNotEmpty()) lastShopAnalysis = result
                        mutableAnalysis.value = ShopAnalysisUiState.Result(result, recommendations)
                    }
                }.onFailure {
                    mutableAnalysis.value = ShopAnalysisUiState.Error("OCR 분석 실패: ${it.message ?: "알 수 없음"}")
                }
            }
        }
    }

    fun requestTracking() = captureSession.awaitConsent()

    fun cancelRequest() = captureSession.idle("화면 공유가 취소되었습니다.")

    fun selectFormationTemplate(id: FormationTemplateId) {
        mutableTemplate.value = FormationTemplates.fromId(id)
    }

    fun selectDetailSlot(slotIndex: Int) {
        mutableDetailSlot.value = slotIndex
    }
}

sealed interface ShopAnalysisUiState {
    data object Idle : ShopAnalysisUiState
    data object Analyzing : ShopAnalysisUiState
    data class Result(
        val analysis: HonorDuelShopAnalysis,
        val recommendations: List<ShopRecommendation>,
    ) : ShopAnalysisUiState
    data class Error(val message: String) : ShopAnalysisUiState
}
