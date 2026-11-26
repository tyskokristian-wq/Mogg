package com.mogg.app

import com.mogg.app.data.Activity
import com.mogg.app.data.ExerciseLibraryFile
import com.mogg.app.data.Flaw
import com.mogg.app.data.JsonConfig
import com.mogg.app.data.Nutrition
import com.mogg.app.data.Profile
import com.mogg.app.data.Slot
import com.mogg.app.workout.RoutineBuilder
import com.mogg.app.workout.WorkoutStep
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class LogicTest {
    private val lib = JsonConfig.json.decodeFromString(
        ExerciseLibraryFile.serializer(), File("src/main/assets/exercises.json").readText()
    )

    private fun show(name: String, p: Profile) {
        for (slot in listOf(Slot.MORNING, Slot.EVENING)) {
            for (d in 0L..1L) {
                val steps = RoutineBuilder.build(lib.exercises, p, slot, LocalDate.of(2026, 11, 26).plusDays(d))
                val total = RoutineBuilder.totalSeconds(steps)
                val names = steps.filterIsInstance<WorkoutStep.Do>().map { it.exercise.id }
                println("$name $slot day+$d: ${total}s ${names.size} ex: $names")
                assertTrue(total <= if (slot == Slot.MORNING) 600 else 1200)
                assertTrue(total >= if (slot == Slot.MORNING) 450 else 900)
                // видео-отрезки валидны
                steps.filterIsInstance<WorkoutStep.Do>().forEach {
                    val e = it.exercise
                    if (e.video != null) {
                        assertTrue(e.id, e.clipEnd > e.clipStart)
                        assertTrue(e.video, File("src/main/assets/videos/${e.video}").exists())
                    }
                }
            }
        }
    }

    @Test fun routines() {
        show("none", Profile())
        show("asym", Profile(flaws = mapOf(Flaw.ASYMMETRY.name to 3, Flaw.EYES_TIRED.name to 2)))
        show("puffy", Profile(flaws = mapOf(Flaw.PUFFINESS.name to 3, Flaw.DOUBLE_CHIN.name to 2, Flaw.POSTURE.name to 3, Flaw.LAZY_EYE.name to 1)))
    }

    @Test fun calories() {
        listOf(
            Profile(age = 25, heightCm = 175, weightKg = 70.0),
            Profile(age = 25, heightCm = 175, weightKg = 95.0, intakeGoal = 1800),
            Profile(age = 25, heightCm = 185, weightKg = 85.0, activity = Activity.MODERATE, intakeGoal = 2000),
            Profile(age = 15, heightCm = 170, weightKg = 60.0),
            Profile(age = 22, heightCm = 180, weightKg = 57.0),
        ).forEach { p ->
            val plan = Nutrition.plan(p)
            println("age=${p.age} ${p.heightCm}/${p.weightKg} eat=${p.intakeGoal}: tdee=${plan.tdee} cardio=${plan.cardioTarget} net=${plan.net} deficit=${plan.deficit} max=${plan.maxDeficit} recomp=${plan.recompMode}")
            assertTrue(plan.deficit <= plan.maxDeficit + 1 || (plan.cardioTarget == 0 && plan.warnings.isNotEmpty()))
            assertTrue(plan.cardioTarget in 0..1000)
        }
    }
}
