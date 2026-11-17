package com.kharchacheck.app.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.Subscription
import com.kharchacheck.app.domain.Subscriptions
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.StatBlock
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SubscriptionsScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val s = data.settings
    val subs = remember(data.txns) { Subscriptions.detect(data.txns, data.catMap, LocalDate.now()) }
    val still = remember(data.txns, s.subsCancelled) { Subscriptions.stillCharged(data.txns, s.subsCancelled) }
    val active = subs.filter { it.key !in s.subsCancelled }
    val toReview = active.filter { it.key !in s.subsKept }
    val kept = active.filter { it.key in s.subsKept }
    val cancelled = subs.filter { it.key in s.subsCancelled }
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    SubScreen("🔁 Subscriptions", onBack = { nav.popBackStack() }) {
        item {
            KcCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBlock("Active", "${active.size}", Modifier.weight(0.7f))
                    StatBlock("Per month", Money.compact(active.sumOf { it.monthlyPaise }), Modifier.weight(1f))
                    StatBlock("Per year", Money.compact(active.sumOf { it.yearlyPaise }), Modifier.weight(1f), Coral)
                }
                val saved = Subscriptions.yearlySaved(subs, s.subsCancelled.keys)
                if (saved > 0) Text("✂️ Cancelling saves you ${Money.format(saved)} a year.", style = MaterialTheme.typography.titleSmall, color = Kc.colors.good)
                Text("Found from payments to the same place in 3 of the last 4 months. Small amounts add up: ask of each one, \"did I use it last month?\"",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
        }
        if (still.isNotEmpty()) item {
            KcCard(title = "⚠️ Still being charged") {
                still.take(5).forEach { sc ->
                    Text("${sc.payee}: ${Money.format(sc.charge.amountPaise)} on ${sc.charge.date.format(fmt)}, after you cancelled on ${sc.cancelledOn.format(fmt)}",
                        style = MaterialTheme.typography.bodyMedium, color = Coral)
                }
                Text("Check the app's account page and your UPI app → AutoPay / mandates, and revoke the mandate. Ask for a refund if it renewed after cancelling.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
        }
        if (subs.isEmpty()) item { EmptyState("🔁", "No subscriptions spotted yet", "Once a payee shows up in 3 of 4 months (streaming, music, apps, gym…), it appears here.") }
        if (toReview.isNotEmpty()) item { Text("Still using these?", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text) }
        items(toReview, key = { "r-" + it.key }) { sub -> SubRow(sub, fmt, vm, reviewed = false) }
        if (kept.isNotEmpty()) item { Text("Keeping", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text, modifier = Modifier.padding(top = 6.dp)) }
        items(kept, key = { "k-" + it.key }) { sub -> SubRow(sub, fmt, vm, reviewed = true) }
        if (cancelled.isNotEmpty()) item {
            KcCard(title = "Cancelled") {
                cancelled.forEach { c -> Text("✂️ ${c.payee} · ${Money.format(c.yearlyPaise)}/year", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted) }
            }
        }
    }
}

@Composable
private fun SubRow(sub: Subscription, fmt: DateTimeFormatter, vm: AppViewModel, reviewed: Boolean) {
    KcCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sub.category?.emoji ?: "🔁", fontSize = 26.sp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sub.payee, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                Text("Last paid ${sub.lastPaid.format(fmt)} · seen ${sub.monthsSeen} months", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${Money.format(sub.monthlyPaise)}/mo", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                Text("${Money.compact(sub.yearlyPaise)}/yr", style = MaterialTheme.typography.labelSmall, color = Coral)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!reviewed) Pill("👍 Keep", Kc.colors.chip, Kc.colors.text) { vm.keepSubscription(sub, true) }
            Pill("✂️ Cancelled", Haldi, Ink) { vm.cancelSubscription(sub) }
            Pill("🔔 Remind me", Kc.colors.chip, Kc.colors.text) { vm.remindSubscription(sub) }
        }
    }
}
