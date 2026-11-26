package com.mogg.app.workout

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mogg.app.data.Exercise
import com.mogg.app.data.ExerciseLibrary
import com.mogg.app.data.Slot
import com.mogg.app.data.Store
import com.mogg.app.ui.TopVideo
import com.mogg.app.ui.TopVideoController
import com.mogg.app.ui.theme.Accent
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun WorkoutScreen(slot: Slot, onClose: () -> Unit) {
    val context = LocalContext.current
    val state by Store.state.collectAsState()
    val profile = state.profile
    val lib = remember { ExerciseLibrary.get(context) }
    val steps = remember { RoutineBuilder.build(lib.exercises, profile, slot) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val top = remember { TopVideoController().apply { normalVolume = profile.videoVolume; duckVolume = profile.videoDuckVolume } }
    val narrator = remember { Narrator(context) { speaking -> top.duck(speaking) } }

    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            narrator.release()
            exo.release()
        }
    }

    var index by remember { mutableIntStateOf(-1) }   // -1 = экран старта
    var remaining by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }

    fun exerciseFor(i: Int): Exercise? = when (val s = steps.getOrNull(i)) {
        is WorkoutStep.Do -> s.exercise
        is WorkoutStep.Rest -> s.next
        null -> null
    }

    fun loadClip(e: Exercise?) {
        if (e?.video == null) { exo.stop(); exo.clearMediaItems(); return }
        val item = MediaItem.Builder()
            .setUri("asset:///videos/${e.video}")
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(e.clipStart * 1000L)
                    .setEndPositionMs(e.clipEnd * 1000L)
                    .build()
            ).build()
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun goTo(i: Int) {
        if (i >= steps.size) {
            finished = true
            exo.stop()
            narrator.speak("done", lib.phrases["done"] ?: "Готово")
            Store.updateDay { if (slot == Slot.MORNING) it.copy(morningDone = true) else it.copy(eveningDone = true) }
            return
        }
        index = i.coerceAtLeast(0)
        val step = steps[index]
        remaining = step.seconds
        showSteps = false
        when (step) {
            is WorkoutStep.Do -> {
                loadClip(step.exercise)
                narrator.speak(step.exercise.id, step.exercise.voice)
            }
            is WorkoutStep.Rest -> {
                loadClip(step.next)
                narrator.speak("rest", lib.phrases["rest"] ?: "Отдых")
            }
        }
    }

    // Таймер
    LaunchedEffect(index, paused, finished) {
        if (index < 0 || paused || finished) return@LaunchedEffect
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
        goTo(index + 1)
    }

    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        // ВЕРХ: последнее видео MrBeast
        TopVideo(top, profile.videoOverride, Modifier.fillMaxWidth().aspectRatio(16f / 9f))

        // НИЗ: как делать упражнение
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val current = exerciseFor(index)
            if (current?.video != null && !finished) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = exo
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (current != null && !finished) {
                StepsCard(current, Modifier.fillMaxSize().padding(16.dp))
            }

            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Close, "Закрыть", tint = Color.White)
            }

            when {
                index < 0 -> StartPanel(slot, steps, onStart = {
                    narrator.speak(if (slot == Slot.MORNING) "start_morning" else "start_evening",
                        lib.phrases[if (slot == Slot.MORNING) "start_morning" else "start_evening"] ?: "Поехали")
                    goTo(0)
                }, Modifier.align(Alignment.Center))
                finished -> FinishedPanel(slot, onClose, Modifier.align(Alignment.Center))
                else -> {
                    val step = steps[index]
                    val doCount = steps.count { it is WorkoutStep.Do }
                    val doIndex = steps.take(index + 1).count { it is WorkoutStep.Do }
                    BottomPanel(
                        step = step,
                        number = "$doIndex / $doCount",
                        remaining = remaining,
                        paused = paused,
                        showSteps = showSteps,
                        onToggleSteps = { showSteps = !showSteps },
                        onPause = {
                            paused = !paused
                            if (paused) { exo.pause(); top.pause(); narrator.stop() } else { exo.play(); top.play() }
                        },
                        onPrev = { goTo((index - 2).coerceAtLeast(0)) },
                        onNext = { goTo(index + 1) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepsCard(e: Exercise, modifier: Modifier) {
    Column(
        modifier.background(Color(0xFF15151C), RoundedCornerShape(20.dp)).padding(20.dp).verticalScroll(rememberScrollState())
    ) {
        Text(e.title, color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        e.steps.forEachIndexed { i, s ->
            Text("${i + 1}. $s", color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun StartPanel(slot: Slot, steps: List<WorkoutStep>, onStart: () -> Unit, modifier: Modifier) {
    val total = RoutineBuilder.totalSeconds(steps)
    Column(
        modifier.padding(24.dp).background(Color(0xEE111118), RoundedCornerShape(24.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (slot == Slot.MORNING) "Утро" else "Вечер", color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("${steps.count { it is WorkoutStep.Do }} упражнений · ${total / 60} мин ${total % 60} с", color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Между упражнениями 10 секунд, чтобы переставить пальцы.", color = Color.White.copy(0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart) { Text("Начать", fontSize = 18.sp) }
    }
}

@Composable
private fun FinishedPanel(slot: Slot, onClose: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(24.dp).background(Color(0xEE111118), RoundedCornerShape(24.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Готово 🔥", color = Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(if (slot == Slot.MORNING) "Утренняя тренировка засчитана" else "Вечерняя тренировка засчитана", color = Color.White)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose) { Text("На главную") }
    }
}

@Composable
private fun BottomPanel(
    step: WorkoutStep,
    number: String,
    remaining: Int,
    paused: Boolean,
    showSteps: Boolean,
    onToggleSteps: () -> Unit,
    onPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    val isRest = step is WorkoutStep.Rest
    val ex = when (step) { is WorkoutStep.Do -> step.exercise; is WorkoutStep.Rest -> step.next }
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000), Color.Black)))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (showSteps && ex.steps.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Color(0xCC15151C), RoundedCornerShape(16.dp)).padding(12.dp)) {
                ex.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", color = Color.White, fontSize = 15.sp) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isRest) "ОТДЫХ · переставь пальцы" else number,
                    color = if (isRest) Color(0xFF7CFFB2) else Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
                Text(if (isRest) "Дальше: ${ex.title}" else ex.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onToggleSteps, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(if (showSteps) "Скрыть технику" else "Как делать ▸", color = Color.White.copy(0.7f))
                }
            }
            Text(
                "%d:%02d".format(remaining / 60, remaining % 60),
                color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { if (step.seconds == 0) 0f else 1f - remaining.toFloat() / step.seconds },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (isRest) Color(0xFF7CFFB2) else Accent,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Default.SkipPrevious, "Назад", tint = Color.White) }
            FilledIconButton(onClick = onPause, modifier = Modifier.size(60.dp)) {
                Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, "Пауза")
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Дальше", tint = Color.White) }
        }
    }
}
