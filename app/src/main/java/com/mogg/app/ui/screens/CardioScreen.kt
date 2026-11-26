package com.mogg.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogg.app.cardio.IntervalPlanner
import com.mogg.app.cardio.Pace
import com.mogg.app.data.CardioMode
import com.mogg.app.data.Nutrition
import com.mogg.app.data.Store
import com.mogg.app.ui.YesSign
import com.mogg.app.ui.theme.Accent
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
    val options = IntervalPlanner.options(p, left)
    val selected = IntervalPlanner.selectedId(p, left)
    val session = IntervalPlanner.build(p, left)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Кардио", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            YesSign(100.dp, -10f)
        }

        Text("Сколько сжечь", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(500, 600).forEach { g ->
                FilterChip(selected = p.cardioGoal == g, onClick = { Store.updateProfile { it.copy(cardioGoal = g) } }, label = { Text("$g ккал") })
            }
        }

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

        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Сегодня: ${day.cardioKcal} / ${plan.cardioTarget} ккал", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (left <= 0) Text("Кардио на сегодня сделано ✅", color = MaterialTheme.colorScheme.primary)
                else Text("Осталось $left ккал. Выбери вариант — время подстроится само.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (left > 0) {
            Spacer(Modifier.height(12.dp))
            Text("Вариант (сверху — быстрее и тяжелее)", fontWeight = FontWeight.SemiBold)
            options.forEach { o ->
                val sel = o.id == selected
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .background(if (sel) LightGreen else Color(0xFFF4F7F5), RoundedCornerShape(14.dp))
                        .border(if (sel) 2.dp else 0.dp, if (sel) Accent else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { Store.updateProfile { it.copy(cardioLevel = o.id) } }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(o.session.name, Modifier.weight(1f), fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    Text("${o.session.minutes} мин", fontWeight = FontWeight.Bold, color = if (o.session.minutes <= 60) Accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Что выставлять", fontWeight = FontWeight.SemiBold)
            val first = session.intervals.first()
            val hard = session.intervals.firstOrNull { it.pace == Pace.HARD }
            val easy = session.intervals.firstOrNull { it.pace == Pace.EASY }
            PlanLine("Разминка ${first.seconds / 60} мин", first.setting, LightGreen)
            if (hard != null) PlanLine("${hard.label.lowercase().replaceFirstChar { it.uppercase() }} × ${session.intervals.count { it.pace == Pace.HARD }}", hard.setting, WarnBg)
            if (easy != null) PlanLine(easy.label.lowercase().replaceFirstChar { it.uppercase() }, easy.setting, SkyBlue)
            PlanLine("Заминка ${session.intervals.last().seconds / 60} мин", session.intervals.last().setting, LightGreen)
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart, enabled = left > 0, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (left > 0) "Начать · ${session.minutes} мин · ~${session.kcal} ккал" else "Готово на сегодня", fontSize = 17.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PlanLine(title: String, setting: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).background(color, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(setting)
    }
}
