@file:OptIn(ExperimentalMaterial3Api::class)

package com.kharchacheck.app.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.Account
import com.kharchacheck.app.domain.AccountKind
import com.kharchacheck.app.domain.Balances
import com.kharchacheck.app.domain.Trip
import com.kharchacheck.app.domain.Trips
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.MicButton
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.ProgressBar
import com.kharchacheck.app.ui.components.Segmented
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.components.spokenName
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ============================== Accounts ==============================

/** Tools → Accounts & balances: every bank account, cash, card and wallet with its running balance. */
@Composable
fun AccountsScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Account?>(null) }
    val balances = remember(accounts, data.allTxns) { Balances.of(accounts, data.allTxns) }
    val active = accounts.filter { !it.archived }
    val unlinked = remember(data.allTxns, accounts) { if (accounts.isEmpty()) 0 else data.allTxns.count { it.accountId == null } }
    val month = YearMonth.now()

    SubScreen("Accounts & balances", onBack = { nav.popBackStack() }) {
        if (accounts.isEmpty()) {
            item {
                EmptyState("🏦", "Know what's in every account",
                    "Add your bank accounts, cash, credit cards and wallets. Each entry you add takes money out of (or puts it into) one of them, so balances stay up to date without connecting to any bank.")
            }
            item {
                Text("Quick start", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
            }
            items(AccountKind.entries.size) { i ->
                val k = AccountKind.entries[i]
                val example = when (k) { AccountKind.BANK -> "Savings account"; AccountKind.CASH -> "Cash in wallet"; AccountKind.CARD -> "Credit card"; AccountKind.WALLET -> "Paytm / Amazon Pay" }
                KcCard(onClick = { editing = Account(name = example, emoji = k.emoji, kind = k) }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k.emoji, fontSize = 26.sp); Spacer(Modifier.width(12.dp))
                        Text("Add ${k.label.lowercase()}", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, modifier = Modifier.weight(1f))
                        Text("+", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted)
                    }
                }
            }
            return@SubScreen
        }

        item {
            KcCard {
                Text("Net balance", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                Text(Money.format(Balances.net(active, balances)), style = MaterialTheme.typography.headlineMedium, color = Kc.colors.text)
                val owed = Balances.cardsOwed(active, balances)
                if (owed > 0) Text("Includes ${Money.format(owed)} owed on credit cards", style = MaterialTheme.typography.bodySmall, color = Coral)
            }
        }
        if (unlinked > 0) item {
            val def = active.firstOrNull()
            KcCard {
                Text("$unlinked entries aren't linked to an account", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                Text("Entries added before accounts (or imported) don't change any balance. Link them all to your main account in one tap, or set each one in the entry.",
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                if (def != null) OutlinedButton(onClick = { vm.linkUnlinked(def) }, modifier = Modifier.fillMaxWidth()) { Text("Link all to ${def.emoji} ${def.name}") }
            }
        }
        items(accounts.size) { i ->
            val a = accounts[i]
            val bal = balances[a.id] ?: 0L
            val (inP, outP) = Balances.flow(a.id, data.allTxns, month)
            KcCard(onClick = { editing = a }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.emoji, fontSize = 28.sp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name + if (i == 0 && !a.archived) "  · default" else "", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                        Text(if (a.archived) "Archived" else "This month: in ${Money.compact(inP)} · out ${Money.compact(outP)}",
                            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (a.kind == AccountKind.CARD && bal < 0) {
                            Text(Money.format(-bal), style = MaterialTheme.typography.titleMedium, color = Coral)
                            Text("you owe", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                        } else Text(Money.format(bal), style = MaterialTheme.typography.titleMedium, color = if (bal < 0) Coral else Kc.colors.text)
                    }
                }
            }
        }
        item { BigButton("+ Add account") { editing = Account(name = "", emoji = "🏦", kind = AccountKind.BANK) } }
        item {
            Text("Balances are worked out on this phone from your entries; nothing connects to your bank. Paying a card bill or taking ATM cash? Add it as a Transfer from one account to another.",
                style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        }
    }

    editing?.let { a -> AccountDialog(a, isDefault = accounts.firstOrNull()?.id == a.id && a.id != 0L,
        onDismiss = { editing = null },
        onSave = { vm.saveAccount(it); editing = null },
        onDelete = { vm.deleteAccount(a); editing = null },
        onDefault = { vm.makeDefaultAccount(a); editing = null }) }
}

@Composable
private fun AccountDialog(a: Account, isDefault: Boolean, onDismiss: () -> Unit, onSave: (Account) -> Unit, onDelete: () -> Unit, onDefault: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(a.name) }
    var kind by rememberSaveable { mutableStateOf(a.kind) }
    var emoji by rememberSaveable { mutableStateOf(a.emoji) }
    // Cards are entered as "what you owe" (a positive number) and stored as a negative balance.
    var opening by rememberSaveable { mutableStateOf(if (a.openingPaise == 0L) "" else Money.format(kotlin.math.abs(a.openingPaise)).filter { it.isDigit() || it == '.' }) }
    var archived by rememberSaveable { mutableStateOf(a.archived) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amount = if (opening.isBlank()) 0L else Money.parse(opening)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (a.id == 0L) "Add account" else "Edit account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(AccountKind.entries.map { it.emoji + " " + it.label.substringBefore(' ') }, AccountKind.entries.indexOf(kind)) { i ->
                    kind = AccountKind.entries[i]; if (emoji in AccountKind.entries.map { it.emoji }) emoji = kind.emoji
                }
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name, e.g. HDFC savings") }, singleLine = true,
                    trailingIcon = { MicButton("Name this account") { name = spokenName(it, 30) } }, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("🏦", "💵", "💳", "📱", "🐷", "🏠", "💼", "👛", "🪙", "🏧")) { e ->
                        Pill(e, if (e == emoji) Haldi else Kc.colors.chip, Ink) { emoji = e }
                    }
                }
                OutlinedTextField(opening, { v -> opening = v.filter { it.isDigit() || it == '.' }.take(12) }, singleLine = true,
                    label = { Text(if (kind == AccountKind.CARD) "Amount you owe now" else "Balance right now") }, prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Text(if (a.id == 0L) "Entries you add from today change this balance." else "Changing this resets the starting point; entries stay.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                if (a.id != 0L) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isDefault && !archived) Pill("Make default", Kc.colors.chip, Kc.colors.text) { onDefault() }
                    Pill(if (archived) "Unarchive" else "Archive", Kc.colors.chip, Kc.colors.text) { archived = !archived }
                    Pill("Delete", Kc.colors.warnBg, Coral) { confirmDelete = true }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && amount != null, onClick = {
                val p = amount ?: 0L
                onSave(a.copy(name = name.trim(), kind = kind, emoji = emoji, openingPaise = if (kind == AccountKind.CARD) -p else p, archived = archived))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${a.name}?") },
        text = { Text("Its entries are kept, just not linked to any account. To keep its history in balances, use Archive instead.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Coral) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

// ============================== Trips & events ==============================

/** Tools → Trips & events: a budget for a trip, a wedding, a festival or a move, tracked by tag. */
@Composable
fun TripsScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val trips by vm.trips.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Trip?>(null) }
    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    SubScreen("Trips & events", onBack = { nav.popBackStack() }) {
        if (trips.isNotEmpty()) item {
            KcCard(onClick = { nav.navigate(com.kharchacheck.app.ui.nav.MemoriesRoute) }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📸", fontSize = 26.sp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Memories", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                        Text("Every trip with its photos, moments and what it cost", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted)
                }
            }
        }
        item {
            Text("Give a trip, wedding, festival or house move its own budget. While it's on, new entries are tagged to it automatically (you can untick it), and they still count in your monthly reports.",
                style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        }
        if (trips.isEmpty()) item {
            EmptyState("✈️", "No trips or events yet", "Planning Goa in December, a wedding, or Diwali shopping? Keep every ticket and booking, where you're going, who's coming and a diary of the best moments, all in one place.",
                "Add a trip or event") { editing = newTrip(today) }
        }
        items(trips.size) { i ->
            val t = trips[i]
            val spent = Trips.spent(t, data.allTxns)
            val count = data.allTxns.count { Trips.hasTag(it, t.tag) }
            val over = t.budgetPaise in 1 until spent
            KcCard(onClick = { nav.navigate(com.kharchacheck.app.ui.nav.TripRoute(t.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.emoji, fontSize = 30.sp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name + if (t.destination.isNotBlank() && !t.name.contains(t.destination, true)) " · ${t.destination}" else "",
                            style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                        val status = com.kharchacheck.app.domain.TripPlan.status(t, today)
                        Text("${t.start.format(fmt)} – ${t.end.format(fmt)} · $status · $count entries" + if (t.foreign) " · ${t.currency}" else "",
                            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                }
                if (t.budgetPaise > 0) {
                    ProgressBar((spent.toFloat() / t.budgetPaise).coerceIn(0f, 1f), if (over) Coral else Mint)
                    Text("${Money.format(spent)} of ${Money.format(t.budgetPaise)}" + if (over) " · ${Money.format(spent - t.budgetPaise)} over" else " · ${Money.format(t.budgetPaise - spent)} left",
                        style = MaterialTheme.typography.bodyMedium, color = if (over) Coral else Kc.colors.text)
                    Trips.perDayLeft(t, spent, today)?.let { Text("About ${Money.format(it)} a day for the days left", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted) }
                } else Text("Spent ${Money.format(spent)}", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
            }
        }
        if (trips.isNotEmpty()) item { BigButton("+ Add trip or event") { editing = newTrip(today) } }
    }

    editing?.let { t ->
        TripDialog(t, taken = trips.filter { it.id != t.id }.map { it.tag }.toSet(),
            onDismiss = { editing = null },
            onSave = { saved -> editing = null; vm.saveTrip(saved) { id -> if (saved.id == 0L) nav.navigate(com.kharchacheck.app.ui.nav.TripRoute(id)) } },
            onDelete = { vm.deleteTrip(t); editing = null })
    }
}

private fun newTrip(today: LocalDate) = Trip(name = "", emoji = "✈️", tag = "", budgetPaise = 0, start = today, end = today.plusDays(3))

@Composable
internal fun TripDialog(t: Trip, taken: Set<String>, onDismiss: () -> Unit, onSave: (Trip) -> Unit, onDelete: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(t.name) }
    var emoji by rememberSaveable { mutableStateOf(t.emoji) }
    var budget by rememberSaveable { mutableStateOf(if (t.budgetPaise > 0) (t.budgetPaise / 100).toString() else "") }
    var start by rememberSaveable { mutableStateOf(t.start.toEpochDay()) }
    var end by rememberSaveable { mutableStateOf(t.end.toEpochDay()) }
    var currency by rememberSaveable { mutableStateOf(t.currency) }
    var rate by rememberSaveable { mutableStateOf(if (t.foreign) "%.2f".format(Locale.ENGLISH, t.ratePaise / 100.0) else "") }
    var destination by rememberSaveable { mutableStateOf(t.destination) }
    var notes by rememberSaveable { mutableStateOf(t.notes) }
    var pickDates by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    val foreign = currency.length == 3 && currency != "INR"
    val ratePaise = Money.parse(rate)
    val ok = name.isNotBlank() && (budget.isBlank() || Money.parse(budget) != null) && (!foreign || (ratePaise ?: 0) > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (t.id == 0L) "New trip or event" else "Edit ${t.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name, e.g. Goa trip, Riya's wedding") }, singleLine = true,
                    trailingIcon = { MicButton("Name the trip or event") { name = spokenName(it, 30) } }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(destination, { destination = it.take(40) }, label = { Text("Where to? e.g. Goa, Shimla & Manali") }, singleLine = true,
                    trailingIcon = { MicButton("Where are you going?") { destination = spokenName(it, 40) } }, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("✈️", "🏖️", "🏔️", "🚗", "💍", "🎉", "🪔", "🏠", "🎓", "🏕️")) { e ->
                        Pill(e, if (e == emoji) Haldi else Kc.colors.chip, Ink) { emoji = e }
                    }
                }
                OutlinedButton(onClick = { pickDates = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("📅 ${LocalDate.ofEpochDay(start).format(fmt)} – ${LocalDate.ofEpochDay(end).format(fmt)}")
                }
                OutlinedTextField(budget, { v -> budget = v.filter { it.isDigit() }.take(9) }, label = { Text("Budget (optional)") }, prefix = { Text("₹ ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text("Abroad? Set the currency to type amounts in it.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("INR", "USD", "EUR", "GBP", "AED", "THB", "SGD", "LKR", "NPR")) { c ->
                        Pill(c, if (c == currency) Haldi else Kc.colors.chip, Ink) { currency = c }
                    }
                }
                if (foreign) OutlinedTextField(rate, { v -> rate = v.filter { it.isDigit() || it == '.' }.take(8) }, singleLine = true,
                    label = { Text("₹ for 1 $currency (your rate)") }, prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("Notes (hotel address, packing list…)") }, minLines = 2,
                    trailingIcon = { MicButton("Add a note") { notes = com.kharchacheck.app.ui.components.appendSpoken(notes, it, 500) } }, modifier = Modifier.fillMaxWidth())
                if (t.id != 0L) Pill("Delete trip and its details", Kc.colors.warnBg, Coral) { onDelete() }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onSave(t.copy(
                    name = name.trim(), emoji = emoji, tag = t.tag.ifBlank { Trips.tagFor(name, taken) },
                    budgetPaise = Money.parse(budget) ?: 0L, start = LocalDate.ofEpochDay(start), end = LocalDate.ofEpochDay(end),
                    currency = if (foreign) currency else "INR", ratePaise = if (foreign) ratePaise ?: 100 else 100,
                    destination = destination.trim(), notes = notes.trim(),
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickDates) {
        val utc = { day: Long -> LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val rs = rememberDateRangePickerState(initialSelectedStartDateMillis = utc(start), initialSelectedEndDateMillis = utc(end))
        DatePickerDialog(
            onDismissRequest = { pickDates = false },
            confirmButton = {
                TextButton(enabled = rs.selectedStartDateMillis != null, onClick = {
                    val s = Instant.ofEpochMilli(rs.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    val e = rs.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() } ?: s
                    start = s; end = maxOf(s, e); pickDates = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDates = false }) { Text("Cancel") } },
        ) { DateRangePicker(state = rs, modifier = Modifier.weight(1f)) }
    }
}
