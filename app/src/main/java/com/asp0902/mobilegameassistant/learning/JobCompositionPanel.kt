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
fun JobCompositionPanel(jobCompositions: Map<String, Any>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Text("직업별 편성 추천 (2026-09-28)", style = MaterialTheme.typography.titleMedium)
            Text("확정(USER_CONFIRMED) 및 시험(TRIAL) 편성. 기믹별 대응 전략 포함.")
        }

        @Suppress("UNCHECKED_CAST")
        val compositions = (jobCompositions["compositions"] as? List<Map<String, Any>>) ?: emptyList()

        items(compositions) { comp ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (comp["status"] as? String) {
                        "USER_CONFIRMED" -> Color(0xFFE8F5E9)
                        "TRIAL" -> Color(0xFFFFF9C4)
                        else -> Color(0xFFF5F5F5)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val name = comp["name"] as? String ?: ""
                    val difficulty = comp["difficulty"] as? String ?: ""
                    val status = comp["status"] as? String ?: ""

                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text("$difficulty | $status", style = MaterialTheme.typography.labelSmall)
                    }

                    @Suppress("UNCHECKED_CAST")
                    val heroes = (comp["heroes"] as? List<String>) ?: emptyList()
                    if (heroes.isNotEmpty()) {
                        Text("영웅: ${heroes.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
                    }

                    comp["echo"]?.let { echo ->
                        Text("메아리: $echo", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
                    }

                    @Suppress("UNCHECKED_CAST")
                    val req = comp["requirements"] as? Map<String, Any>
                    if (req != null && req.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .background(Color(0xFFF0F0F0), shape = MaterialTheme.shapes.small)
                                .padding(8.dp)
                        ) {
                            Text("조건", style = MaterialTheme.typography.labelSmall)
                            req.forEach { (key, value) ->
                                Text("• $key: $value", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    @Suppress("UNCHECKED_CAST")
                    val result = comp["result"] as? Map<String, Any>
                    if (result != null && result.isNotEmpty()) {
                        val resultText = when {
                            result["killed"] == true -> "✓ 처치 | ${result["time"]}"
                            result.containsKey("percentage") -> "${result["percentage"]}% 공략"
                            else -> "시험 중"
                        }
                        Text(resultText, style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                        result["notes"]?.let { notes ->
                            Text("📝 $notes", style = MaterialTheme.typography.labelSmall, color = Color(0xFF666666))
                        }
                    }
                }
            }
        }
    }
}
