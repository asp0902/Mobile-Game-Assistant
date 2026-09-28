package com.asp0902.mobilegameassistant.learning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DreamRealmPanel(data: DreamRealmData) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Title and basic boss info
        item {
            Text("꿈의 세계", style = MaterialTheme.typography.titleLarge)
        }

        // Boss information card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${data.boss.name} - ${data.boss.title}", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("등급", style = MaterialTheme.typography.labelSmall)
                            Text(data.boss.type, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("레벨", style = MaterialTheme.typography.labelSmall)
                            Text(data.boss.level.toString(), style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("추정 HP", style = MaterialTheme.typography.labelSmall)
                            Text(data.boss.estimatedHP, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Boss skills section
        item {
            Text("보스 스킬", style = MaterialTheme.typography.titleSmall)
        }
        items(data.skills) { skill ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(skill, style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }
        }

        // Best composition section
        item {
            Text("최적 구성", style = MaterialTheme.typography.titleSmall)
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.bestComposition.forEach { (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(key, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                value.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF616161)
                            )
                        }
                    }
                }
            }
        }

        // Note section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("유의사항", style = MaterialTheme.typography.labelSmall)
                    Text("보스 스킬 개념과 최적 구성은 현재 시험 단계입니다. 실제 전투는 서버 상태, 선택지 변동, 랜덤 요소의 영향을 받습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5D4037))
                }
            }
        }
    }
}
