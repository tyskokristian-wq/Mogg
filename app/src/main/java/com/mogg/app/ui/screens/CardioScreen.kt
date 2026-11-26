package com.mogg.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogg.app.data.Cardio
import com.mogg.app.data.CardioMode
import com.mogg.app.data.Nutrition
import com.mogg.app.data.Store
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardioScreen(onStart: () -> Unit) {
    val state by Store.state.collectAsState()
    val p = state.profile
    val day = state.days[Store.today()] ?: Store.day()
    val plan = Nutrition.plan(p, day.eatenTotal)
    val left = (plan.cardioTarget - day.cardioKcal).coerceAtLeast(0)
    val perMin = Cardio.kcalPerMinute(p)
    val minutes = Cardio.minutesFor(left, p)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Кардио", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Комфортно, без бега до смерти. Пока идёшь — смотришь MrBeast.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        Text("Режим", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = p.cardioMode == CardioMode.TREADMILL, onClick = { Store.updateProfile { it.copy(cardioMode = CardioMode.TREADMILL) } }, label = { Text("Беговая дорожка") })
            FilterChip(selected = p.cardioMode != CardioMode.TREADMILL, onClick = { if (p.cardioMode == CardioMode.TREADMILL) Store.updateProfile { it.copy(cardioMode = CardioMode.WALK) } }, label = { Text("Обычное кардио") })
        }
        if (p.cardioMode != CardioMode.TREADMILL) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CardioMode.WALK, CardioMode.BIKE, CardioMode.ELLIPTICAL).forEach { m ->
                    FilterChip(selected = p.cardioMode == m, onClick = { Store.updateProfile { it.copy(cardioMode = m) } }, label = { Text(m.title) })
                }
            }
        }

        if (p.cardioMode == CardioMode.TREADMILL) {
            Spacer(Modifier.height(8.dp))
            Text("Скорость: ${"%.1f".format(p.treadmillSpeed)} км/ч")
            Slider(value = p.treadmillSpeed.toFloat(), valueRange = 3f..10f, onValueChange = { v -> Store.updateProfile { it.copy(treadmillSpeed = (v * 2).roundToInt() / 2.0) } })
            Text("Наклон: ${"%.0f".format(p.treadmillIncline)}%")
            Slider(value = p.treadmillIncline.toFloat(), valueRange = 0f..15f, onValueChange = { v -> Store.updateProfile { it.copy(treadmillIncline = v.roundToInt().toDouble()) } })
            Text("Совет: 5 км/ч и наклон 8–12% — горит как бег, но идёшь спокойно и колени целы.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Сегодня: ${day.cardioKcal} / ${plan.cardioTarget} ккал · ${day.cardioMinutes} мин", fontWeight = FontWeight.Bold)
                Text(Cardio.description(p))
                Text("≈ ${"%.1f".format(perMin)} ккал/мин при весе ${p.weightKg} кг")
                Spacer(Modifier.height(6.dp))
                if (left > 0) {
                    val sessions = Cardio.sessions(minutes)
                    Text("Осталось ~$left ккал = $minutes мин", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (sessions.size > 1) Text("Лучше разбить: " + sessions.joinToString(" + ") { "$it мин" } + " (утро/вечер)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("План по кардио выполнен ✅. Можно ещё погулять — просто для здоровья.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                plan.warnings.firstOrNull()?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Начать кардио", fontSize = 18.sp) }
        Spacer(Modifier.height(16.dp))
    }
}
