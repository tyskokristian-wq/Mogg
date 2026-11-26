package com.mogg.app.workout

import com.mogg.app.data.Exercise
import com.mogg.app.data.Flaw
import com.mogg.app.data.Profile
import com.mogg.app.data.Slot
import java.time.LocalDate

sealed class WorkoutStep {
    abstract val seconds: Int
    data class Do(val exercise: Exercise, override val seconds: Int) : WorkoutStep()
    data class Rest(val next: Exercise, override val seconds: Int = RoutineBuilder.REST_SEC) : WorkoutStep()
}

/**
 * Собирает индивидуальную тренировку под недостатки человека.
 * Утро — до 10 минут, вечер — до 20, с отдыхом 10 сек между упражнениями.
 * Упражнения подбираются по кругу под каждый недостаток (сначала самые сильные),
 * и меняются день ото дня, чтобы не было скучно.
 */
object RoutineBuilder {
    const val REST_SEC = 10
    const val MORNING_BUDGET = 10 * 60
    const val EVENING_BUDGET = 20 * 60

    private val firstIds = listOf("lymph_ears", "lymph_face", "neck_collar_warmup", "door_pec", "chin_tucks", "neck_curls", "floor_angels")
    private val lastIds = listOf("masseter_release")

    fun build(all: List<Exercise>, profile: Profile, slot: Slot, date: LocalDate = LocalDate.now()): List<WorkoutStep> {
        val budget = if (slot == Slot.MORNING) MORNING_BUDGET else EVENING_BUDGET
        val evenDay = date.dayOfYear % 2 == 0
        val pool = all.filter { (it.slot == slot || it.slot == Slot.ANY) && (!it.everyOtherDay || evenDay) }

        val flaws: List<Pair<Flaw, Int>> = Flaw.entries
            .map { it to profile.flawLevel(it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .ifEmpty { Flaw.entries.map { it to 1 } } // ничего не отмечено — общий уход за всем лицом

        val chosen = mutableListOf<Exercise>()
        var used = 0
        fun cost(e: Exercise) = e.durationSec + if (chosen.isEmpty()) 0 else REST_SEC
        fun tryAdd(e: Exercise): Boolean {
            if (e in chosen) return false
            val c = cost(e)
            if (used + c > budget) return false
            chosen += e; used += c
            return true
        }

        // 1. База: лимфодренаж утром, осанка вечером — нужна всем.
        pool.filter { it.base && it.slot == slot }.forEach { tryAdd(it) }

        // 2. По кругу под каждый недостаток. Сильный недостаток получает больше ходов.
        val seed = date.toEpochDay()
        var progress = true
        var round = 0
        while (progress) {
            progress = false
            for ((flaw, level) in flaws) {
                if (round >= level * 2) continue
                val candidates = pool
                    .filter { it !in chosen && it.weight(flaw) > 0 }
                    .sortedWith(compareByDescending<Exercise> { it.weight(flaw) * 10 + score(it, flaws) }.thenBy { it.id })
                if (candidates.isEmpty()) continue
                // Ротация: каждый день начинаем с другого из топ-3, чтобы программа менялась.
                val top = candidates.take(3)
                val start = ((seed + flaw.ordinal + round) % top.size).toInt()
                val ordered = top.drop(start) + top.take(start) + candidates.drop(3)
                if (ordered.any { tryAdd(it) }) progress = true
            }
            round++
            if (round > 12) break
        }

        // 3. Если осталось время — добиваем самыми полезными для этого человека.
        pool.filter { it !in chosen }
            .sortedByDescending { score(it, flaws) }
            .forEach { tryAdd(it) }

        val ordered = chosen.sortedBy { order(it) }
        val steps = mutableListOf<WorkoutStep>()
        ordered.forEachIndexed { i, e ->
            if (i > 0) steps += WorkoutStep.Rest(next = e)
            steps += WorkoutStep.Do(e, e.durationSec)
        }
        return steps
    }

    private fun score(e: Exercise, flaws: List<Pair<Flaw, Int>>): Int = flaws.sumOf { (f, lvl) -> e.weight(f) * lvl }

    private fun order(e: Exercise): Int {
        val f = firstIds.indexOf(e.id)
        if (f >= 0) return f
        if (e.id in lastIds) return 1000
        // группируем по видео, чтобы меньше прыгать по лицу
        return 100 + (e.video?.hashCode()?.mod(50) ?: 60)
    }

    fun totalSeconds(steps: List<WorkoutStep>) = steps.sumOf { it.seconds }
}
