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
fun DreamRealmPanel(dreamRealm: Map<String, Any>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Text("꿈의 추격 (2026-09-28, 「달빛의 장막」)", style = MaterialTheme.typography.titleMedium)
            Text("매주 월요일 09:00(UTC+9) 갱신 | 보스 5마리 5파티 연속 도전 | 보스당 90초 제한")
            Text("다음 신규 보스: 미스 루스타 (추정)")
        }

        @Suppress("UNCHECKED_CAST")
        val bosses = (dreamRealm["bosses"] as? List<Map<String, Any>>) ?: emptyList()

        items(bosses) { bossMap ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val name = bossMap["name"] as? String ?: "Unknown"
                    val level = bossMap["level"] as? Int ?: 0
                    val job = bossMap["job"] as? String ?: ""
                    val attack = bossMap["attack"] as? String ?: ""
                    val range = bossMap["range"] as? Int ?: 0

                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text("Lv.$level $job | $attack/$range", 
                            style = MaterialTheme.typography.labelSmall)
                    }

                    val counters = bossMap["counters"] as? String ?: ""
                    Text("⚡ $counters", 
                        style = MaterialTheme.typography.labelSmall, 
                        modifier = Modifier
                            .background(Color(0xFFE8F5E9), shape = MaterialTheme.shapes.small)
                            .padding(8.dp))

                    @Suppress("UNCHECKED_CAST")
                    val result = bossMap["bestResult"] as? Map<String, Any> ?: emptyMap()
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .background(Color(0xFFF0F0F0), shape = MaterialTheme.shapes.small)
                            .padding(8.dp)
                    ) {
                        val resultText = when {
                            result["killed"] == true -> "✓ 처치 | ${result["time"]}"
                            result.containsKey("percentage") -> "${result["percentage"]}% (${result["attempt"]}회차)"
                            else -> "미기록"
                        }
                        Text("최고 기록: $resultText", style = MaterialTheme.typography.labelSmall)
                        
                        @Suppress("UNCHECKED_CAST")
                        val comp = result["composition"] as? List<String>
                        comp?.let {
                            Text("편성: ${it.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        result["echo"]?.let { echo ->
                            Text("메아리: $echo", style = MaterialTheme.typography.labelSmall)
                        }
                        
                        result["note"]?.let { note ->
                            Text("📝 $note", style = MaterialTheme.typography.labelSmall, color = Color(0xFF666666))
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("학습 결론", style = MaterialTheme.typography.titleSmall)
                    @Suppress("UNCHECKED_CAST")
                    val conclusions = (dreamRealm["conclusions"] as? List<String>) ?: emptyList()
                    conclusions.forEach { conclusion ->
                        Text("• $conclusion", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
