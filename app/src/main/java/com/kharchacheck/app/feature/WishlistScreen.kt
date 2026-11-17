package com.kharchacheck.app.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.ui.components.MicButton
import com.kharchacheck.app.ui.components.spokenName
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.WishDecision
import com.kharchacheck.app.domain.WishItem
import com.kharchacheck.app.domain.Wishlist
import com.kharchacheck.app.ui.AppData
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.StatBlock
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.nav.EditRoute
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import java.time.LocalDate

/** Cooling-off wishlist: park a purchase, decide after the wait. Skipped items count as money kept. */
@Composable
fun WishlistScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val s = data.settings
    val today = LocalDate.now()
    var adding by rememberSaveable { mutableStateOf(false) }
    val waiting = s.wishlist.filter { it.decision == WishDecision.WAITING }.sortedBy { it.readyOn }
    val ready = waiting.filter { Wishlist.ready(it, today) }
    val pending = waiting - ready.toSet()
    val decided = s.wishlist.filter { it.decision != WishDecision.WAITING }.sortedByDescending { it.decidedOn }
    val saved = Wishlist.savedBySkipping(s.wishlist)

    SubScreen("🛒 Wishlist", onBack = { nav.popBackStack() }) {
        item {
            KcCard {
                Text("Want something? Park it here instead of buying. After a short wait (2 days for small things, up to 30 for big ones) you decide with a clear head. " +
                    "Research on impulse buying finds most urges fade with a little time.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBlock("Kept by skipping", Money.compact(saved), Modifier.weight(1f), Kc.colors.good)
                    StatBlock("Skipped", "${s.wishlist.count { it.decision == WishDecision.SKIPPED }}", Modifier.weight(0.7f))
                    StatBlock("Waiting", "${waiting.size}", Modifier.weight(0.7f))
                }
                Wishlist.lifeHours(saved, s.monthlyIncomePaise, s.workHoursPerMonth)?.takeIf { saved > 0 }?.let {
                    Text("That's ${Wishlist.hoursLabel(it)} you didn't trade for things you didn't need.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                }
                BigButton("+ I want to buy something") { adding = true }
            }
        }
        if (ready.isNotEmpty()) item { Text("⏰ Wait's over: decide", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text) }
        items(ready, key = { "r-" + it.id }) { w ->
            WishCard(w, data, today) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("🙅 Skip it", Haldi, Ink) { vm.decideWish(w.id, WishDecision.SKIPPED) }
                    Pill("✅ Buy it", Kc.colors.chip, Kc.colors.text) {
                        vm.decideWish(w.id, WishDecision.BOUGHT)
                        nav.navigate(EditRoute(amountPaise = w.pricePaise, payee = w.name))
                    }
                    Pill("+7 days", Kc.colors.chip, Kc.colors.muted) { vm.extendWish(w.id, 7) }
                }
            }
        }
        if (pending.isNotEmpty()) item { Text("Cooling off", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text) }
        items(pending, key = { "p-" + it.id }) { w ->
            WishCard(w, data, today) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Decide in ${Wishlist.daysLeft(w, today)} days", style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted, modifier = Modifier.weight(1f))
                    Pill("Skip now", Kc.colors.chip, Kc.colors.text) { vm.decideWish(w.id, WishDecision.SKIPPED) }
                    Pill("✕", Kc.colors.chip, Kc.colors.muted) { vm.deleteWish(w.id) }
                }
            }
        }
        if (s.wishlist.isEmpty()) item { EmptyState("🛒", "Nothing parked yet", "Next time something tempts you online, add it here before you buy.") }
        if (decided.isNotEmpty()) item {
            KcCard(title = "Decisions") {
                decided.take(20).forEach { w ->
                    Text((if (w.decision == WishDecision.SKIPPED) "🙅 Skipped " else "✅ Bought ") + "${w.name} · ${Money.format(w.pricePaise)}",
                        style = MaterialTheme.typography.bodyMedium, color = if (w.decision == WishDecision.SKIPPED) Kc.colors.good else Kc.colors.muted)
                }
            }
        }
    }
    if (adding) AddWishDialog(s.monthlyIncomePaise, s.workHoursPerMonth, onDismiss = { adding = false }) { name, price, uses, signals -> vm.addWish(name, price, uses, signals); adding = false }
}

@Composable
private fun WishCard(w: WishItem, d: AppData, today: LocalDate, actions: @Composable () -> Unit) {
    val hrs = Wishlist.lifeHours(w.pricePaise, d.settings.monthlyIncomePaise, d.settings.workHoursPerMonth)
    KcCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.name, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, modifier = Modifier.weight(1f))
            Text(Money.format(w.pricePaise), style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
        }
        val facts = listOfNotNull(
            hrs?.let { "⏱️ ${Wishlist.hoursLabel(it)}" },
            Wishlist.costPerUse(w.pricePaise, w.usesPerMonth)?.let { "💳 about ${Money.format(it)} per use over a year" },
            if (w.wantSignals >= 2) "🤔 ${w.wantSignals} of 4 answers said \"want, not need\"" else null,
        )
        facts.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("🤔")) Coral else Kc.colors.muted) }
        actions()
    }
}

@Composable
private fun AddWishDialog(income: Long, hours: Int, onDismiss: () -> Unit, onAdd: (String, Long, Int, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var uses by rememberSaveable { mutableStateOf("") }
    val checks = remember { mutableStateOf(List(Wishlist.QUESTIONS.size) { false }) }
    val paise = (price.toLongOrNull() ?: 0) * 100
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Park it on the wishlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("What is it?") }, singleLine = true,
                    trailingIcon = { MicButton("What do you want to buy?") { name = spokenName(it, 40) } }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(price, { v -> price = v.filter(Char::isDigit).take(8) }, label = { Text("Price, with delivery & fees") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (paise > 0) {
                    Wishlist.lifeHours(paise, income, hours)?.let { Text("⏱️ ${Wishlist.hoursLabel(it)}", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text) }
                    Text("You'll decide in ${Wishlist.waitDays(paise)} days.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                }
                OutlinedTextField(uses, { v -> uses = v.filter(Char::isDigit).take(3) }, label = { Text("Times you'd use it per month (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text("Be honest. Tick what's true:", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                Wishlist.QUESTIONS.forEachIndexed { i, q ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checks.value[i], { on -> checks.value = checks.value.toMutableList().also { it[i] = on } })
                        Text(q, style = MaterialTheme.typography.bodySmall, color = Kc.colors.text)
                    }
                }
                val n = checks.value.count { it }
                if (n >= 2) Text("$n of 4 point to a want, not a need. The wait will help.", style = MaterialTheme.typography.bodySmall, color = Coral)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && paise > 0, onClick = { onAdd(name, paise, uses.toIntOrNull() ?: 0, checks.value.count { it }) }) { Text("Park it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
