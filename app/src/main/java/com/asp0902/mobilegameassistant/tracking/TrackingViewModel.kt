package com.asp0902.mobilegameassistant.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalyzer
import com.asp0902.mobilegameassistant.analysis.HeroCorrection
import com.asp0902.mobilegameassistant.analysis.HeroCorrectionRepository
import com.asp0902.mobilegameassistant.analysis.HeroCorrectionApplier
import com.asp0902.mobilegameassistant.analysis.HeroRecognitionCatalog
import com.asp0902.mobilegameassistant.analysis.InitialFormationOffer
import com.asp0902.mobilegameassistant.analysis.InitialFormationAdvisor
import com.asp0902.mobilegameassistant.analysis.InitialFormationRecommendation
import com.asp0902.mobilegameassistant.analysis.InitialFormationAction
import com.asp0902.mobilegameassistant.analysis.GameViewport
import com.asp0902.mobilegameassistant.analysis.ScreenType
import com.asp0902.mobilegameassistant.analysis.ShopDetailReconciler
import com.asp0902.mobilegameassistant.capture.CaptureSession
import com.asp0902.mobilegameassistant.artisans.ArtisansPathAdvisor
import com.asp0902.mobilegameassistant.artisans.ArtisansPathAnalysis
import com.asp0902.mobilegameassistant.artisans.ArtisansAction
import com.asp0902.mobilegameassistant.formation.FormationTemplate
import com.asp0902.mobilegameassistant.formation.FormationTemplateId
import com.asp0902.mobilegameassistant.formation.FormationTemplates
import com.asp0902.mobilegameassistant.labyrinth.LabyrinthChoiceAdvisor
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round
import java.util.concurrent.atomic.AtomicLong
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
    private var lastArtisansSelectionSignature: String? = null
    private var artisansStableFrames = 0
    private val analysisSequence = AtomicLong(0)

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
                val analysisId = analysisSequence.incrementAndGet()
                hasLiveAnalysis = true
                overlayController.hide()
                mutableAnalysis.value = ShopAnalysisUiState.Analyzing
                runCatching {
                    withContext(Dispatchers.Default) {
                        shopAnalyzer.analyze(bitmap, runId)
                    }
                }.onSuccess { result ->
                    if (analysisId != analysisSequence.get()) return@onSuccess
                    if (result.screenType == ScreenType.HONOR_DUEL_START) {
                        mutableArtisansAnalysis.value = null
                        resetArtisansStability()
                        mutableAnalysis.value = ShopAnalysisUiState.HonorDuelStart
                        overlayController.show("명예의 결투 시작 화면\n지금 시작을 누르면 다음 화면을 분석합니다.")
                        return@onSuccess
                    }
                    if (result.screenType == ScreenType.HONOR_DUEL_INITIAL_FORMATION_SELECTION) {
                        mutableArtisansAnalysis.value = null
                        resetArtisansStability()
                        val formationRecommendations = recommendInitialFormation(result.initialFormation)
                        val formationTargets = initialFormationTargets(result.initialFormation, formationRecommendations)
                        mutableAnalysis.value = ShopAnalysisUiState.HonorDuelInitialFormation(
                            offers = result.initialFormation,
                            recommendations = formationRecommendations,
                        )
                        val overlayText = buildString {
                            append("명예의 결투 초기 진형 선택 화면")
                            val best = formationRecommendations.firstOrNull { it.action == InitialFormationAction.SELECT }
                            if (best != null) {
                                append("\n추천: ${best.slotIndex + 1}번")
                                append("\n")
                                append(best.reason.take(240))
                            } else {
                                append("\n동률 또는 미확정 후보: 앱에서 카드별 비교 근거 확인")
                            }
                        }
                        updateOverlay(null, emptyList(), emptyList(), textOverride = overlayText, targets = formationTargets)
                        return@onSuccess
                    }
                    val ocrText = result.ocrBlocks.joinToString(" ") { it.text }
                    LabyrinthChoiceAdvisor.analyze(ocrText)?.let { advice ->
                        mutableArtisansAnalysis.value = null
                        resetArtisansStability()
                        mutableAnalysis.value = ShopAnalysisUiState.Labyrinth(advice.message)
                        overlayController.show("이계의 미궁\n${advice.message}")
                        return@onSuccess
                    }
                    val artisans = ArtisansPathAdvisor.analyze(
                        ocrText,
                        result.ocrBlocks,
                        mutableArtisansAnalysis.value?.score,
                        screenConfirmed = result.screenType == ScreenType.ARTISANS_PATH_CARD_SELECTION,
                    )
                    val isArtisansScreen = result.screenType == ScreenType.ARTISANS_PATH_CARD_SELECTION
                    mutableArtisansAnalysis.value = if (isArtisansScreen) artisans else null
                    if (isArtisansScreen) {
                        val artisanTargets = if (isArtisansStable(artisans)) artisanTargets(artisans, result.viewport) else emptyList()
                        updateOverlay(artisans, emptyList(), emptyList(), targets = artisanTargets)
                        return@onSuccess
                    }
                    if (!shouldRunHonorDuelPipeline(result.screenType)) {
                        resetArtisansStability()
                        mutableAnalysis.value = ShopAnalysisUiState.UnsupportedScreen(result.screenType, result.screenReasons)
                        updateOverlay(null, emptyList(), emptyList())
                        return@onSuccess
                    }
                    resetArtisansStability()
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
                    updateOverlay(null, shopRecommendations, runRecommendations)
                    viewModelScope.launch(Dispatchers.IO) {
                        runRepository.save(tracked)
                        runRepository.reconcilePurchases(runId, tracked.analysis)
                    }
                }.onFailure {
                    if (it is CancellationException || analysisId != analysisSequence.get()) return@onFailure
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
        textOverride: String? = null,
        targets: List<RecommendationOverlayController.OverlayTarget> = emptyList(),
    ) {
        val text = textOverride ?: when {
            artisans != null -> null
            shop.isNotEmpty() -> buildString {
                append("명예의 결투\n")
                shop.take(3).forEach { append("${it.slotIndex + 1}: ${it.action}\n") }
                run.firstOrNull()?.let { append(it.action) }
            }
            else -> null
        }?.trim()
        if (text == null && targets.isEmpty()) {
            overlayController.hide()
            return
        }
        overlayController.show(text?.ifBlank { null }, targets)
    }

    private fun artisanTargets(
        artisans: ArtisansPathAnalysis?,
        viewport: GameViewport?,
    ): List<RecommendationOverlayController.OverlayTarget> {
        if (artisans == null) return emptyList()
        if (artisans.candidates.size != 3) return emptyList()
        val selected = artisans.recommendations.firstOrNull { it.action == ArtisansAction.SELECT } ?: return emptyList()
        val slot = selected.slotIndex ?: return emptyList()
        val sourceBounds = artisans.candidates.firstOrNull { it.slotIndex == slot }?.fullCardBounds ?: return emptyList()
        if (sourceBounds.right <= sourceBounds.left || sourceBounds.bottom <= sourceBounds.top) return emptyList()

        val mappedLeft = mapToViewportX(sourceBounds.left, viewport)
        val mappedRight = mapToViewportX(sourceBounds.right, viewport)
        val mappedTop = mapToViewportY(sourceBounds.top, viewport)
        val mappedBottom = mapToViewportY(sourceBounds.bottom, viewport)
        if (mappedLeft >= mappedRight || mappedTop >= mappedBottom) return emptyList()

        val paddedLeft = (mappedLeft - 0.01f).coerceIn(0f, 1f)
        val paddedRight = (mappedRight + 0.01f).coerceIn(0f, 1f)
        val paddedTop = (mappedTop - 0.015f).coerceIn(0f, 1f)
        val paddedBottom = (mappedBottom + 0.015f).coerceIn(0f, 1f)
        return listOf(
            RecommendationOverlayController.OverlayTarget(
                left = paddedLeft,
                right = paddedRight,
                top = paddedTop,
                bottom = paddedBottom,
                action = RecommendationOverlayController.OverlayTarget.Action.SELECT,
            ),
        )
    }

    private fun initialFormationTargets(
        offers: List<InitialFormationOffer>,
        recommendations: List<InitialFormationRecommendation>,
    ): List<RecommendationOverlayController.OverlayTarget> {
        if (offers.size != 4 || offers.any { it.selectButtonBounds == null }) return emptyList()
        val selected = recommendations.singleOrNull { it.action == InitialFormationAction.SELECT } ?: return emptyList()
        val offer = offers.firstOrNull { it.slotIndex == selected.slotIndex } ?: return emptyList()
        if (offer.artifactSource != "OCR_MATCH" || offer.heroSlots.any { it.status != com.asp0902.mobilegameassistant.analysis.HeroRecognitionStatus.CONFIRMED }) return emptyList()
        val bounds = offer.selectButtonBounds ?: return emptyList()
        if (bounds.left >= bounds.right || bounds.top >= bounds.bottom) return emptyList()
        return listOf(
            RecommendationOverlayController.OverlayTarget(
                left = bounds.left,
                right = bounds.right,
                top = bounds.top,
                bottom = bounds.bottom,
                action = RecommendationOverlayController.OverlayTarget.Action.SELECT,
            ),
        )
    }

    private fun recommendInitialFormation(offers: List<InitialFormationOffer>) = InitialFormationAdvisor.recommend(offers)

    private fun isArtisanSelectionConfirmed(artisans: ArtisansPathAnalysis?): Boolean {
        if (artisans == null) return false
        if (artisans.candidates.size != 3) return false
        if (artisans.recommendations.count { it.action == ArtisansAction.SELECT } != 1) return false
        val selected = artisans.recommendations.first { it.action == ArtisansAction.SELECT }
        val selectedCandidate = artisans.candidates.firstOrNull { it.slotIndex == selected.slotIndex } ?: return false
        return selectedCandidate.fullCardBounds.right > selectedCandidate.fullCardBounds.left &&
            selectedCandidate.fullCardBounds.bottom > selectedCandidate.fullCardBounds.top
    }

    private fun isArtisansStable(
        artisans: ArtisansPathAnalysis?,
    ): Boolean {
        if (!isArtisanSelectionConfirmed(artisans)) {
            resetArtisansStability()
            return false
        }
        val signature = artisanSelectionSignature(artisans!!) ?: run {
            resetArtisansStability()
            return false
        }
        if (signature != lastArtisansSelectionSignature) {
            lastArtisansSelectionSignature = signature
            artisansStableFrames = 1
            return false
        }
        artisansStableFrames++
        return artisansStableFrames >= 2
    }

    private fun artisanSelectionSignature(artisans: ArtisansPathAnalysis): String? {
        val selected = artisans.recommendations.firstOrNull { it.action == ArtisansAction.SELECT } ?: return null
        val selectedCandidate = artisans.candidates.firstOrNull { it.slotIndex == selected.slotIndex } ?: return null
        return buildString {
            append(artisans.round)
            append('|')
            append(artisans.candidates.joinToString(";") { "${it.slotIndex}:${it.name}" })
            append('|')
            append(selected.cardName)
            append('|')
            append(selected.slotIndex)
            append('|')
            append(round(selectedCandidate.fullCardBounds.left * 2000f) / 2000f)
            append('|')
            append(round(selectedCandidate.fullCardBounds.top * 2000f) / 2000f)
            append('|')
            append(round(selectedCandidate.fullCardBounds.right * 2000f) / 2000f)
            append('|')
            append(round(selectedCandidate.fullCardBounds.bottom * 2000f) / 2000f)
        }
    }

    private fun resetArtisansStability() {
        lastArtisansSelectionSignature = null
        artisansStableFrames = 0
    }

    private fun mapToViewportX(value: Float, viewport: GameViewport?): Float {
        if (viewport == null) return value
        val width = viewport.right - viewport.left
        if (width <= 0f) return value
        return ((value - viewport.left) / width).coerceIn(0f, 1f)
    }

    private fun mapToViewportY(value: Float, viewport: GameViewport?): Float {
        if (viewport == null) return value
        val height = viewport.bottom - viewport.top
        if (height <= 0f) return value
        return ((value - viewport.top) / height).coerceIn(0f, 1f)
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

internal fun shouldRunHonorDuelPipeline(screenType: ScreenType): Boolean = when (screenType) {
    ScreenType.HONOR_DUEL_START,
    ScreenType.HONOR_DUEL_INITIAL_FORMATION_SELECTION,
    ScreenType.ARTISANS_PATH_CARD_SELECTION,
    ScreenType.OTHER,
    ScreenType.UNKNOWN -> false
    else -> true
}

internal fun isNeutralScreen(screenType: ScreenType): Boolean =
    screenType == ScreenType.OTHER || screenType == ScreenType.UNKNOWN

sealed interface ShopAnalysisUiState {
    data object Idle : ShopAnalysisUiState
    data object Analyzing : ShopAnalysisUiState
    data object HonorDuelStart : ShopAnalysisUiState
    data class HonorDuelInitialFormation(
        val offers: List<InitialFormationOffer>,
        val recommendations: List<InitialFormationRecommendation>,
    ) : ShopAnalysisUiState
    data class Labyrinth(val message: String) : ShopAnalysisUiState
    data class UnsupportedScreen(
        val screenType: ScreenType,
        val reasons: List<String> = emptyList(),
    ) : ShopAnalysisUiState
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

data class InitialFormationRecommendation(
    val slotIndex: Int,
    val score: Int,
    val action: InitialFormationAction,
    val reason: String,
    val hasUncertainty: Boolean = false,
)

enum class InitialFormationAction {
    SELECT,
    CONSIDER,
    SKIP,
}
