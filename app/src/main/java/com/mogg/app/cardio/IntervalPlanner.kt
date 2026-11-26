package com.mogg.app.cardio

import com.mogg.app.data.CardioMode
import com.mogg.app.data.Profile
import kotlin.math.roundToInt

enum class Pace(val title: String) { WARMUP("Разминка"), WALK("Шаг"), RUN("Бег"), COOLDOWN("Заминка") }

data class Interval(
    val pace: Pace,
    val seconds: Int,
    /** Что выставить: «9 км/ч · 1%» или «быстрый шаг». */
    val setting: String,
    val kcalPerMin: Double,
) {
    val kcal: Double get() = kcalPerMin * seconds / 60
}

data class CardioSession(
    val intervals: List<Interval>,
    val number: Int,
    val totalSessions: Int,
    val patternName: String,
) {
    val seconds: Int get() = intervals.sumOf { it.seconds }
    val kcal: Int get() = intervals.sumOf { it.kcal }.roundToInt()
}

/**
 * Сам придумывает интервальное кардио «шаг — бег — шаг — бег»:
 * ты выбираешь только время, интенсивность подбирается под нужные калории.
 */
object IntervalPlanner {
    /** ACSM: ходьба / бег с наклоном, чистые ккал в минуту. */
    private fun treadmill(kg: Double, kmh: Double, inclinePct: Double): Double {
        val v = kmh * 1000 / 60
        val g = inclinePct / 100
        val vo2 = if (kmh >= 8) 0.2 * v + 0.9 * v * g else 0.1 * v + 1.8 * v * g
        return vo2 * kg / 1000 * 5
    }

    private fun met(kg: Double, met: Double) = (met - 1) * 3.5 * kg / 200

    /** Ступень интенсивности: скорость бега, наклон шага, схема. */
    private data class Level(val runKmh: Double, val walkIncline: Double, val runMet: Double, val walkMet: Double, val pattern: List<Pair<Int, Int>>, val name: String)

    private val levels = listOf(
        Level(8.0, 6.0, 7.5, 4.0, listOf(60 to 120), "1 мин бег / 2 мин шаг"),
        Level(8.5, 8.0, 8.0, 4.3, listOf(60 to 90), "1 мин бег / 1,5 мин шаг"),
        Level(8.5, 8.0, 8.0, 4.5, listOf(60 to 90, 120 to 90, 180 to 120, 120 to 90, 60 to 90), "Пирамида 1-2-3-2-1"),
        Level(9.0, 10.0, 8.5, 4.8, listOf(120 to 120), "2 мин бег / 2 мин шаг"),
        Level(9.0, 10.0, 9.0, 5.0, listOf(180 to 120), "3 мин бег / 2 мин шаг"),
        Level(9.5, 12.0, 9.5, 5.3, listOf(240 to 90), "4 мин бег / 1,5 мин шаг"),
        Level(10.0, 12.0, 10.0, 5.5, listOf(300 to 60), "5 мин бег / 1 мин шаг"),
    )

    private fun fmt(x: Double) = if (x % 1.0 == 0.0) "%.0f".format(x) else "%.1f".format(x)

    private fun intervals(p: Profile, lv: Level): Triple<Interval, Interval, Interval> /* warm, run, walk */ {
        val kg = p.weightKg
        return when (p.cardioMode) {
            CardioMode.TREADMILL -> Triple(
                Interval(Pace.WARMUP, 0, "5 км/ч · наклон 3%", treadmill(kg, 5.0, 3.0)),
                Interval(Pace.RUN, 0, "${fmt(lv.runKmh)} км/ч · наклон 1%", treadmill(kg, lv.runKmh, 1.0)),
                Interval(Pace.WALK, 0, "5,5 км/ч · наклон ${fmt(lv.walkIncline)}%", treadmill(kg, 5.5, lv.walkIncline)),
            )
            CardioMode.WALK -> Triple(
                Interval(Pace.WARMUP, 0, "спокойный шаг", met(kg, 3.5)),
                Interval(Pace.RUN, 0, "бег ~${fmt(lv.runKmh)} км/ч", met(kg, lv.runMet)),
                Interval(Pace.WALK, 0, "быстрый шаг", met(kg, lv.walkMet)),
            )
            CardioMode.BIKE -> Triple(
                Interval(Pace.WARMUP, 0, "лёгкое вращение", met(kg, 4.0)),
                Interval(Pace.RUN, 0, "быстро, сопротивление выше", met(kg, lv.runMet)),
                Interval(Pace.WALK, 0, "средний темп", met(kg, lv.walkMet + 1)),
            )
            CardioMode.ELLIPTICAL -> Triple(
                Interval(Pace.WARMUP, 0, "лёгкий темп", met(kg, 4.0)),
                Interval(Pace.RUN, 0, "быстрый темп", met(kg, lv.runMet - 1)),
                Interval(Pace.WALK, 0, "средний темп", met(kg, lv.walkMet + 0.5)),
            )
        }
    }

    private fun assemble(p: Profile, lv: Level, totalSec: Int, number: Int): CardioSession {
        val (warm, run, walk) = intervals(p, lv)
        val warmSec = if (totalSec >= 20 * 60) 5 * 60 else 3 * 60
        val coolSec = if (totalSec >= 20 * 60) 4 * 60 else 2 * 60
        val list = mutableListOf(warm.copy(seconds = warmSec))
        var used = warmSec + coolSec
        var i = 0
        while (true) {
            val (r, w) = lv.pattern[i % lv.pattern.size]
            if (used + r > totalSec) break
            if (r > 0) { list += run.copy(seconds = r); used += r }
            val wl = minOf(w, totalSec - used)
            if (wl > 0) { list += walk.copy(seconds = wl); used += wl }
            if (r == 0 && wl == 0) break
            i++
        }
        // добиваем остаток шагом, чтобы время совпало
        if (totalSec - used > 0) list += walk.copy(seconds = totalSec - used)
        list += Interval(Pace.COOLDOWN, coolSec, warm.setting, warm.kcalPerMin)
        return CardioSession(list, number, number, lv.name)
    }

    /**
     * Ты выбираешь только время. Приложение берёт самую лёгкую схему,
     * которая за это время сожжёт [target] ккал. Не хватает времени — самая мощная схема.
     */
    /**
     * Варианты времени, за которые реально сжечь [target] ккал:
     * минимальное (самая высокая интенсивность) и подольше, но полегче.
     */
    fun timeOptions(p: Profile, target: Int): List<Int> {
        if (target <= 0) return listOf(30)
        val min = (15..300 step 5).firstOrNull { build(p, target, it).kcal >= target } ?: 300
        return listOf(min, min + 10, min + 20, min + 35, min + 50).distinctBy { build(p, target, it).patternName }
            .ifEmpty { listOf(min) }
    }

    /** Ближайший к сохранённому вариант (но не меньше минимального). */
    fun pickMinutes(p: Profile, target: Int): Int {
        val opts = timeOptions(p, target)
        return opts.minBy { kotlin.math.abs(it - p.cardioSessionMinutes) }
    }

    fun build(p: Profile, target: Int, minutes: Int, number: Int = 1): CardioSession {
        val total = minutes * 60
        val sessions = levels.map { assemble(p, it, total, number) }
        return sessions.firstOrNull { it.kcal >= target } ?: sessions.last()
    }
}
