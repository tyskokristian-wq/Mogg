package com.mogg.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Недостатки, под которые подбираются упражнения. */
enum class Flaw(val title: String, val hint: String) {
    ASYMMETRY("Асимметрия", "Одна сторона лица выше/шире, жевательные мышцы неравные"),
    PUFFINESS("Отёчность", "Лицо опухшее с утра, контур «плывёт»"),
    WEAK_JAW("Слабая линия челюсти", "Нечёткий угол челюсти, подбородок «утоплен»"),
    DOUBLE_CHIN("Второй подбородок", "Мягкая зона под челюстью, подъязычная"),
    EYES_TIRED("Уставший взгляд / наклон глаз", "Нейтральный или отрицательный кантальный наклон, опущенные веки"),
    UNDER_EYE("Мешки и круги под глазами", "Тёмные круги, припухлость под глазами"),
    LAZY_EYE("Ленивый глаз", "Одно веко ниже или глаз открыт меньше"),
    POSTURE("Компьютерная осанка", "Голова вынесена вперёд, плечи завалены"),
    CHEEKS("Щёки / круглое лицо", "Лицо круглое, много мягких тканей"),
    BROWS("Брови", "Редкие или опущенные брови"),
    NOSE("Нос", "Хочется более аккуратную форму носа");
}

enum class Slot { MORNING, EVENING, ANY }

@Serializable
data class Exercise(
    val id: String,
    val title: String,
    val slot: Slot,
    val durationSec: Int,
    val video: String? = null,
    val clipStart: Int = 0,
    val clipEnd: Int = 0,
    val tags: Map<String, Int> = emptyMap(),
    val base: Boolean = false,
    val everyOtherDay: Boolean = false,
    val voice: String,
    val steps: List<String> = emptyList(),
) {
    fun weight(flaw: Flaw): Int = tags[flaw.name] ?: 0
}

@Serializable
data class ExerciseLibraryFile(
    val exercises: List<Exercise>,
    val phrases: Map<String, String>,
)

object ExerciseLibrary {
    @Volatile private var cached: ExerciseLibraryFile? = null

    fun get(context: Context): ExerciseLibraryFile {
        cached?.let { return it }
        val text = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val lib = JsonConfig.json.decodeFromString(ExerciseLibraryFile.serializer(), text)
        cached = lib
        return lib
    }

    fun byId(context: Context, id: String): Exercise? = get(context).exercises.firstOrNull { it.id == id }
}

object JsonConfig {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
}

enum class Sex(val title: String) { MALE("Мужской"), FEMALE("Женский") }

enum class Activity(val title: String, val factor: Double) {
    SITTING("Сидячий образ жизни", 1.2),
    LIGHT("Немного хожу / 1–2 тренировки", 1.375),
    STEPS_10K("10 000 шагов в день", 1.45),
    MODERATE("Активный / 3–5 тренировок", 1.55),
}

enum class CardioMode(val title: String) {
    TREADMILL("Беговая дорожка"),
    WALK("Быстрая ходьба на улице"),
    BIKE("Велосипед / велотренажёр"),
    ELLIPTICAL("Эллипс"),
}

@Serializable
data class FaceMetrics(
    val symmetryScore: Int,
    val asymmetryPct: Double,
    val canthalTiltLeft: Double,
    val canthalTiltRight: Double,
    val eyeOpennessDiffPct: Double,
    val browDiffPct: Double,
    val faceHeightToWidth: Double,
    val jawToCheek: Double,
    val lowerThirdRatio: Double,
    val fwhr: Double,
    val analyzedAt: Long,
)

@Serializable
data class Profile(
    val onboarded: Boolean = false,
    val sex: Sex = Sex.MALE,
    val age: Int = 18,
    val heightCm: Int = 175,
    val weightKg: Double = 70.0,
    val activity: Activity = Activity.SITTING,
    val intakeGoal: Int = 1500,
    /** true — кардио минимум 600 (еда поднимается), false — еда ровно как выбрал. */
    val cardioPriority: Boolean = true,
    /** Сколько минут готов кардио — единственное, что выбираешь. */
    val cardioSessionMinutes: Int = 45,
    /** flaw.name -> сила 1..3 */
    val flaws: Map<String, Int> = emptyMap(),
    val photoFlaws: Map<String, Int> = emptyMap(),
    val quizFlaws: Map<String, Int> = emptyMap(),
    val face: FaceMetrics? = null,
    val morningHour: Int = 8,
    val morningMinute: Int = 0,
    val eveningHour: Int = 21,
    val eveningMinute: Int = 0,
    val remindersOn: Boolean = true,
    val cardioMode: CardioMode = CardioMode.TREADMILL,
    val treadmillSpeed: Double = 5.0,
    val treadmillIncline: Double = 8.0,
    /** Своя ссылка на YouTube вместо последнего видео MrBeast. */
    val videoOverride: String = "",
    val videoVolume: Int = 70,
    val videoDuckVolume: Int = 8,
    val useHealthConnect: Boolean = false,
) {
    fun flawLevel(f: Flaw): Int = flaws[f.name] ?: 0
}

@Serializable
data class DayLog(
    val date: String,
    val eatenKcal: Int = 0,
    val healthConnectKcal: Int? = null,
    val cardioKcal: Int = 0,
    val cardioMinutes: Int = 0,
    val cardioSessions: Int = 0,
    val morningDone: Boolean = false,
    val eveningDone: Boolean = false,
    val waterMl: Int = 0,
    val weightKg: Double? = null,
) {
    /** Сколько съедено: если YAZIO (Health Connect) дал больше — берём его. */
    val eatenTotal: Int get() = maxOf(eatenKcal, healthConnectKcal ?: 0)
}

@Serializable
data class AppState(
    val profile: Profile = Profile(),
    val days: Map<String, DayLog> = emptyMap(),
)
