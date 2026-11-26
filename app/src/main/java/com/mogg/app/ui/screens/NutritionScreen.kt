package com.mogg.app.ui.screens

import com.mogg.app.ui.YesSign
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.mogg.app.data.Nutrition
import com.mogg.app.data.Store
import com.mogg.app.health.HealthSync
import com.mogg.app.ui.theme.Warn
import com.mogg.app.ui.theme.WarnBg
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutritionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Store.state.collectAsState()
    val p = state.profile
    val day = state.days[Store.today()] ?: Store.day()
    val plan = Nutrition.plan(p, day.eatenTotal)
    var add by remember { mutableStateOf("") }
    var hcStatus by remember { mutableStateOf<String?>(null) }

    suspend fun sync() {
        HealthSync.eatenToday(context)
            .onSuccess { kcal ->
                Store.updateDay { it.copy(healthConnectKcal = kcal) }
                hcStatus = "Из YAZIO / Health Connect: $kcal ккал"
            }
            .onFailure { hcStatus = "Не удалось прочитать Health Connect: ${it.message}" }
    }

    val permLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(HealthSync.permissions)) {
            Store.updateProfile { it.copy(useHealthConnect = true) }
            scope.launch { sync() }
        } else hcStatus = "Разрешения не выданы"
    }

    LaunchedEffect(p.useHealthConnect) {
        if (p.useHealthConnect && HealthSync.hasPermissions(context)) sync()
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Питание", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            YesSign(96.dp, 8f)
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Сегодня съедено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${day.eatenTotal} / ${plan.intake} ккал", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Кардио: ${day.cardioKcal} / ${plan.cardioTarget} ккал · Чистыми: ${day.eatenTotal - day.cardioKcal} ккал")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = add, onValueChange = { add = it.filter(Char::isDigit).take(5) },
                        label = { Text("+ ккал") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val v = add.toIntOrNull() ?: 0
                        if (v > 0) Store.updateDay { it.copy(eatenKcal = it.eatenKcal + v) }
                        add = ""
                    }, modifier = Modifier.padding(top = 8.dp)) { Text("Добавить") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100, 300, 500).forEach { v ->
                        OutlinedButton(onClick = { Store.updateDay { it.copy(eatenKcal = it.eatenKcal + v) } }) { Text("+$v") }
                    }
                    OutlinedButton(onClick = { Store.updateDay { it.copy(eatenKcal = 0) } }) { Text("Сброс") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("YAZIO", fontWeight = FontWeight.Bold)
                Text(
                    "YAZIO отправляет съеденное в Health Connect, а Mogg забирает оттуда. В YAZIO: Профиль → Настройки → Подключения → Health Connect → включи «Питание».",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (!HealthSync.isAvailable(context)) {
                    OutlinedButton(onClick = { context.startActivity(HealthSync.installIntent()) }) { Text("Установить Health Connect") }
                } else if (p.useHealthConnect) {
                    Button(onClick = { scope.launch { sync() } }) { Text("Обновить из YAZIO") }
                } else {
                    Button(onClick = { permLauncher.launch(HealthSync.permissions) }) { Text("Подключить YAZIO") }
                }
                hcStatus?.let { Text(it, fontSize = 13.sp) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Цель по еде: ${p.intakeGoal} ккал", fontWeight = FontWeight.Bold)
                Slider(
                    value = p.intakeGoal.toFloat(), valueRange = 1200f..3500f, steps = (3500 - 1200) / 50 - 1,
                    onValueChange = { v -> Store.updateProfile { it.copy(intakeGoal = (v / 50).roundToInt() * 50) } }
                )
                Text("Что важнее", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !p.cardioPriority, onClick = { Store.updateProfile { it.copy(cardioPriority = false) } }, label = { Text("Еда ${p.intakeGoal}") })
                    FilterChip(selected = p.cardioPriority, onClick = { Store.updateProfile { it.copy(cardioPriority = true) } }, label = { Text("Кардио 600+") })
                }
                Row { Text("Обмен в покое (BMR)", Modifier.weight(1f)); Text("${plan.bmr}") }
                Row { Text("Норма с активностью", Modifier.weight(1f)); Text("${plan.tdee}") }
                Row { Text("Кардио в день", Modifier.weight(1f)); Text("${plan.cardioTarget}") }
                Row { Text("Чистыми (еда − кардио)", Modifier.weight(1f)); Text("${plan.net}") }
                Row { Text("Дефицит", Modifier.weight(1f)); Text("${plan.deficit} (макс. ${plan.maxDeficit})") }
                Row { Text("ИМТ", Modifier.weight(1f)); Text("%.1f".format(plan.bmi)) }
                if (plan.deficit > 0) {
                    val kgWeek = plan.deficit * 7 / 7700.0
                    Text("≈ −${"%.2f".format(kgWeek)} кг жира в неделю", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }

        plan.warnings.forEach {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = WarnBg)) {
                Text("⚠️ $it", Modifier.padding(16.dp), color = Warn)
            }
        }
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                plan.tips.forEach { Text("• $it", Modifier.padding(vertical = 3.dp)) }
                Text("• Исключи газировку, пакетные соки, много мучного и молочку перед сном.", Modifier.padding(vertical = 3.dp))
                Text("• Жёсткая еда (мясо, яблоки, морковь) — жуй задними зубами, качает жевательные.", Modifier.padding(vertical = 3.dp))
            }
        }

        Spacer(Modifier.height(10.dp))
        var w by remember { mutableStateOf("") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Вес утром натощак", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = w, onValueChange = { w = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.take(5) },
                        label = { Text(day.weightKg?.let { "сегодня $it кг" } ?: "кг") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = {
                        w.toDoubleOrNull()?.takeIf { it in 30.0..300.0 }?.let { kg ->
                            Store.updateDay { it.copy(weightKg = kg) }
                            Store.updateProfile { it.copy(weightKg = kg) }
                        }
                        w = ""
                    }, modifier = Modifier.padding(top = 8.dp)) { Text("Записать") }
                }
                val history = state.days.values.filter { it.weightKg != null }.sortedBy { it.date }.takeLast(7)
                if (history.isNotEmpty()) Text(history.joinToString("  ") { "${it.date.takeLast(5)}: ${it.weightKg}" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
