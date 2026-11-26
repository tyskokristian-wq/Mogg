package com.mogg.app.cardio

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.mogg.app.data.Cardio
import com.mogg.app.data.ExerciseLibrary
import com.mogg.app.data.Nutrition
import com.mogg.app.data.Store
import com.mogg.app.health.HealthSync
import com.mogg.app.ui.TopVideo
import com.mogg.app.ui.TopVideoController
import com.mogg.app.ui.theme.Accent
import com.mogg.app.workout.Narrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt

/** Кардио: видео на весь экран, в углу — режим и таймер. */
@Composable
fun CardioRunScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Store.state.collectAsState()
    val p = state.profile
    val day = state.days[Store.today()] ?: Store.day()
    val plan = remember { Nutrition.plan(p, day.eatenTotal) }
    val left = remember { (plan.cardioTarget - day.cardioKcal).coerceAtLeast(0) }
    val totalMin = remember { Cardio.sessions(Cardio.minutesFor(left, p)).firstOrNull() ?: 20 }
    val perMin = remember { Cardio.kcalPerMinute(p) }
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

    var elapsed by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val startedAt = remember { Instant.now() }
    val totalSec = totalMin * 60

    fun finish() {
        val minutes = (elapsed / 60.0)
        val kcal = (minutes * perMin).roundToInt()
        if (elapsed >= 30) {
            Store.updateDay { it.copy(cardioKcal = it.cardioKcal + kcal, cardioMinutes = it.cardioMinutes + minutes.roundToInt()) }
            if (p.useHealthConnect) scope.launch { HealthSync.writeCardio(context, p.cardioMode, startedAt, Instant.now(), kcal) }
        }
        onClose()
    }

    LaunchedEffect(Unit) { narrator.speak("cardio_start", lib.phrases["cardio_start"] ?: "") }
    LaunchedEffect(paused) {
        while (!paused) {
            delay(1000)
            elapsed++
            if (elapsed == totalSec / 2) narrator.speak("cardio_half", lib.phrases["cardio_half"] ?: "")
            if (elapsed == totalSec) narrator.speak("cardio_done", lib.phrases["cardio_done"] ?: "")
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        TopVideo(top, p.videoOverride, Modifier.fillMaxSize())

        // Маленький уголок с упражнением и таймером
        Column(
            Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max = 230.dp)
                .background(Color(0xCC000000), RoundedCornerShape(14.dp)).padding(10.dp)
        ) {
            Text(p.cardioMode.title, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (p.cardioMode == com.mogg.app.data.CardioMode.TREADMILL)
                Text("${"%.1f".format(p.treadmillSpeed)} км/ч · ${"%.0f".format(p.treadmillIncline)}%", color = Color.White, fontSize = 12.sp)
            val remain = (totalSec - elapsed)
            Text(
                if (remain >= 0) "%d:%02d".format(remain / 60, remain % 60) else "+%d:%02d".format(-remain / 60, -remain % 60),
                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold
            )
            Text("🔥 ${(elapsed / 60.0 * perMin).roundToInt()} ккал", color = Color.White, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { paused = !paused; if (paused) top.pause() else top.play() }) {
                    Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, "Пауза", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { finish() }) { Icon(Icons.Default.Stop, "Закончить", tint = Color.White) }
            }
        }
    }
}
