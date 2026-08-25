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
import com.asp0902.mobilegameassistant.artisans.ArtisansPathAdvisor
import com.asp0902.mobilegameassistant.artisans.ArtisansPathAnalysis
import com.asp0902.mobilegameassistant.formation.FormationTemplate
import com.asp0902.mobilegameassistant.formation.FormationTemplateId
import com.asp0902.mobilegameassistant.formation.FormationTemplates
import com.asp0902.mobilegameassistant.overlay.RecommendationOverlayController
import com.asp0902.mobilegameassistant.rules.HonorDuelRuleEngine
import com.asp0902.mobilegameassistant.rules.RunRecommendation
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
    private val overlayController: RecommendationOverlayController,
) : ViewModel() {
    val state = captureSession.state
    val frame = captureSession.frame
    private val mutableTemplate = MutableStateFlow<FormationTemplate?>(null)
    val template = mutableTemplate.asStateFlow()
    private val mutableAnalysis = MutableStateFlow<ShopAnalysisUiState>(ShopAnalysisUiState.Idle)
    val analysis = mutableAnalysis.asStateFlow()
    private val mutableArtisansAnalysis = MutableStateFlow<ArtisansPathAnalysis?>(null)
    val artisansAnalysis = mutableArtisansAnalysis.asStateFlow()
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
                    mutableAnalysis.value = ShopAnalysisUiState.Result(restored.analysis, ruleEngine.recommend(restored.analysis), ruleEngine.recommendRunActions(restored.analysis, restored.progress.status), nextSnapshotId++, restored.headerSources, runStatus = restored.progress.status)
                }
            }
        }
        viewModelScope.launch {
            captureSession.frame.filterNotNull().collectLatest { bitmap ->
                hasLiveAnalysis = true
                mutableAnalysis.value = ShopAnalysisUiState.Analyzing
                runCatching {
                    withContext(Dispatchers.Default) {
                        shopAnalyzer.analyze(bitmap, runId)
                    }
                }.onSuccess { result ->
                    val artisans = ArtisansPathAdvisor.analyze(result.ocrBlocks.joinToString(" ") { it.text })
                    mutableArtisansAnalysis.value = artisans
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
                    val outcome = BattleResultRecognizer.recognize(observed.ocrBlocks.joinToString(" ") { it.text })
                    val progress = BattleResultTracker.apply(lastRunState?.progress ?: reconciled.progress, outcome)
                    val tracked = reconciled.copy(analysis = BattleResultTracker.apply(reconciled.analysis, progress), progress = progress)
                    lastRunState = tracked
                    if (tracked.analysis.shopItems.isNotEmpty()) lastShopAnalysis = tracked.analysis
                    val snapshotId = nextSnapshotId++
                    val shopRecommendations = ruleEngine.recommend(tracked.analysis)
                    val runRecommendations = ruleEngine.recommendRunActions(tracked.analysis, tracked.progress.status)
                    mutableAnalysis.value = ShopAnalysisUiState.Result(tracked.analysis, shopRecommendations, runRecommendations, snapshotId, tracked.headerSources, runStatus = tracked.progress.status)
                    updateOverlay(artisans, shopRecommendations, runRecommendations)
                    viewModelScope.launch(Dispatchers.IO) {
                        runRepository.save(tracked)
                        runRepository.reconcilePurchases(runId, tracked.analysis)
                    }
                }.onFailure {
                    overlayController.hide()
                    mutableAnalysis.value = ShopAnalysisUiState.Error("OCR 분석 실패: ${it.message ?: "알 수 없음"}")
                }
            }
        }
    }

    fun requestTracking() = captureSession.awaitConsent()

    fun cancelRequest() = captureSession.idle("화면 공유가 취소되었습니다.")

    private fun updateOverlay(
        artisans: ArtisansPathAnalysis?,
        shop: List<ShopRecommendation>,
        run: List<RunRecommendation>,
    ) {
        val text = when {
            artisans != null -> buildString {
                append("장인의 길\n")
                artisans.recommendations.take(2).forEach { append("${it.cardName}: ${it.action}\n") }
            }
            shop.isNotEmpty() -> buildString {
                append("명예의 결투\n")
                shop.take(3).forEach { append("${it.slotIndex + 1}: ${it.action}\n") }
                run.firstOrNull()?.let { append(it.action) }
            }
            else -> null
        }?.trim()
        if (text == null) overlayController.hide() else overlayController.show(text)
    }

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
        val shopRecommendations = ruleEngine.recommend(corrected)
        val runRecommendations = ruleEngine.recommendRunActions(corrected, current.runStatus)
        mutableAnalysis.value = ShopAnalysisUiState.Result(corrected, shopRecommendations, runRecommendations, snapshotId, current.headerSources, runStatus = current.runStatus)
        updateOverlay(null, shopRecommendations, runRecommendations)
        viewModelScope.launch {
            correctionRepository.save(HeroCorrection(
                runId, snapshotId, slotIndex, item.heroId, hero.id, hero.koreanName, item.portraitSignature,
            ))
        }
    }

    fun recordHeroPurchase(snapshotId: Long, slotIndex: Int) {
        val current = (mutableAnalysis.value as? ShopAnalysisUiState.Result) ?: return
        val item = current.analysis.shopItems.firstOrNull { it.slotIndex == slotIndex } ?: return
        val heroId = item.heroId ?: return
        val action = HeroPurchaseAction(
            actionId = "$runId:$snapshotId:$slotIndex",
            heroId = heroId,
            quantity = item.quantity ?: 1,
            cost = item.price ?: return,
        )
        val prediction = PurchasePredictor.apply(current.analysis, action)
        viewModelScope.launch {
            if (!withContext(Dispatchers.IO) { runRepository.savePurchase(runId, action, prediction) }) return@launch
            val sources = current.headerSources + (com.asp0902.mobilegameassistant.analysis.HeaderField.CURRENCY to ReconciliationSource.ACTION_PREDICTION)
            lastRunState = ReconciledHonorDuelState(runId, prediction.analysis, sources)
            lastShopAnalysis = prediction.analysis
            val shopRecommendations = ruleEngine.recommend(prediction.analysis)
            val runRecommendations = ruleEngine.recommendRunActions(prediction.analysis, lastRunState?.progress?.status ?: RunStatus.ACTIVE)
            mutableAnalysis.value = ShopAnalysisUiState.Result(
                prediction.analysis,
                shopRecommendations,
                runRecommendations,
                snapshotId,
                sources,
                "구매 예상 기록: ${item.heroName ?: heroId} ${action.quantity}장 / ${action.cost}",
                lastRunState?.progress?.status ?: RunStatus.ACTIVE,
            )
            updateOverlay(null, shopRecommendations, runRecommendations)
        }
    }
}

sealed interface ShopAnalysisUiState {
    data object Idle : ShopAnalysisUiState
    data object Analyzing : ShopAnalysisUiState
    data class Result(
        val analysis: HonorDuelShopAnalysis,
        val recommendations: List<ShopRecommendation>,
        val runRecommendations: List<RunRecommendation>,
        val snapshotId: Long,
        val headerSources: Map<com.asp0902.mobilegameassistant.analysis.HeaderField, ReconciliationSource> = emptyMap(),
        val actionMessage: String? = null,
        val runStatus: RunStatus = RunStatus.ACTIVE,
    ) : ShopAnalysisUiState
    data class Error(val message: String) : ShopAnalysisUiState
}
