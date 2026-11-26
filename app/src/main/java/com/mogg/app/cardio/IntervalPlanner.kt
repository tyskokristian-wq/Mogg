package com.mogg.app.cardio

import com.mogg.app.data.CardioMode
import com.mogg.app.data.Profile
import kotlin.math.roundToInt

enum class Pace { WARMUP, EASY, HARD, COOLDOWN }

data class Interval(
    val pace: Pace,
    val seconds: Int,
    /** «БЕГ», «ШАГ», «БЫСТРО», «СПОКОЙНО», «РАЗМИНКА»… */
    val label: String,
    /** Что выставить: «9 км/ч · 1%» или «сопротивление 6–7». */
    val setting: String,
    val kcalPerMin: Double,
) {
    val kcal: Double get() = kcalPerMin * seconds / 60
}

data class CardioSession(
    val intervals: List<Interval>,
    val name: String,
) {
    val seconds: Int get() = intervals.sumOf { it.seconds }
    val minutes: Int get() = seconds / 60
    val kcal: Int get() = intervals.sumOf { it.kcal }.roundToInt()
}

/**
 * Сам придумывает кардио на нужные калории (500 или 600).
 * Ты выбираешь вариант — от «тяжело, но быстро» до «легко, но дольше»,
 * время под калории считается само.
 */
object IntervalPlanner {

    private data class Level(
        val id: String,
        val name: String,
        /** (быстрая часть, спокойная часть) в секундах; 0 быстрой = ровный шаг без бега. */
        val pattern: List<Pair<Int, Int>>,
        val runKmh: Double = 0.0, val runIncline: Double = 1.0,
        val walkKmh: Double = 5.5, val walkIncline: Double = 8.0,
        /** Для улицы/эллипса/велосипеда — MET быстрой и спокойной части. */
        val hardMet: Double = 0.0, val easyMet: Double = 0.0,
        val walkOnly: Boolean = false,
    )

    private val levels = listOf(
        Level("i6", "5 мин бег / 1 мин шаг — максимум", listOf(300 to 60), runKmh = 10.5, runIncline = 2.0, walkIncline = 10.0, hardMet = 10.5, easyMet = 5.5),
        Level("i5", "4 мин бег / 1 мин шаг", listOf(240 to 60), runKmh = 10.0, walkIncline = 10.0, hardMet = 10.0, easyMet = 5.3),
        Level("i4", "3 мин бег / 2 мин шаг", listOf(180 to 120), runKmh = 9.0, walkIncline = 10.0, hardMet = 9.0, easyMet = 5.0),
        Level("i3", "Пирамида 1-2-3-2-1", listOf(60 to 90, 120 to 90, 180 to 120, 120 to 90, 60 to 90), runKmh = 8.5, walkIncline = 8.0, hardMet = 8.5, easyMet = 4.5),
        Level("i2", "2 мин бег / 2 мин шаг", listOf(120 to 120), runKmh = 8.5, walkIncline = 8.0, hardMet = 8.0, easyMet = 4.5),
        Level("i1", "1 мин бег / 2 мин шаг", listOf(60 to 120), runKmh = 8.0, walkIncline = 6.0, hardMet = 7.5, easyMet = 4.3),
        Level("w2", "Шаг в крутую горку (без бега)", listOf(0 to 300), walkKmh = 5.5, walkIncline = 12.0, easyMet = 6.0, walkOnly = true),
        Level("w1", "Шаг в горку (без бега)", listOf(0 to 300), walkKmh = 5.0, walkIncline = 8.0, easyMet = 5.0, walkOnly = true),
    )

    /** ACSM: ходьба / бег с наклоном, чистые ккал в минуту. */
    private fun treadmill(kg: Double, kmh: Double, inclinePct: Double): Double {
        val v = kmh * 1000 / 60
        val g = inclinePct / 100
        val vo2 = if (kmh >= 8) 0.2 * v + 0.9 * v * g else 0.1 * v + 1.8 * v * g
        return vo2 * kg / 1000 * 5
    }

    private fun met(kg: Double, met: Double) = (met - 1) * 3.5 * kg / 200

    private fun fmt(x: Double) = if (x % 1.0 == 0.0) "%.0f".format(x) else "%.1f".format(x).replace('.', ',')

    private fun available(mode: CardioMode) =
        levels.filter { !it.walkOnly || mode == CardioMode.TREADMILL || mode == CardioMode.WALK }

    /** Разминка, быстрая часть, спокойная часть — с подписями под режим. */
    private fun parts(p: Profile, lv: Level): Triple<Interval, Interval, Interval> {
        val kg = p.weightKg
        return when (p.cardioMode) {
            CardioMode.TREADMILL -> Triple(
                Interval(Pace.WARMUP, 0, "РАЗМИНКА", "5 км/ч · наклон 3%", treadmill(kg, 5.0, 3.0)),
                Interval(Pace.HARD, 0, "БЕГ", "${fmt(lv.runKmh)} км/ч · наклон ${fmt(lv.runIncline)}%", treadmill(kg, lv.runKmh, lv.runIncline)),
                Interval(Pace.EASY, 0, "ШАГ", "${fmt(lv.walkKmh)} км/ч · наклон ${fmt(lv.walkIncline)}%", treadmill(kg, lv.walkKmh, lv.walkIncline)),
            )
            CardioMode.WALK -> Triple(
                Interval(Pace.WARMUP, 0, "РАЗМИНКА", "спокойный шаг", met(kg, 3.5)),
                Interval(Pace.HARD, 0, "БЕГ", "бег ~${fmt(lv.runKmh)} км/ч", met(kg, lv.hardMet)),
                Interval(Pace.EASY, 0, "ШАГ", if (lv.walkOnly) "быстрый шаг в горку / по лестнице" else "быстрый шаг", met(kg, lv.easyMet)),
            )
            CardioMode.BIKE -> Triple(
                Interval(Pace.WARMUP, 0, "РАЗМИНКА", "лёгкое вращение", met(kg, 4.0)),
                Interval(Pace.HARD, 0, "БЫСТРО", "быстро, сопротивление выше", met(kg, lv.hardMet)),
                Interval(Pace.EASY, 0, "СПОКОЙНО", "средний темп", met(kg, lv.easyMet + 1)),
            )
            CardioMode.ELLIPTICAL -> Triple(
                Interval(Pace.WARMUP, 0, "РАЗМИНКА", "лёгкий темп", met(kg, 4.0)),
                Interval(Pace.HARD, 0, "БЫСТРО", "быстрый темп, сопротивление 6–8", met(kg, lv.hardMet - 1)),
                Interval(Pace.EASY, 0, "СПОКОЙНО", "средний темп, сопротивление 3–4", met(kg, lv.easyMet + 0.5)),
            )
        }
    }

    /** На эллипсе и велосипеде нет «бега» и «шага». */
    private fun displayName(mode: CardioMode, lv: Level): String =
        if (mode == CardioMode.BIKE || mode == CardioMode.ELLIPTICAL)
            lv.name.replace("бег", "быстро").replace("шаг", "спокойно")
        else lv.name

    private fun assemble(p: Profile, lv: Level, totalSec: Int): CardioSession {
        val (warm, hard, easy) = parts(p, lv)
        val warmSec = 4 * 60
        val coolSec = 3 * 60
        val list = mutableListOf(warm.copy(seconds = warmSec))
        var used = warmSec + coolSec
        var i = 0
        while (used < totalSec) {
            val (h, e) = lv.pattern[i % lv.pattern.size]
            if (h > 0) {
                if (used + h > totalSec) break
                list += hard.copy(seconds = h); used += h
            }
            val el = minOf(e, totalSec - used)
            if (el > 0) { list += easy.copy(seconds = el); used += el }
            i++
        }
        if (totalSec - used > 0) list += easy.copy(seconds = totalSec - used)
        list += warm.copy(pace = Pace.COOLDOWN, label = "ЗАМИНКА", seconds = coolSec)
        return CardioSession(list, displayName(p.cardioMode, lv))
    }

    /** Минимальное время (с точностью до минуты), за которое вариант сжигает [target]. */
    private fun sessionFor(p: Profile, lv: Level, target: Int): CardioSession {
        var lo = 10
        var hi = 300
        if (assemble(p, lv, hi * 60).kcal < target) return assemble(p, lv, hi * 60)
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (assemble(p, lv, mid * 60).kcal >= target) hi = mid else lo = mid + 1
        }
        return assemble(p, lv, lo * 60)
    }

    data class Option(val id: String, val session: CardioSession)

    /** Все варианты на [target] ккал — от самого быстрого к самому долгому. */
    fun options(p: Profile, target: Int): List<Option> =
        available(p.cardioMode).map { Option(it.id, sessionFor(p, it, maxOf(target, 1))) }.sortedBy { it.session.seconds }

    private fun pick(p: Profile, target: Int): Option {
        val opts = options(p, target)
        return opts.firstOrNull { it.id == p.cardioLevel } ?: opts.lastOrNull { it.session.minutes <= 60 } ?: opts.first()
    }

    /** Выбранный вариант (по умолчанию — самый лёгкий, что укладывается в час). */
    fun build(p: Profile, target: Int): CardioSession = pick(p, target).session

    fun selectedId(p: Profile, target: Int): String = pick(p, target).id
}
