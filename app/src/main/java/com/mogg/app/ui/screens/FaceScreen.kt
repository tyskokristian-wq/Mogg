package com.mogg.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mogg.app.data.Flaw
import com.mogg.app.data.Store
import com.mogg.app.face.FaceAnalyzer
import com.mogg.app.face.FaceResult
import com.mogg.app.ui.theme.Accent
import kotlinx.coroutines.launch
import java.io.File

/** Вопросы анкеты: то, что по фото анфас не определить. */
data class QuizQuestion(val text: String, val effects: Map<Flaw, Int>)

val Quiz = listOf(
    QuizQuestion("С утра лицо опухшее, контур «плывёт»?", mapOf(Flaw.PUFFINESS to 2)),
    QuizQuestion("Есть тёмные круги или мешки под глазами?", mapOf(Flaw.UNDER_EYE to 2)),
    QuizQuestion("Много сидишь за компом/телефоном, голова уходит вперёд?", mapOf(Flaw.POSTURE to 3)),
    QuizQuestion("Сбоку видно второй подбородок / мягкую зону под челюстью?", mapOf(Flaw.DOUBLE_CHIN to 2)),
    QuizQuestion("Жуёшь в основном одной стороной или челюсть щёлкает?", mapOf(Flaw.ASYMMETRY to 2)),
    QuizQuestion("Спишь на боку или на животе?", mapOf(Flaw.ASYMMETRY to 1, Flaw.PUFFINESS to 1)),
    QuizQuestion("Одно веко ниже другого?", mapOf(Flaw.LAZY_EYE to 2)),
    QuizQuestion("Брови редкие или висят над глазами?", mapOf(Flaw.BROWS to 2, Flaw.EYES_TIRED to 1)),
    QuizQuestion("Лицо кажется круглым / пухлые щёки?", mapOf(Flaw.CHEEKS to 2)),
    QuizQuestion("Хочешь поработать над формой носа?", mapOf(Flaw.NOSE to 1)),
    QuizQuestion("Взгляд кажется уставшим, веки опущены?", mapOf(Flaw.EYES_TIRED to 2)),
)

fun mergeFlaws(photo: Map<String, Int>, quiz: Map<String, Int>): Map<String, Int> =
    (photo.keys + quiz.keys).associateWith { maxOf(photo[it] ?: 0, quiz[it] ?: 0) }.filterValues { it > 0 }

@Composable
fun FaceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Store.state.collectAsState()
    val profile = state.profile

    var result by remember { mutableStateOf<FaceResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var answers by remember { mutableStateOf(List(Quiz.size) { false }) }

    fun analyze(uri: Uri) {
        busy = true; error = null
        scope.launch {
            try {
                val bmp = FaceAnalyzer.loadBitmap(context, uri)
                FaceAnalyzer.analyze(bmp)
                    .onSuccess { r ->
                        result = r
                        val photo = r.flaws.mapKeys { it.key.name }
                        Store.updateProfile { p ->
                            p.copy(face = r.metrics, photoFlaws = photo, flaws = mergeFlaws(photo, p.quizFlaws))
                        }
                    }
                    .onFailure { error = it.message }
            } catch (e: Exception) {
                error = "Не удалось открыть фото: ${e.message}"
            }
            busy = false
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::analyze) }
    val photoFile = remember { File(context.cacheDir, "face.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", photoFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) analyze(photoUri) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Анализ лица", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Фото анфас, ровный свет, лицо без эмоций, волосы убраны со лба. Приложение само расставит точки и посчитает симметрию, наклон глаз и пропорции.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { camera.launch(photoUri) }) { Text("Сфоткаться") }
            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Из галереи") }
        }
        if (busy) { Spacer(Modifier.height(16.dp)); CircularProgressIndicator() }
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }

        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            FaceOverlay(r)
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    r.notes.forEach { (text, ok) ->
                        Text((if (ok) "✅ " else "⚠️ ") + text, modifier = Modifier.padding(vertical = 3.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Оценка примерная: зависит от света, угла камеры и выражения лица. Сделай 2–3 фото и сравни.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } ?: profile.face?.let { m ->
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Прошлый анализ", fontWeight = FontWeight.Bold)
                    FaceAnalyzer.interpret(m).second.forEach { (t, ok) -> Text((if (ok) "✅ " else "⚠️ ") + t) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Анкета", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("То, что по фото не видно.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Quiz.forEachIndexed { i, q ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(q.text, Modifier.weight(1f))
                Switch(checked = answers[i], onCheckedChange = { v -> answers = answers.toMutableList().also { it[i] = v } })
            }
        }
        Button(onClick = {
            val quiz = mutableMapOf<String, Int>()
            Quiz.forEachIndexed { i, q -> if (answers[i]) q.effects.forEach { (f, lvl) -> quiz[f.name] = maxOf(quiz[f.name] ?: 0, lvl) } }
            Store.updateProfile { it.copy(quizFlaws = quiz, flaws = mergeFlaws(it.photoFlaws, quiz)) }
        }) { Text("Сохранить ответы") }

        Spacer(Modifier.height(20.dp))
        Text("Твои недостатки → программа", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Нажимай, чтобы поменять силу (0–3). Утро и вечер собираются под этот список.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Flaw.entries.forEach { f ->
            val lvl = profile.flawLevel(f)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.title, fontWeight = FontWeight.SemiBold)
                    Text(f.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = lvl > 0,
                    onClick = {
                        Store.updateProfile { p ->
                            val next = (lvl + 1) % 4
                            p.copy(flaws = if (next == 0) p.flaws - f.name else p.flaws + (f.name to next))
                        }
                    },
                    label = { Text(if (lvl == 0) "нет" else "●".repeat(lvl)) }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FaceOverlay(r: FaceResult) {
    val bmp = r.bitmap
    val image = remember(bmp) { bmp.asImageBitmap() }
    Canvas(Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height)) {
        val k = size.width / bmp.width
        drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()), dstOffset = IntOffset.Zero)
        val dot = (3.dp.toPx())
        r.contours.values.forEach { list ->
            list.forEach { p -> drawCircle(Accent, radius = dot, center = Offset(p.x * k, p.y * k)) }
        }
        drawLine(Color.Red, Offset(r.midlineTop.x * k, r.midlineTop.y * k), Offset(r.midlineBottom.x * k, r.midlineBottom.y * k), strokeWidth = 2.dp.toPx())
        r.canthalLines.forEach { (a, b) ->
            drawLine(Color.Cyan, Offset(a.x * k, a.y * k), Offset(b.x * k, b.y * k), strokeWidth = 2.dp.toPx())
        }
        drawRect(Color.White.copy(alpha = 0.2f), style = Stroke(1.dp.toPx()))
    }
}
