package com.mogg.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogg.app.data.ExerciseLibrary
import com.mogg.app.data.Flaw
import com.mogg.app.data.Nutrition
import com.mogg.app.data.Slot
import com.mogg.app.data.Store
import com.mogg.app.ui.theme.Accent
import com.mogg.app.ui.theme.Good
import com.mogg.app.workout.RoutineBuilder
import com.mogg.app.workout.WorkoutStep

@Composable
fun HomeScreen(onWorkout: (Slot) -> Unit, onCardio: () -> Unit, onFood: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val state by Store.state.collectAsState()
    val p = state.profile
    val day = state.days[Store.today()] ?: Store.day()
    val plan = Nutrition.plan(p, day.eatenTotal)
    val lib = remember { ExerciseLibrary.get(context) }
    val morning = remember(p.flaws) { RoutineBuilder.build(lib.exercises, p, Slot.MORNING) }
    val evening = remember(p.flaws) { RoutineBuilder.build(lib.exercises, p, Slot.EVENING) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mogg", color = Accent, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text("Серия: ${Store.streak(state)} дн. 🔥", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Настройки") }
        }
        Spacer(Modifier.height(12.dp))

        WorkoutCard("☀️ Утро", morning, day.morningDone) { onWorkout(Slot.MORNING) }
        Spacer(Modifier.height(10.dp))
        WorkoutCard("🌙 Вечер", evening, day.eveningDone) { onWorkout(Slot.EVENING) }
        Spacer(Modifier.height(10.dp))

        ProgressCard(
            title = "🔥 Кардио",
            value = day.cardioKcal, target = plan.cardioTarget, unit = "ккал",
            subtitle = if (plan.cardioTarget == 0) "Сегодня только для здоровья — по желанию" else "Цель на сегодня",
            onClick = onCardio,
        )
        Spacer(Modifier.height(10.dp))
        ProgressCard(
            title = "🍽 Еда",
            value = day.eatenTotal, target = plan.intake, unit = "ккал",
            subtitle = "Чистыми (еда − кардио): ${day.eatenTotal - day.cardioKcal} ккал",
            onClick = onFood,
        )
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("💧 Вода: ${day.waterMl} / ${(p.weightKg * 35).toInt()} мл", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(250, 500).forEach { ml ->
                        OutlinedButton(onClick = { Store.updateDay { it.copy(waterMl = it.waterMl + ml) } }) { Text("+$ml") }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val flaws = Flaw.entries.filter { p.flawLevel(it) > 0 }.sortedByDescending { p.flawLevel(it) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Программа под тебя", fontWeight = FontWeight.Bold)
                if (flaws.isEmpty()) Text("Сделай анализ лица во вкладке «Лицо» — пока программа общая.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                flaws.forEach { Text("• ${it.title} " + "●".repeat(p.flawLevel(it)), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(6.dp))
                Text("Каждый день: мьюинг (язык к нёбу), жёсткая еда задними зубами, сон 8 часов, на спине.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WorkoutCard(title: String, steps: List<WorkoutStep>, done: Boolean, onClick: () -> Unit) {
    val total = RoutineBuilder.totalSeconds(steps)
    val names = steps.filterIsInstance<WorkoutStep.Do>().map { it.exercise.title }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (done) Color(0xFF14261C) else MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (done) "✅ сделано" else "${total / 60} мин ▸", color = if (done) Good else Accent, fontWeight = FontWeight.SemiBold)
            }
            Text(
                names.take(4).joinToString(" · ") + if (names.size > 4) " и ещё ${names.size - 4}" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ProgressCard(title: String, value: Int, target: Int, unit: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("$value / $target $unit")
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (target <= 0) 1f else (value.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
