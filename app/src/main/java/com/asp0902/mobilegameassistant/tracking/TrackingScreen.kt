package com.asp0902.mobilegameassistant.tracking

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.asp0902.mobilegameassistant.capture.TrackingState
import com.asp0902.mobilegameassistant.formation.FormationOverlay
import com.asp0902.mobilegameassistant.formation.FormationTemplate
import com.asp0902.mobilegameassistant.formation.FormationTemplateId
import com.asp0902.mobilegameassistant.rules.RecommendationAction

@Composable
fun TrackingScreen(
    state: TrackingState,
    frame: Bitmap?,
    template: FormationTemplate?,
    analysis: ShopAnalysisUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTemplateSelected: (FormationTemplateId) -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("AFK: 새로운 여정 트래커", style = MaterialTheme.typography.headlineSmall)
            Text(statusText(state))

            when (state) {
                is TrackingState.Idle -> Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStart,
                ) {
                    Text("트래킹 시작")
                }

                TrackingState.AwaitingConsent,
                TrackingState.Starting,
                -> CircularProgressIndicator()

                TrackingState.Tracking -> {
                    Text("게임 화면에서 알림의 ‘AFK 분석’을 누르세요.")
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onStop,
                    ) {
                        Text("트래킹 중지")
                    }
                    if (frame == null) {
                        Text("아직 캡처된 화면이 없습니다.")
                    } else {
                        Text(template?.let { "필드 캐시: ${it.id.displayName}" } ?: "필드 선택 필요")
                        FormationTemplateId.entries.forEach { id ->
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onTemplateSelected(id) },
                            ) {
                                Text(id.displayName)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(frame.width.toFloat() / frame.height),
                        ) {
                            Image(
                                bitmap = frame.asImageBitmap(),
                                contentDescription = "최근 캡처 화면",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                            template?.let { FormationOverlay(it, Modifier.fillMaxSize()) }
                        }
                        AnalysisSummary(analysis)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisSummary(analysis: ShopAnalysisUiState) {
    when (analysis) {
        ShopAnalysisUiState.Idle -> Text("AFK 분석을 누르면 상점 OCR을 시작합니다.")
        ShopAnalysisUiState.Analyzing -> Text("상점 분석 중")
        is ShopAnalysisUiState.Error -> Text(analysis.message)
        is ShopAnalysisUiState.Result -> {
            val header = analysis.analysis.header
            Text(
                "${analysis.analysis.screenType} (${(analysis.analysis.screenConfidence * 100).toInt()}%) · 휘장 ${header.currency ?: "?"} · " +
                    "상점 Lv.${header.shopLevel ?: "?"} · " +
                    "아티팩트 ${header.artifactXp?.current ?: "?"}/${header.artifactXp?.required ?: "?"}",
            )
            Text(analysis.analysis.screenReasons.joinToString(" · "))
            analysis.recommendations.forEach { recommendation ->
                val action = when (recommendation.action) {
                    RecommendationAction.BUY -> "BUY"
                    RecommendationAction.CONSIDER -> "확인"
                    RecommendationAction.SKIP -> "SKIP"
                }
                Text("슬롯 ${recommendation.slotIndex + 1}: $action — ${recommendation.reason}")
            }
        }
    }
}

private fun statusText(state: TrackingState): String = when (state) {
    is TrackingState.Idle -> state.message ?: "대기 중"
    TrackingState.AwaitingConsent -> "화면 공유 권한을 기다리는 중"
    TrackingState.Starting -> "트래킹을 시작하는 중"
    TrackingState.Tracking -> "트래킹 중"
}
