package com.asp0902.mobilegameassistant.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalyzer
import com.asp0902.mobilegameassistant.analysis.HeroCorrection
import com.asp0902.mobilegameassistant.analysis.HeroCorrectionRepository
import com.asp0902.mobilegameassistant.analysis.HeroCorrectionApplier
import com.asp0902.mobilegameassistant.analysis.HeroRecognitionCatalog
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
    private val heroCatalog: HeroRecognitionCatalog,
    private val correctionRepository: HeroCorrectionRepository,
    private val runRepository: HonorDuelRunRepository,
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
    private var runId = System.currentTimeMillis()
    private var nextSnapshotId = 1L
    private var lastRunState: ReconciledHonorDuelState? = null
    private var hasLiveAnalysis = false

    init {
        viewModelScope.launch {
            runRepository.restoreLatest()?.let { restored ->
                if (!hasLiveAnalysis) {
                    runId = restored.runId
                    lastRunState = restored
                    lastShopAnalysis = restored.analysis
                    mutableAnalysis.value = ShopAnalysisUiState.Result(restored.analysis, ruleEngine.recommend(restored.analysis), nextSnapshotId++, restored.headerSources)
                }
            }
        }
        viewModelScope.launch {
            captureSession.frame.filterNotNull().collectLatest { bitmap ->
                hasLiveAnalysis = true
                mutableAnalysis.value = ShopAnalysisUiState.Analyzing
                runCatching {
                    withContext(Dispatchers.Default) {
                        val result = shopAnalyzer.analyze(bitmap, runId)
                        result to ruleEngine.recommend(result)
                    }
                }.onSuccess { (result, recommendations) ->
                    val detail = result.heroDetail
                    val priorShop = lastShopAnalysis
                    val observed = if (detail != null && mutableDetailSlot.value != null && priorShop != null) {
                        priorShop.copy(
                            shopItems = ShopDetailReconciler.apply(priorShop.shopItems, mutableDetailSlot.value!!, detail),
                        )
                    } else {
                        result
                    }
                    val reconciled = HonorDuelStateReconciler.reconcile(runId, lastRunState, observed)
                    lastRunState = reconciled
                    if (reconciled.analysis.shopItems.isNotEmpty()) lastShopAnalysis = reconciled.analysis
                    val snapshotId = nextSnapshotId++
                    mutableAnalysis.value = ShopAnalysisUiState.Result(reconciled.analysis, ruleEngine.recommend(reconciled.analysis), snapshotId, reconciled.headerSources)
                    viewModelScope.launch(Dispatchers.IO) { runRepository.save(reconciled) }
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

    fun heroChoices() = heroCatalog.heroChoices()

    fun correctHero(snapshotId: Long, slotIndex: Int, koreanName: String) {
        val current = (mutableAnalysis.value as? ShopAnalysisUiState.Result) ?: return
        val item = current.analysis.shopItems.firstOrNull { it.slotIndex == slotIndex } ?: return
        val hero = heroCatalog.heroByName(koreanName) ?: return
        val corrected = current.analysis.copy(shopItems = current.analysis.shopItems.map {
            if (it.slotIndex == slotIndex) HeroCorrectionApplier.apply(it, hero) else it
        })
        lastShopAnalysis = corrected
        mutableAnalysis.value = ShopAnalysisUiState.Result(corrected, ruleEngine.recommend(corrected), snapshotId, current.headerSources)
        viewModelScope.launch {
            correctionRepository.save(HeroCorrection(
                runId, snapshotId, slotIndex, item.heroId, hero.id, hero.koreanName, item.portraitSignature,
            ))
        }
    }
}

sealed interface ShopAnalysisUiState {
    data object Idle : ShopAnalysisUiState
    data object Analyzing : ShopAnalysisUiState
    data class Result(
        val analysis: HonorDuelShopAnalysis,
        val recommendations: List<ShopRecommendation>,
        val snapshotId: Long,
        val headerSources: Map<com.asp0902.mobilegameassistant.analysis.HeaderField, ReconciliationSource> = emptyMap(),
    ) : ShopAnalysisUiState
    data class Error(val message: String) : ShopAnalysisUiState
}
