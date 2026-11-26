package com.mogg.app.cardio

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogg.app.data.ExerciseLibrary
import com.mogg.app.data.Nutrition
import com.mogg.app.data.Store
import com.mogg.app.health.HealthSync
import com.mogg.app.ui.TopVideo
import com.mogg.app.ui.TopVideoController
import com.mogg.app.workout.Narrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt

private fun paceColor(p: Pace) = when (p) {
    Pace.HARD -> Color(0xFFFF8A65)
    Pace.EASY -> Color(0xFF7CC8FF)
    else -> Color(0xFF7CE0A3)
}

/** Интервальное кардио: видео на весь экран, в углу — что делать сейчас и таймер. */
@Composable
fun CardioRunScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val start = remember { Store.state.value }
    val p = start.profile
    val day = start.days[Store.today()] ?: Store.day()
    val session = remember {
        val plan = Nutrition.plan(p, day.eatenTotal)
        IntervalPlanner.build(p, (plan.cardioTarget - day.cardioKcal).coerceAtLeast(0))
    }
    val lib = remember { ExerciseLibrary.get(context) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val old = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            activity?.requestedOrientation = old ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val top = remember { TopVideoController().apply { normalVolume = p.videoVolume; duckVolume = p.videoDuckVolume } }
    val narrator = remember { Narrator(context) { top.duck(it) } }
    DisposableEffect(Unit) { onDispose { narrator.release() } }

    var index by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(session.intervals.first().seconds) }
    var elapsed by remember { mutableIntStateOf(0) }
    var burned by remember { mutableDoubleStateOf(0.0) }
    var paused by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val startedAt = remember { Instant.now() }

    fun announce(i: Int) {
        val iv = session.intervals[i]
        val noRun = p.cardioMode == com.mogg.app.data.CardioMode.BIKE || p.cardioMode == com.mogg.app.data.CardioMode.ELLIPTICAL
        val walkOnly = session.intervals.none { it.pace == Pace.HARD }
        val key = when (iv.pace) {
            Pace.WARMUP -> "cardio_start"
            Pace.HARD -> if (noRun) "cardio_fast" else "cardio_run"
            Pace.EASY -> if (noRun) "cardio_easy" else if (walkOnly) "cardio_incline" else "cardio_walk"
            Pace.COOLDOWN -> "cardio_cooldown"
        }
        val text = lib.phrases[key] ?: iv.label
        narrator.speak(key, text)
    }

    fun save() {
        if (saved) return
        saved = true
        val kcal = burned.roundToInt()
        if (elapsed >= 30) {
            Store.updateDay {
                it.copy(
                    cardioKcal = it.cardioKcal + kcal,
                    cardioMinutes = it.cardioMinutes + (elapsed / 60.0).roundToInt(),
                    cardioSessions = it.cardioSessions + 1,
                )
            }
            if (p.useHealthConnect) scope.launch { HealthSync.writeCardio(context, p.cardioMode, startedAt, Instant.now(), kcal) }
        }
    }

    fun next() {
        if (index + 1 >= session.intervals.size) {
            narrator.speak("cardio_done", lib.phrases["cardio_done"] ?: "Кардио завершено.")
            save()
            index = session.intervals.size
            return
        }
        index++
        remaining = session.intervals[index].seconds
        announce(index)
    }

    LaunchedEffect(Unit) { announce(0) }
    LaunchedEffect(paused, index) {
        if (index >= session.intervals.size) return@LaunchedEffect
        while (!paused && remaining > 0) {
            delay(1000)
            remaining--
            elapsed++
            burned += session.intervals[index].kcalPerMin / 60
            if (remaining == 3) narrator.speak("beep", "3, 2, 1")
        }
        if (!paused && remaining <= 0) next()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        TopVideo(top, p.videoOverride, Modifier.fillMaxSize())

        val done = index >= session.intervals.size
        Column(
            Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max = 250.dp)
                .background(Color(0xD9000000), RoundedCornerShape(16.dp)).padding(12.dp)
        ) {
            if (done) {
                Text("ГОТОВО 🔥", color = Color(0xFF7CE0A3), fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("−${burned.roundToInt()} ккал · ${elapsed / 60} мин", color = Color.White)
                IconButton(onClick = onClose) { Icon(Icons.Default.Stop, "Выйти", tint = Color.White) }
            } else {
                val iv = session.intervals[index]
                Text(iv.label, color = paceColor(iv.pace), fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(iv.setting, color = Color.White, fontSize = 14.sp)
                Text("%d:%02d".format(remaining / 60, remaining % 60), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { 1f - remaining.toFloat() / iv.seconds },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = paceColor(iv.pace),
                )
                Spacer(Modifier.height(6.dp))
                session.intervals.getOrNull(index + 1)?.let {
                    Text("Дальше: ${it.label.lowercase()} ${it.seconds / 60}:%02d".format(it.seconds % 60), color = Color.White.copy(0.7f), fontSize = 12.sp)
                }
                val total = session.seconds
                Text(
                    "🔥 ${burned.roundToInt()} / ${session.kcal} ккал · ${(total - elapsed) / 60} мин осталось",
                    color = Color.White, fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { paused = !paused; if (paused) top.pause() else top.play() }) {
                        Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, "Пауза", tint = Color.White)
                    }
                    IconButton(onClick = { next() }) { Icon(Icons.Default.SkipNext, "Следующий интервал", tint = Color.White) }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { save(); onClose() }) { Icon(Icons.Default.Stop, "Закончить", tint = Color.White) }
                }
            }
        }
    }
}
