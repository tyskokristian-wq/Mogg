package com.mogg.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mogg.app.MainActivity
import com.mogg.app.R
import com.mogg.app.data.Nutrition
import com.mogg.app.data.Store

/** Виджет на рабочий стол: что сделано сегодня. */
class MoggWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MoggWidget::class.java))
            if (ids.isEmpty()) return
            val v = views(context)
            ids.forEach { manager.updateAppWidget(it, v) }
        }

        private fun views(context: Context): RemoteViews {
            val state = Store.load(context)
            val day = Store.day()
            val plan = Nutrition.plan(state.profile, day.eatenTotal)
            val v = RemoteViews(context.packageName, R.layout.widget_mogg)
            v.setTextViewText(R.id.w_morning, (if (day.morningDone) "✅" else "⬜") + " Утро")
            v.setTextViewText(R.id.w_evening, (if (day.eveningDone) "✅" else "⬜") + " Вечер")
            v.setTextViewText(R.id.w_cardio, "🔥 Кардио ${day.cardioKcal}/${plan.cardioTarget}")
            v.setTextViewText(R.id.w_food, "🍽 Еда ${day.eatenTotal}/${plan.intake}")
            v.setTextViewText(R.id.w_streak, "Серия: ${Store.streak(state)} дн.")
            val pi = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.w_root, pi)
            return v
        }
    }
}
