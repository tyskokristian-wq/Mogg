package com.mogg.app.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.mogg.app.data.FaceMetrics
import com.mogg.app.data.Flaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class FaceResult(
    val bitmap: Bitmap,
    /** Все точки лица — рисуются поверх фото. */
    val contours: Map<Int, List<PointF>>,
    val midlineTop: PointF,
    val midlineBottom: PointF,
    val canthalLines: List<Pair<PointF, PointF>>,
    val metrics: FaceMetrics,
    val flaws: Map<Flaw, Int>,
    val notes: List<Pair<String, Boolean>>, // текст, всё ли ок
)

sealed class FaceError(val message: String) {
    object NoFace : FaceError("Лицо не найдено. Сфоткайся анфас, при хорошем свете, без чёлки на глазах.")
    object ManyFaces : FaceError("На фото больше одного лица.")
    object Turned : FaceError("Голова повёрнута. Смотри прямо в камеру, телефон на уровне глаз.")
    class Other(msg: String) : FaceError(msg)
}

object FaceAnalyzer {

    suspend fun loadBitmap(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val max = maxOf(info.size.width, info.size.height)
                if (max > 1600) {
                    val k = 1600f / max
                    decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                }
            }
        } else {
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it)!! }
        }
    }

    suspend fun analyze(bitmap: Bitmap): Result<FaceResult> {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
        val detector = FaceDetection.getClient(options)
        val faces: List<Face> = try {
            suspendCancellableCoroutine { cont ->
                detector.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(emptyList()) }
            }
        } finally {
            detector.close()
        }
        if (faces.isEmpty()) return Result.failure(FaceException(FaceError.NoFace))
        val face = faces.maxBy { it.boundingBox.width() * it.boundingBox.height() }
        if (abs(face.headEulerAngleY) > 12) return Result.failure(FaceException(FaceError.Turned))
        return try {
            Result.success(compute(bitmap, face))
        } catch (e: Exception) {
            Result.failure(FaceException(FaceError.Other("Не получилось разметить лицо: ${e.message}")))
        }
    }

    class FaceException(val error: FaceError) : Exception(error.message)

    private fun pts(face: Face, type: Int): List<PointF> = face.getContour(type)?.points ?: emptyList()

    private fun compute(bitmap: Bitmap, face: Face): FaceResult {
        val types = listOf(
            FaceContour.FACE, FaceContour.LEFT_EYEBROW_TOP, FaceContour.LEFT_EYEBROW_BOTTOM,
            FaceContour.RIGHT_EYEBROW_TOP, FaceContour.RIGHT_EYEBROW_BOTTOM, FaceContour.LEFT_EYE,
            FaceContour.RIGHT_EYE, FaceContour.UPPER_LIP_TOP, FaceContour.UPPER_LIP_BOTTOM,
            FaceContour.LOWER_LIP_TOP, FaceContour.LOWER_LIP_BOTTOM, FaceContour.NOSE_BRIDGE,
            FaceContour.NOSE_BOTTOM, FaceContour.LEFT_CHEEK, FaceContour.RIGHT_CHEEK
        )
        val contours = types.associateWith { pts(face, it) }.filterValues { it.isNotEmpty() }

        val faceOval = contours[FaceContour.FACE] ?: error("нет контура лица")
        val eyeA = contours[FaceContour.LEFT_EYE] ?: error("нет глаз")
        val eyeB = contours[FaceContour.RIGHT_EYE] ?: error("нет глаз")
        val noseBridge = contours[FaceContour.NOSE_BRIDGE] ?: error("нет носа")
        val noseBottom = contours[FaceContour.NOSE_BOTTOM] ?: error("нет носа")

        // 1. Выравниваем наклон головы по линии глаз.
        val cA = center(eyeA); val cB = center(eyeB)
        val roll = atan2((cB.y - cA.y).toDouble(), (cB.x - cA.x).toDouble())
        val pivot = PointF((cA.x + cB.x) / 2, (cA.y + cB.y) / 2)
        fun n(p: PointF): PointF {
            val dx = p.x - pivot.x; val dy = p.y - pivot.y
            val c = cos(-roll); val s = sin(-roll)
            return PointF((dx * c - dy * s).toFloat(), (dx * s + dy * c).toFloat())
        }
        val oval = faceOval.map(::n)
        // «левый» и «правый» — как на фото
        val leftEye = listOf(eyeA.map(::n), eyeB.map(::n)).minBy { center(it).x }
        val rightEye = listOf(eyeA.map(::n), eyeB.map(::n)).maxBy { center(it).x }

        val browsAll = listOfNotNull(contours[FaceContour.LEFT_EYEBROW_TOP], contours[FaceContour.RIGHT_EYEBROW_TOP]).map { it.map(::n) }
        val leftBrow = browsAll.minByOrNull { center(it).x }
        val rightBrow = browsAll.maxByOrNull { center(it).x }

        // 2. Средняя линия: переносица + низ носа.
        val nb = noseBottom.map(::n)
        val midX = (noseBridge.map(::n).map { it.x }.average() + nb[nb.size / 2].x) / 2

        val minX = oval.minOf { it.x }; val maxX = oval.maxOf { it.x }
        val minY = oval.minOf { it.y }; val maxY = oval.maxOf { it.y }
        val width = (maxX - minX).toDouble()
        val height = (maxY - minY).toDouble()

        // 3. Симметрия контура: точки FACE идут по кругу (0 — верх, 18 — подбородок).
        val count = oval.size
        var err = 0.0; var pairs = 0
        for (i in 1 until count / 2) {
            val a = oval[i]; val b = oval[count - i]
            val da = abs(a.x - midX); val db = abs(b.x - midX)
            err += abs(da - db) + abs(a.y - b.y) * 0.5
            pairs++
        }
        // глаза и брови относительно средней линии
        val cl = center(leftEye); val cr = center(rightEye)
        err += abs(abs(cl.x - midX) - abs(cr.x - midX)) + abs(cl.y - cr.y); pairs += 1
        val asymPct = if (pairs == 0) 0.0 else err / pairs / width * 100
        val symmetryScore = (100 - asymPct * 8).coerceIn(0.0, 100.0).roundToInt()

        // 4. Кантальный наклон: угол от внутреннего к внешнему уголку (плюс — внешний выше).
        fun tilt(eye: List<PointF>, isLeftOnPhoto: Boolean): Pair<Double, Pair<PointF, PointF>> {
            val inner = if (isLeftOnPhoto) eye.maxBy { it.x } else eye.minBy { it.x }
            val outer = if (isLeftOnPhoto) eye.minBy { it.x } else eye.maxBy { it.x }
            val deg = Math.toDegrees(atan2((inner.y - outer.y).toDouble(), abs(outer.x - inner.x).toDouble()))
            return deg to (inner to outer)
        }
        val (tL, lineL) = tilt(leftEye, true)
        val (tR, lineR) = tilt(rightEye, false)

        // 5. Открытость глаз (ленивый глаз).
        fun openness(eye: List<PointF>) = (eye.maxOf { it.y } - eye.minOf { it.y }).toDouble() / (eye.maxOf { it.x } - eye.minOf { it.x })
        val oL = openness(leftEye); val oR = openness(rightEye)
        val openDiff = abs(oL - oR) / maxOf(oL, oR) * 100

        // 6. Брови: высота над глазом.
        val browDiff = if (leftBrow != null && rightBrow != null) {
            val hL = cl.y - center(leftBrow).y; val hR = cr.y - center(rightBrow).y
            abs(hL - hR) / maxOf(abs(hL), abs(hR), 1f) * 100.0
        } else 0.0

        // 7. Пропорции.
        val cheekWidth = width
        val jawY = minY + height * 0.78
        val jawPts = oval.filter { abs(it.y - jawY) < height * 0.08 }
        val jawWidth = if (jawPts.size >= 2) (jawPts.maxOf { it.x } - jawPts.minOf { it.x }).toDouble() else cheekWidth * 0.85
        val jawToCheek = jawWidth / cheekWidth
        val browY = browsAll.flatten().map { it.y }.average().takeIf { !it.isNaN() } ?: (cl.y - height * 0.08)
        val noseY = nb.maxOf { it.y }.toDouble()
        val upperLip = contours[FaceContour.UPPER_LIP_TOP]?.map(::n)?.minOf { it.y }?.toDouble() ?: (noseY + height * 0.05)
        val lowerThird = (maxY - noseY) / (noseY - browY)
        val fwhr = cheekWidth / (upperLip - browY)
        val hw = height / width

        val m = FaceMetrics(
            symmetryScore = symmetryScore,
            asymmetryPct = asymPct,
            canthalTiltLeft = tL,
            canthalTiltRight = tR,
            eyeOpennessDiffPct = openDiff,
            browDiffPct = browDiff,
            faceHeightToWidth = hw,
            jawToCheek = jawToCheek,
            lowerThirdRatio = lowerThird,
            fwhr = fwhr,
            analyzedAt = System.currentTimeMillis(),
        )

        val (flaws, notes) = interpret(m)

        // Переводим линии обратно в координаты фото.
        fun back(p: PointF): PointF {
            val c = cos(roll); val s = sin(roll)
            return PointF((p.x * c - p.y * s + pivot.x).toFloat(), (p.x * s + p.y * c + pivot.y).toFloat())
        }
        return FaceResult(
            bitmap = bitmap,
            contours = contours,
            midlineTop = back(PointF(midX.toFloat(), minY - 20)),
            midlineBottom = back(PointF(midX.toFloat(), maxY + 20)),
            canthalLines = listOf(back(lineL.first) to back(lineL.second), back(lineR.first) to back(lineR.second)),
            metrics = m,
            flaws = flaws,
            notes = notes,
        )
    }

    /** Переводим цифры в недостатки и понятные выводы. */
    fun interpret(m: FaceMetrics): Pair<Map<Flaw, Int>, List<Pair<String, Boolean>>> {
        val flaws = mutableMapOf<Flaw, Int>()
        val notes = mutableListOf<Pair<String, Boolean>>()

        when {
            m.asymmetryPct < 2.0 -> notes += "Симметрия ${m.symmetryScore}/100 — отлично" to true
            m.asymmetryPct < 3.5 -> { flaws[Flaw.ASYMMETRY] = 1; notes += "Симметрия ${m.symmetryScore}/100 — лёгкая асимметрия" to false }
            m.asymmetryPct < 5.5 -> { flaws[Flaw.ASYMMETRY] = 2; notes += "Симметрия ${m.symmetryScore}/100 — заметная асимметрия" to false }
            else -> { flaws[Flaw.ASYMMETRY] = 3; notes += "Симметрия ${m.symmetryScore}/100 — сильная асимметрия" to false }
        }

        val tilt = (m.canthalTiltLeft + m.canthalTiltRight) / 2
        val tiltStr = "%+.1f°".format(tilt)
        when {
            tilt >= 3 -> notes += "Кантальный наклон $tiltStr — положительный, хищный взгляд" to true
            tilt >= 1 -> { flaws[Flaw.EYES_TIRED] = 1; notes += "Кантальный наклон $tiltStr — нейтральный (идеал +3…+7°)" to false }
            tilt >= -1 -> { flaws[Flaw.EYES_TIRED] = 2; notes += "Кантальный наклон $tiltStr — нейтральный/слабый, взгляд уставший" to false }
            else -> { flaws[Flaw.EYES_TIRED] = 3; notes += "Кантальный наклон $tiltStr — отрицательный" to false }
        }

        when {
            m.eyeOpennessDiffPct < 12 -> notes += "Глаза открыты одинаково" to true
            m.eyeOpennessDiffPct < 22 -> { flaws[Flaw.LAZY_EYE] = 2; notes += "Один глаз открыт меньше на ${m.eyeOpennessDiffPct.roundToInt()}% — ленивый глаз" to false }
            else -> { flaws[Flaw.LAZY_EYE] = 3; notes += "Один глаз открыт меньше на ${m.eyeOpennessDiffPct.roundToInt()}% — ленивый глаз" to false }
        }

        if (m.browDiffPct > 12) {
            flaws[Flaw.BROWS] = 1
            flaws[Flaw.ASYMMETRY] = maxOf(flaws[Flaw.ASYMMETRY] ?: 0, 1)
            notes += "Брови на разной высоте (${m.browDiffPct.roundToInt()}%)" to false
        } else notes += "Брови на одной высоте" to true

        if (m.jawToCheek < 0.80 || m.lowerThirdRatio < 0.9) {
            flaws[Flaw.WEAK_JAW] = if (m.jawToCheek < 0.74) 3 else 2
            notes += "Челюсть уже скул (${"%.2f".format(m.jawToCheek)}) — контур челюсти можно усилить" to false
        } else notes += "Ширина челюсти к скулам ${"%.2f".format(m.jawToCheek)} — сильная нижняя треть" to true

        if (m.faceHeightToWidth < 1.22) {
            flaws[Flaw.CHEEKS] = 2
            flaws[Flaw.PUFFINESS] = 1
            notes += "Лицо круглое (${"%.2f".format(m.faceHeightToWidth)}) — уйдёт с отёками и % жира" to false
        } else notes += "Пропорции лица ${"%.2f".format(m.faceHeightToWidth)} — вытянутое, хорошо" to true

        notes += "FWHR ${"%.2f".format(m.fwhr)} (ширина к высоте средней трети; мужской идеал ≈ 1.9–2.0)" to (m.fwhr >= 1.8)
        return flaws to notes
    }

    private fun center(p: List<PointF>) = PointF(p.map { it.x }.average().toFloat(), p.map { it.y }.average().toFloat())
}
