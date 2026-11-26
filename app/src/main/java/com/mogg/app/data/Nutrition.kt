package com.mogg.app.data

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Расчёт калорий и кардио.
 *
 * Идея пользователя: ешь ~1500, кардио сжигает 500–1000, чтобы «чистыми» оставалось ~1000.
 * Ограничения по возрасту/весу/росту нет — сушка доступна всем, но дефицит
 * никогда не уходит за безопасную границу (иначе уходят мышцы, в том числе на лице,
 * сыпятся волосы, падает тестостерон, у подростков встаёт рост).
 */
data class CaloriePlan(
    val bmr: Int,
    val tdee: Int,
    val bmi: Double,
    val intake: Int,
    val maxDeficit: Int,
    val cardioTarget: Int,
    val net: Int,
    val deficit: Int,
    val recompMode: Boolean,
    val warnings: List<String>,
    val tips: List<String>,
)

object Nutrition {
    const val TARGET_NET = 1000
    const val CARDIO_MIN = 600
    const val CARDIO_MAX = 1000

    fun bmr(p: Profile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        return if (p.sex == Sex.MALE) base + 5 else base - 161
    }

    fun bmi(p: Profile): Double {
        val m = p.heightCm / 100.0
        return p.weightKg / (m * m)
    }

    fun plan(p: Profile, eatenToday: Int? = null): CaloriePlan {
        val bmr = bmr(p)
        val tdee = bmr * p.activity.factor
        val bmi = bmi(p)
        val minor = p.age < 18
        val warnings = mutableListOf<String>()
        val tips = mutableListOf<String>()

        // Безопасный максимум дефицита в день.
        val maxDeficit = when {
            bmi < 17.5 -> 0
            minor -> 400
            else -> 1000
        }
        val recomp = maxDeficit == 0

        val wantedCardio = p.cardioGoal.coerceIn(0, CARDIO_MAX)
        val cardio: Int
        val intake: Int
        if (p.cardioPriority) {
            // Кардио минимум 600, еда поднимается, чтобы дефицит не превысил maxDeficit.
            cardio = wantedCardio
            intake = max(p.intakeGoal, (tdee + cardio - maxDeficit).roundToInt())
            if (intake > p.intakeGoal) tips += "Чтобы кардио было $cardio ккал, еда поднята до $intake ккал."
        } else {
            // Еда — ровно как выбрал. Кардио — сколько влезает в дефицит.
            intake = p.intakeGoal
            val allowed = (maxDeficit - (tdee - intake)).roundToInt()
            cardio = allowed.coerceIn(0, wantedCardio)
            if (cardio < wantedCardio) tips += "Хочешь кардио $wantedCardio ккал — выбери «Приоритет: кардио», еда станет ${(tdee + wantedCardio - maxDeficit).roundToInt()} ккал."
            if (allowed < 0) warnings += "Еда уже даёт дефицит ${(tdee - intake).roundToInt()} ккал."
        }

        val net = intake - cardio
        val deficit = (tdee + cardio - intake).roundToInt()

        tips += "Белок: ${(p.weightKg * 1.6).roundToInt()}+ г в день."

        return CaloriePlan(
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            bmi = bmi,
            intake = intake,
            maxDeficit = maxDeficit,
            cardioTarget = cardio,
            net = net,
            deficit = deficit,
            recompMode = recomp,
            warnings = warnings,
            tips = tips,
        )
    }
}

/** Расход калорий на кардио (чистый, без учёта покоя). */
object Cardio {
    /** ккал в минуту. Для дорожки — формула ACSM для ходьбы с наклоном. */
    fun kcalPerMinute(p: Profile, mode: CardioMode = p.cardioMode): Double {
        return when (mode) {
            CardioMode.TREADMILL -> {
                val speedMMin = p.treadmillSpeed * 1000 / 60
                val grade = p.treadmillIncline / 100
                // если быстрее 8 км/ч — уже бег
                val vo2 = if (p.treadmillSpeed >= 8.0) 0.2 * speedMMin + 0.9 * speedMMin * grade
                else 0.1 * speedMMin + 1.8 * speedMMin * grade
                vo2 * p.weightKg / 1000 * 5
            }
            CardioMode.WALK -> (4.3 - 1) * 3.5 * p.weightKg / 200
            CardioMode.BIKE -> (6.8 - 1) * 3.5 * p.weightKg / 200
            CardioMode.ELLIPTICAL -> (5.0 - 1) * 3.5 * p.weightKg / 200
        }
    }

    fun minutesFor(kcal: Int, p: Profile, mode: CardioMode = p.cardioMode): Int {
        if (kcal <= 0) return 0
        return (kcal / kcalPerMinute(p, mode)).roundToInt().coerceAtLeast(1)
    }

    fun description(p: Profile, mode: CardioMode = p.cardioMode): String = when (mode) {
        CardioMode.TREADMILL -> "Скорость ${"%.1f".format(p.treadmillSpeed)} км/ч, наклон ${"%.0f".format(p.treadmillIncline)}%. " +
            if (p.treadmillSpeed >= 8.0) "Это уже бег." else "Идёшь, не бежишь — можно разговаривать."
        CardioMode.WALK -> "Быстрый шаг ~6 км/ч. Дыхание учащённое, но можешь говорить."
        CardioMode.BIKE -> "Умеренный темп, лёгкое сопротивление, ~60–80 об/мин."
        CardioMode.ELLIPTICAL -> "Средний темп, сопротивление 3–5 из 10."
    }

    /** Разбиваем на сессии не длиннее 45 минут — так комфортнее. */
    fun sessions(totalMinutes: Int): List<Int> {
        if (totalMinutes <= 0) return emptyList()
        val n = (totalMinutes + 44) / 45
        val base = totalMinutes / n
        val rest = totalMinutes % n
        return List(n) { i -> base + if (i < rest) 1 else 0 }
    }
}
