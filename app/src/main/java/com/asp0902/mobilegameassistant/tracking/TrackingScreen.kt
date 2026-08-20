package com.asp0902.mobilegameassistant.tracking

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

@Composable
fun TrackingScreen(
    state: TrackingState,
    frame: Bitmap?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAnalyze: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
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
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onAnalyze,
                    ) {
                        Text("AFK 분석")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onStop,
                    ) {
                        Text("트래킹 중지")
                    }
                    if (frame == null) {
                        Text("아직 캡처된 화면이 없습니다.")
                    } else {
                        Image(
                            bitmap = frame.asImageBitmap(),
                            contentDescription = "최근 캡처 화면",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
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
