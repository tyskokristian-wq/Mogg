package com.mogg.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogg.app.cardio.IntervalPlanner
import com.mogg.app.cardio.Pace
import com.mogg.app.data.CardioMode
import com.mogg.app.data.Nutrition
import com.mogg.app.data.Store
import com.mogg.app.ui.YesSign
import com.mogg.app.ui.theme.LightGreen
import com.mogg.app.ui.theme.SkyBlue
import com.mogg.app.ui.theme.WarnBg

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardioScreen(onStart: () -> Unit) {
    val state by Store.state.collectAsState()
    val p = state.profile
    val day = state.days[Store.today()] ?: Store.day()
    val plan = Nutrition.plan(p, day.eatenTotal)
    val left = (plan.cardioTarget - day.cardioKcal).coerceAtLeast(0)
    val minutes = IntervalPlanner.pickMinutes(p, left)
    val session = IntervalPlanner.build(p, left, minutes)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Кардио", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            YesSign(100.dp, -10f)
        }
        Text("Калории всегда ${plan.cardioTarget}. Выбираешь время: меньше минут — больше бега и быстрее, больше минут — легче.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        Text("Сколько минут", fontWeight = FontWeight.SemiBold)
        Column {
            IntervalPlanner.timeOptions(p, left).forEach { m ->
                FilterChip(
                    selected = minutes == m,
                    onClick = { Store.updateProfile { it.copy(cardioSessionMinutes = m) } },
                    label = { Text("$m мин — ${IntervalPlanner.build(p, left, m).patternName}") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        Text("Где", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                CardioMode.TREADMILL to "Беговая дорожка",
                CardioMode.WALK to "На улице",
                CardioMode.BIKE to "Велосипед",
                CardioMode.ELLIPTICAL to "Эллипс",
            ).forEach { (m, t) ->
                FilterChip(selected = p.cardioMode == m, onClick = { Store.updateProfile { it.copy(cardioMode = m) } }, label = { Text(t) })
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Сегодня: ${day.cardioKcal} / ${plan.cardioTarget} ккал", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (left <= 0) {
                    Text("Кардио на сегодня сделано ✅", color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("${session.seconds / 60} мин · ~${session.kcal} ккал · ${session.patternName}", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (left > 0) {
            Spacer(Modifier.height(12.dp))
            // Сжатый план: одинаковые подряд интервалы склеиваем
            val runs = session.intervals.count { it.pace == Pace.RUN }
            val first = session.intervals.first()
            val run = session.intervals.firstOrNull { it.pace == Pace.RUN }
            val walk = session.intervals.firstOrNull { it.pace == Pace.WALK }
            val last = session.intervals.last()
            PlanLine("Разминка", "${first.seconds / 60} мин", first.setting, LightGreen)
            if (run != null && walk != null) {
                PlanLine("Бег × $runs", "", run.setting, WarnBg)
                PlanLine("Шаг между ними", "", walk.setting, SkyBlue)
            }
            PlanLine("Заминка", "${last.seconds / 60} мин", last.setting, LightGreen)
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart, enabled = left > 0, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Начать кардио", fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PlanLine(title: String, time: String, setting: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).background(color, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(setting)
        if (time.isNotEmpty()) { Spacer(Modifier.width(8.dp)); Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
