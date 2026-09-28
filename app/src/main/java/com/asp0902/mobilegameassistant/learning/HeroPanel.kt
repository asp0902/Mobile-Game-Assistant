package com.asp0902.mobilegameassistant.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HeroPanel(heroes: List<HeroInfo>) {
    var query by rememberSaveable { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("레오프론 영웅 (${heroes.size}명)", style = MaterialTheme.typography.titleLarge)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("영웅 검색 (이름/칭호/직업)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        val filtered = heroes.filter { hero ->
            query.isBlank() ||
            hero.name.contains(query, ignoreCase = true) ||
            hero.title?.contains(query, ignoreCase = true) == true ||
            hero.role.contains(query, ignoreCase = true)
        }

        items(filtered, key = { it.id }) { hero ->
            HeroCard(hero)
        }
    }
}

@Composable
fun HeroCard(hero: HeroInfo) {
    val rankColor = when (hero.rank) {
        "신화" -> Color(0xFF9C27B0)
        "레전드", "레전드+" -> Color(0xFFD32F2F)
        "에픽", "에픽+" -> Color(0xFFFFA500)
        else -> Color(0xFF757575)
    }

    val roleColor = when (hero.role) {
        "탱커" -> Color(0xFF2196F3)
        "서포터" -> Color(0xFF4CAF50)
        "마법사", "사수" -> Color(0xFFFF9800)
        "전사", "레인저" -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Name and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(hero.name, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF212121))
                    if (hero.title != null && hero.title != "미정") {
                        Text(hero.title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF616161))
                    }
                }
                Text(hero.rank, style = MaterialTheme.typography.labelMedium, color = rankColor)
            }

            // Role and range info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(hero.role, style = MaterialTheme.typography.labelSmall, color = roleColor)
                if (hero.attackType != null && hero.attackType != "미정") {
                    Text(hero.attackType, style = MaterialTheme.typography.labelSmall, color = Color(0xFF7B1FA2))
                }
                if (hero.range != null && hero.range > 0) {
                    Text("사거리 ${hero.range}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF455A64))
                }
            }

            // Rarity and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hero.rarity != null && hero.rarity != "미정") {
                    Text("${hero.rarity}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF616161))
                }
                if (hero.asOf != null) {
                    Text(hero.asOf, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9E9E9E))
                }
            }
        }
    }
}
