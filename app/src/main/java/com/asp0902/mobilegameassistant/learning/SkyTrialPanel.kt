package com.asp0902.mobilegameassistant.learning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SkyTrialPanel(skyTrial: SkyTrialData) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Text("천공의 시련 (2026-09-23 ~ 2027-01)", style = MaterialTheme.typography.titleMedium)
            Text("기간: 시즌 1단계 1~80층, 2단계 81~140층, 3단계 141~200층")
            Text("층 보상: 과거의 단편 ×150 + 골드 ×120K | 메아리 11~20레벨 상승용, +14 필요 공명: 325")
        }
        items(skyTrial.warnings) { warning ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (warning.severity) {
                        "HIGH" -> Color(0xFFFFEEEE)
                        "MEDIUM" -> Color(0xFFFFF9C4)
                        else -> Color(0xFFE8F5E9)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("⚠️ ${warning.type}", style = MaterialTheme.typography.labelSmall)
                    Text(warning.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (skyTrial.phantomMapping.isNotEmpty()) {
            item {
                Text("팬텀 배치", style = MaterialTheme.typography.titleSmall)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        skyTrial.phantomMapping.forEach { (tower, phantom) ->
                            Text("$tower: $phantom", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        items(skyTrial.towers) { tower ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(tower.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${tower.currentFloor}층 ${tower.status}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tower.status == "클리어") Color(0xFF2E7D32) else Color(0xFF1565C0)
                        )
                    }
                    Text("진영: ${tower.faction} | 요일: ${tower.openDays.joinToString(", ")}")
                    Text("팬텀: ${tower.phantom}")
                    tower.note?.let { Text("📝 $it", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}
