package com.mogg.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import com.mogg.app.data.CardioMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * YAZIO не даёт открытого API, но пишет съеденное в Health Connect.
 * Мы читаем оттуда калории за день и записываем туда своё кардио.
 * В YAZIO: Профиль → Настройки → Подключённые приложения → Health Connect.
 */
object HealthSync {
    val permissions = setOf(
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
    )

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))

    suspend fun hasPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /** Калории, съеденные сегодня (из YAZIO и других приложений). */
    suspend fun eatenToday(context: Context): Int? {
        if (!isAvailable(context)) return null
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now().atStartOfDay(zone).toInstant()
            val end = Instant.now()
            val res = client.aggregate(
                AggregateRequest(
                    metrics = setOf(NutritionRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            )
            res[NutritionRecord.ENERGY_TOTAL]?.inKilocalories?.toInt()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun writeCardio(context: Context, mode: CardioMode, start: Instant, end: Instant, kcal: Int) {
        if (!isAvailable(context)) return
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val offset: ZoneOffset = ZoneId.systemDefault().rules.getOffset(start)
            val type = when (mode) {
                CardioMode.TREADMILL -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
                CardioMode.WALK -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
                CardioMode.BIKE -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
                CardioMode.ELLIPTICAL -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
            }
            client.insertRecords(
                listOf(
                    ExerciseSessionRecord(
                        startTime = start, startZoneOffset = offset,
                        endTime = end, endZoneOffset = offset,
                        exerciseType = type, title = "Mogg: ${mode.title}",
                    ),
                    ActiveCaloriesBurnedRecord(
                        startTime = start, startZoneOffset = offset,
                        endTime = end, endZoneOffset = offset,
                        energy = Energy.kilocalories(kcal.toDouble()),
                    ),
                )
            )
        } catch (_: Exception) {
        }
    }
}
