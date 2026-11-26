package com.mogg.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mogg.app.MainActivity
import com.mogg.app.R
import com.mogg.app.data.Nutrition
import com.mogg.app.data.Store
import java.time.LocalDateTime
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "mogg_reminders"
    const val TYPE_MORNING = "morning"
    const val TYPE_EVENING = "evening"
    const val TYPE_CARDIO = "cardio"
    const val EXTRA_TYPE = "type"
    const val EXTRA_OPEN = "open"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Напоминания о тренировках", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Утро, вечер и кардио"
                }
            )
        }
    }

    fun scheduleAll(context: Context) {
        val p = Store.load(context).profile
        val am = context.getSystemService(AlarmManager::class.java)
        listOf(
            TYPE_MORNING to (p.morningHour to p.morningMinute),
            TYPE_CARDIO to (14 to 0),
            TYPE_EVENING to (p.eveningHour to p.eveningMinute),
        ).forEachIndexed { i, (type, time) ->
            val pi = pending(context, type, i)
            am.cancel(pi)
            if (!p.remindersOn || !p.onboarded) return@forEachIndexed
            var at = LocalDateTime.now().withHour(time.first).withMinute(time.second).withSecond(0).withNano(0)
            if (at.isBefore(LocalDateTime.now())) at = at.plusDays(1)
            val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, millis, AlarmManager.INTERVAL_DAY, pi)
        }
    }

    private fun pending(context: Context, type: String, code: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TYPE, type)
        return PendingIntent.getBroadcast(context, 100 + code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(context: Context, type: String) {
        val state = Store.load(context)
        val day = Store.day()
        val (title, text) = when (type) {
            TYPE_MORNING -> {
                if (day.morningDone) return
                "Утро · 10 минут" to "Лимфодренаж и лицо. Сними отёк, пока не ушёл из дома."
            }
            TYPE_EVENING -> {
                if (day.eveningDone) return
                "Вечер · 20 минут" to "Осанка, челюсть, глаза. Серия: ${Store.streak(state)} дн. Не сливай."
            }
            else -> {
                val plan = Nutrition.plan(state.profile, day.eatenTotal)
                val left = plan.cardioTarget - day.cardioKcal
                if (left <= 0) return
                "Кардио" to "Осталось сжечь ~$left ккал. Включай MrBeast и иди на дорожку."
            }
        }
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, type)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, type.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(type.hashCode(), n)
        } catch (_: SecurityException) {
            // нет разрешения на уведомления
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Reminders.createChannel(context)
        Reminders.show(context, intent.getStringExtra(Reminders.EXTRA_TYPE) ?: return)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Reminders.scheduleAll(context)
    }
}
