package com.kharchacheck.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.Bill
import com.kharchacheck.app.domain.BillStatus
import com.kharchacheck.app.domain.Bills
import com.kharchacheck.app.domain.Due
import com.kharchacheck.app.domain.DueDirection
import com.kharchacheck.app.domain.Dues
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.Segmented
import com.kharchacheck.app.ui.components.StatBlock
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

// ================================================================ Split & dues

@Composable
fun DuesScreen(vm: AppViewModel, nav: NavHostController) {
    val dues by vm.dues.collectAsStateWithLifecycle()
    val p = LocalPlatform.current
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingDue by remember { mutableStateOf<Due?>(null) }
    var showSettled by rememberSaveable { mutableStateOf(false) }
    val balances = Dues.balances(dues)
    val open = dues.filter { !it.settled }
    val settled = dues.filter { it.settled }

    SubScreen("Split & dues", onBack = { nav.popBackStack() }) {
        item {
            KcCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBlock("To collect", Money.format(Dues.toCollect(dues)), Modifier.weight(1f), Kc.colors.good)
                    StatBlock("To pay", Money.format(Dues.toPay(dues)), Modifier.weight(1f), Coral)
                }
                Text("Tip: tick “🤝 Split this bill” when adding an expense, and the others' shares appear here automatically.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                BigButton("+ Add who owes what") { adding = true }
            }
        }
        if (balances.isEmpty()) item { EmptyState("🤝", "All square", "Nobody owes anybody. Split a bill or add a due to start.") }
        if (balances.isNotEmpty()) item { Text("By person", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text) }
        items(balances, key = { "b-" + it.person.lowercase() }) { b ->
            val theyOwe = b.netPaise > 0
            KcCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.person.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = Ink,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Haldi).padding(horizontal = 14.dp, vertical = 6.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.person, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                        Text(if (theyOwe) "owes you" else "you owe", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                    Text(Money.format(kotlin.math.abs(b.netPaise), true), style = MaterialTheme.typography.titleLarge, color = if (theyOwe) Kc.colors.good else Coral)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("✓ Settle up", Kc.colors.goodBg, Kc.colors.good) { vm.settlePerson(b.person) }
                    if (theyOwe) Pill("💬 Remind", Kc.colors.chip, Kc.colors.text) {
                        val items = open.filter { it.person.trim().equals(b.person, true) && it.direction == DueDirection.THEY_OWE }
                            .joinToString("\n") { "• ${it.note.ifBlank { "Shared expense" }} (${it.date.format(dFmt)}): ${Money.format(it.amountPaise, true)}" }
                        p.shareText("Hi ${b.person}! Gentle reminder: ${Money.format(b.netPaise, true)} for\n$items\n\nUPI me whenever you can 🙂")
                    }
                }
            }
        }
        if (open.isNotEmpty()) item { Text("Open items", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text, modifier = Modifier.padding(top = 6.dp)) }
        items(open, key = { "o-" + it.id }) { d -> DueRow(d, onSettle = { vm.settleDue(d) }, onDelete = { vm.deleteDue(d) }, onEdit = { editingDue = d }) }
        if (settled.isNotEmpty()) item {
            Text(if (showSettled) "Hide settled" else "Show settled (${settled.size})", style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted,
                modifier = Modifier.clickable { showSettled = !showSettled }.padding(vertical = 8.dp))
        }
        if (showSettled) items(settled.take(30), key = { "s-" + it.id }) { d -> DueRow(d, onSettle = null, onDelete = { vm.deleteDue(d) }, onEdit = { editingDue = d }) }
    }
    if (adding) DueDialog(null, onDismiss = { adding = false }) { vm.saveDue(it); adding = false }
    editingDue?.let { e -> DueDialog(e, onDismiss = { editingDue = null }) { vm.saveDue(it); editingDue = null } }
}

@Composable
private fun DueRow(d: Due, onSettle: (() -> Unit)?, onDelete: () -> Unit, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Kc.colors.card).clickable(onClick = onEdit).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${d.person} · ${if (d.direction == DueDirection.THEY_OWE) "owes you" else "you owe"}", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
            Text(listOf(d.note, d.date.format(dFmt)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, maxLines = 2)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(d.amountPaise, true), style = MaterialTheme.typography.titleSmall, color = if (d.settled) Kc.colors.muted else Kc.colors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (onSettle != null) Pill("Settle", Kc.colors.goodBg, Kc.colors.good, onClick = onSettle) else Pill("Settled", Kc.colors.chip, Kc.colors.muted)
                Pill("✕", Kc.colors.chip, Kc.colors.muted, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun DueDialog(existing: Due?, onDismiss: () -> Unit, onSave: (Due) -> Unit) {
    var person by rememberSaveable { mutableStateOf(existing?.person ?: "") }
    var amount by rememberSaveable { mutableStateOf(existing?.amountPaise?.let { if (it % 100 == 0L) (it / 100).toString() else "%d.%02d".format(it / 100, it % 100) } ?: "") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var dir by rememberSaveable { mutableStateOf(if (existing?.direction == DueDirection.I_OWE) 1 else 0) }
    val paise = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add a due" else "Edit due") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(listOf("They owe me", "I owe them"), dir) { dir = it }
                OutlinedTextField(person, { person = it.take(30) }, label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { v -> amount = v.filter { it.isDigit() || it == '.' }.take(10) }, label = { Text("Amount") }, prefix = { Text("₹ ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(60) }, label = { Text("For what (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = person.isNotBlank() && paise != null, onClick = {
            val base = existing ?: Due(person = "", amountPaise = 0, direction = DueDirection.THEY_OWE)
            onSave(base.copy(person = person.trim(), amountPaise = paise ?: 0, direction = if (dir == 0) DueDirection.THEY_OWE else DueDirection.I_OWE, note = note.trim()))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ================================================================ Bills & reminders

fun billStatusText(s: BillStatus, paidThisCycle: Boolean): String = when {
    paidThisCycle -> "Paid ✓ · next due ${s.dueDate.format(dFmt)}"
    s.overdue -> "Overdue by ${-s.daysLeft} day${if (s.daysLeft == -1L) "" else "s"}"
    s.daysLeft == 0L -> "Due today"
    s.daysLeft == 1L -> "Due tomorrow"
    else -> "Due ${s.dueDate.format(dFmt)} · in ${s.daysLeft} days"
}

@Composable
fun BillsScreen(vm: AppViewModel, nav: NavHostController) {
    val bills by vm.bills.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Bill?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val statuses = bills.filter { it.active }.map { Bills.status(it, today) }.sortedBy { it.dueDate }

    SubScreen("Bills & due dates", onBack = { nav.popBackStack() }) {
        item {
            KcCard {
                Text("Never pay a late fee again. Add your credit cards, rent, electricity, insurance and EMIs; you'll get a reminder before each due date.",
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                val monthly = bills.filter { it.active }.sumOf { it.amountPaise }
                if (monthly > 0) Text("Known bills add up to about ${Money.format(monthly)} a month.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                BigButton("+ Add a bill") { adding = true }
            }
        }
        if (statuses.isEmpty()) item { EmptyState("🧾", "No bills yet", "Credit card due dates are the best place to start: card late fees and interest are expensive.") }
        items(statuses, key = { it.bill.id }) { s ->
            val paidThisCycle = s.bill.paidMonth != null && s.bill.paidMonth >= YearMonth.from(today)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Kc.colors.card).clickable { editing = s.bill }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.bill.emoji, fontSize = 28.sp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.bill.name, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                    Text(billStatusText(s, paidThisCycle), style = MaterialTheme.typography.bodySmall,
                        color = when { paidThisCycle -> Kc.colors.good; s.overdue || s.daysLeft <= 1 -> Coral; else -> Kc.colors.muted })
                    if (s.bill.amountPaise > 0) Text(Money.format(s.bill.amountPaise), style = MaterialTheme.typography.labelLarge, color = Kc.colors.text)
                }
                if (!paidThisCycle) Pill("Mark paid", Haldi, Ink) { vm.markBillPaid(s.bill, YearMonth.from(s.dueDate)) }
            }
        }
    }
    if (adding) BillDialog(null, onDismiss = { adding = false }, onDelete = {}) { vm.saveBill(it); adding = false }
    editing?.let { b -> BillDialog(b, onDismiss = { editing = null }, onDelete = { vm.deleteBill(b); editing = null }) { vm.saveBill(it); editing = null } }
}

@Composable
private fun BillDialog(existing: Bill?, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Bill) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(existing?.emoji ?: "💳") }
    var day by rememberSaveable { mutableStateOf(existing?.dueDay?.toString() ?: "") }
    var amount by rememberSaveable { mutableStateOf(existing?.amountPaise?.takeIf { it > 0 }?.let { (it / 100).toString() } ?: "") }
    var remind by rememberSaveable { mutableStateOf(existing?.remindDaysBefore ?: 3) }
    val dueDay = day.toIntOrNull()?.takeIf { it in 1..31 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New bill" else "Edit bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("💳", "🏠", "💡", "📶", "🛡️", "🏦", "📺", "🚗").forEach { e ->
                        Text(e, fontSize = 20.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(if (e == emoji) Haldi else Kc.colors.chip)
                            .clickable { emoji = e }.padding(6.dp))
                    }
                }
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name, e.g. HDFC credit card") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(day, { v -> day = v.filter(Char::isDigit).take(2) }, label = { Text("Due day") }, singleLine = true, isError = day.isNotEmpty() && dueDay == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(amount, { v -> amount = v.filter(Char::isDigit).take(9) }, label = { Text("Amount") }, prefix = { Text("₹ ") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1.3f))
                }
                Text("Remind me", style = MaterialTheme.typography.labelLarge, color = Kc.colors.text)
                Segmented(listOf("1 day", "3 days", "5 days", "7 days"), listOf(1, 3, 5, 7).indexOf(remind).coerceAtLeast(0)) { remind = listOf(1, 3, 5, 7)[it] }
                Text("before, plus on the due day.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                if (existing != null) TextButton(onClick = onDelete) { Text("Delete bill", color = Coral) }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && dueDay != null, onClick = {
            onSave((existing ?: Bill(name = "", dueDay = 1)).copy(name = name.trim(), emoji = emoji, dueDay = dueDay ?: 1,
                amountPaise = (amount.toLongOrNull() ?: 0) * 100, remindDaysBefore = remind))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
