package com.kharchacheck.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kharchacheck.app.MainActivity
import com.kharchacheck.app.R
import com.kharchacheck.app.container
import com.kharchacheck.app.domain.DailyWisdom
import com.kharchacheck.app.domain.Gamification
import com.kharchacheck.app.work.Notifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Home-screen widget like Bas 1 Minute's: streak, logged-today status, one-tap Add, today's money thought.
 * Deliberately shows no amounts: a home screen is visible to anyone who glances at the phone.
 */
class KharchaWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                val s = c.settings.current()
                val txns = c.finance.txns.first()
                val cats = c.finance.categories.first().associateBy { it.id }
                val g = Gamification.build(txns, cats, emptyList(), s.noSpendDays, s.challengesWon, s.monthlyBudgetPaise)
                push(context, State(s.onboarded, g.logStreak, g.loggedToday, s.wisdomSeed))
            } catch (_: Exception) {
                push(context, State(false, 0, false, 0))
            } finally {
                pending.finish()
            }
        }
    }

    data class State(val onboarded: Boolean, val streak: Int, val loggedToday: Boolean, val seed: Long)

    companion object {
        /** Called by the app whenever its data changes, so the widget never lags behind. */
        fun push(context: Context, state: State) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, KharchaWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context, state)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context, s: State): RemoteViews = RemoteViews(context.packageName, R.layout.widget_kharcha).apply {
            setTextViewText(R.id.widget_streak, when {
                !s.onboarded -> "Kharcha Check"
                s.streak > 0 -> "🔥 ${s.streak}-day streak"
                else -> "Start your streak"
            })
            setTextViewText(R.id.widget_status, when {
                !s.onboarded -> "Open the app to set up"
                s.loggedToday -> "Logged today ✓"
                s.streak > 0 -> "Log today to keep it going"
                else -> "Tap + to log a spend"
            })
            val thought = if (s.seed != 0L) DailyWisdom.forDate(LocalDate.now(), s.seed).let { "${it.emoji} ${it.line}" } else ""
            setTextViewText(R.id.widget_thought, thought)

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open = PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java), flags)
            val add = PendingIntent.getActivity(context, 11,
                Intent(context, MainActivity::class.java).setAction("com.kharchacheck.app.ADD").putExtra(Notifier.EXTRA_OPEN, "add")
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags)
            setOnClickPendingIntent(R.id.widget_root, open)
            setOnClickPendingIntent(R.id.widget_add, add)
        }
    }
}
