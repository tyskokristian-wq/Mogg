package com.mogg.app.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogg.app.data.Activity
import com.mogg.app.data.Profile
import com.mogg.app.data.Sex
import com.mogg.app.data.Store
import com.mogg.app.notify.Reminders
import com.mogg.app.ui.MrBeastFeed
import com.mogg.app.ui.theme.Accent
import kotlin.math.roundToInt

/** Поля профиля — общие для онбординга и настроек. */
@Composable
fun ProfileFields(p: Profile, onChange: (Profile) -> Unit) {
    var age by remember { mutableStateOf(p.age.toString()) }
    var height by remember { mutableStateOf(p.heightCm.toString()) }
    var weight by remember { mutableStateOf(p.weightKg.toString()) }

    Text("Пол", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.entries.forEach { s -> FilterChip(selected = p.sex == s, onClick = { onChange(p.copy(sex = s)) }, label = { Text(s.title) }) }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(age, { v -> age = v.filter(Char::isDigit).take(3); age.toIntOrNull()?.let { if (it in 10..100) onChange(p.copy(age = it)) } },
            label = { Text("Возраст") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        OutlinedTextField(height, { v -> height = v.filter(Char::isDigit).take(3); height.toIntOrNull()?.let { if (it in 120..230) onChange(p.copy(heightCm = it)) } },
            label = { Text("Рост, см") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        OutlinedTextField(weight, { v -> weight = v.replace(',', '.').filter { it.isDigit() || it == '.' }.take(5); weight.toDoubleOrNull()?.let { if (it in 30.0..300.0) onChange(p.copy(weightKg = it)) } },
            label = { Text("Вес, кг") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Text("Активность (без кардио из приложения)", fontWeight = FontWeight.SemiBold)
    Activity.entries.forEach { a ->
        FilterChip(selected = p.activity == a, onClick = { onChange(p.copy(activity = a)) }, label = { Text(a.title) })
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by Store.state.collectAsState()
    val p = state.profile
    var link by remember { mutableStateOf(p.videoOverride) }

    fun pickTime(h: Int, m: Int, set: (Int, Int) -> Unit) {
        TimePickerDialog(context, { _, hh, mm -> set(hh, mm); Reminders.scheduleAll(context) }, h, m, true).show()
    }

    Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("Настройки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        ProfileFields(p) { np -> Store.updateProfile { np } }

        Spacer(Modifier.height(16.dp))
        Text("Напоминания", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Уведомления", Modifier.weight(1f))
            Switch(p.remindersOn, { v -> Store.updateProfile { it.copy(remindersOn = v) }; Reminders.scheduleAll(context) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickTime(p.morningHour, p.morningMinute) { h, m -> Store.updateProfile { it.copy(morningHour = h, morningMinute = m) } } }) {
                Text("Утро %02d:%02d".format(p.morningHour, p.morningMinute))
            }
            OutlinedButton(onClick = { pickTime(p.eveningHour, p.eveningMinute) { h, m -> Store.updateProfile { it.copy(eveningHour = h, eveningMinute = m) } } }) {
                Text("Вечер %02d:%02d".format(p.eveningHour, p.eveningMinute))
            }
        }
        Text("Виджет: зажми рабочий стол → Виджеты → Mogg.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))
        Text("Видео поверх упражнений", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("По умолчанию — самое последнее видео MrBeast. Можно вставить свою ссылку на YouTube.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(link, { link = it }, label = { Text("Ссылка YouTube (пусто = MrBeast)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { Store.updateProfile { it.copy(videoOverride = link.trim()) } }, enabled = link.isBlank() || MrBeastFeed.parseId(link) != null) { Text("Сохранить") }
            OutlinedButton(onClick = { link = ""; Store.updateProfile { it.copy(videoOverride = "") } }) { Text("Вернуть MrBeast") }
        }
        Text("Громкость видео: ${p.videoVolume}%")
        Slider(p.videoVolume.toFloat(), { v -> Store.updateProfile { it.copy(videoVolume = v.roundToInt()) } }, valueRange = 0f..100f)
        Text("Громкость, пока говорит голос: ${p.videoDuckVolume}%")
        Slider(p.videoDuckVolume.toFloat(), { v -> Store.updateProfile { it.copy(videoDuckVolume = v.roundToInt()) } }, valueRange = 0f..50f)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(Store.state.value.profile) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Store.updateProfile { draft.copy(onboarded = true) }
        Reminders.scheduleAll(context)
    }

    Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("MOGG", color = Accent, fontSize = 44.sp, fontWeight = FontWeight.Black)
        Text("Асценд по плану: лицо утром 10 минут, вечером 20, кардио и питание.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        ProfileFields(draft) { draft = it }
        Spacer(Modifier.height(12.dp))
        Text("Сколько хочешь есть в день: ${draft.intakeGoal} ккал", fontWeight = FontWeight.SemiBold)
        Slider(draft.intakeGoal.toFloat(), { v -> draft = draft.copy(intakeGoal = (v / 50).roundToInt() * 50) }, valueRange = 1200f..3500f)
        Text("Кардио подстроится, чтобы сушка шла, но без вреда: приложение не даст уйти в опасный дефицит.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            else { Store.updateProfile { draft.copy(onboarded = true) }; Reminders.scheduleAll(context) }
        }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Поехали", fontSize = 18.sp) }
        Spacer(Modifier.height(8.dp))
        Text("Дальше — вкладка «Лицо»: фото + анкета, и программа соберётся под твои недостатки.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
