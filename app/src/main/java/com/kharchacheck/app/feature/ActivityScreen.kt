package com.kharchacheck.app.feature

import com.kharchacheck.app.ui.components.CalendarHeat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.ui.components.MicButton
import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.ReviewState
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.components.TxnRow
import com.kharchacheck.app.ui.nav.EditRoute
import com.kharchacheck.app.ui.nav.ExportRoute
import com.kharchacheck.app.ui.nav.StatementsRoute
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Ink2
import com.kharchacheck.app.ui.theme.InkLine
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.MutedOnInk
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Filter(val label: String) { ALL("All"), IMPORTED("📄 Imported"), NEED("Needs"), WANT("Wants"), LEAK("Leaks"), INCOME("Income"), INVEST("Invest"), TRANSFER("Transfers"), REGRET("😬 Regret"), REVIEW("To review") }

@Composable
fun ActivityScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(month)

    val list = remember(data.txns, query, filter, month) {
        val q = query.trim().lowercase(Locale.ENGLISH)
        data.txns.filter { t ->
            val c = data.catMap[t.categoryId]
            (q.isNotEmpty() || t.month == ym) &&
                (q.isEmpty() || t.payee.lowercase().contains(q) || t.note.lowercase().contains(q) || t.tags.lowercase().contains(q) ||
                    (c?.name?.lowercase()?.contains(q) == true) || Money.format(t.amountPaise).contains(q)) &&
                when (filter) {
                    Filter.ALL -> true
                    Filter.IMPORTED -> t.source == com.kharchacheck.app.core.model.Source.STATEMENT
                    Filter.NEED -> c?.bucket == Bucket.NEED && t.type == TxType.EXPENSE
                    Filter.WANT -> c?.bucket == Bucket.WANT && t.type == TxType.EXPENSE
                    Filter.LEAK -> c?.bucket == Bucket.LEAK && t.type == TxType.EXPENSE
                    Filter.INCOME -> t.type == TxType.INCOME
                    Filter.INVEST -> t.type == TxType.INVEST
                    Filter.TRANSFER -> t.type == TxType.TRANSFER
                    Filter.REGRET -> t.worth == com.kharchacheck.app.core.model.Worth.REGRET
                    Filter.REVIEW -> t.review == ReviewState.NEEDS_REVIEW
                }
        }
    }
    val total = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountPaise }
    val files by vm.attachments.collectAsStateWithLifecycle()
    val attached = remember(files) { files.mapNotNull { it.txnId }.toSet() }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    // 📅 Calendar view: the month at a glance, shaded by spend; tap a day to see just that day.
    var calendar by rememberSaveable { mutableStateOf(false) }
    var pickedDay by rememberSaveable(month) { mutableStateOf<Int?>(null) }
    var moving by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val selecting = selectedIds.isNotEmpty()
    fun toggle(id: Long) { selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(Ink)
                .statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selecting) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${selectedIds.size} selected", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                Pill("All", Ink2, Color.White) { selectedIds = list.map { it.id }.toSet() }
                Pill("🏷️", Haldi, Ink) { moving = true }
                val chosenHidden = selectedIds.isNotEmpty() && selectedIds.all { it in data.hiddenIds }
                Pill(if (chosenHidden) "👁 Unhide" else "🙈 Hide", Ink2, Color.White) {
                    vm.setHidden(data.allTxns.filter { it.id in selectedIds }, !chosenHidden); selectedIds = emptySet()
                }
                Pill("🗑️", Ink2, com.kharchacheck.app.ui.theme.Coral) { confirmDelete = true }
                Pill("✕", Ink2, Color.White) { selectedIds = emptySet() }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activity", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.weight(1f))
                Pill("📄 Import", Haldi, Ink, Modifier.padding(end = 4.dp)) { nav.navigate(StatementsRoute(ym.toString())) }
                Pill("⬇ Export", Ink2, Color.White, Modifier.padding(end = 4.dp)) { nav.navigate(ExportRoute) }
                if (query.isBlank()) Pill("📅", if (calendar) Haldi else Ink2, if (calendar) Ink else Color.White, Modifier.padding(end = 4.dp)) {
                    calendar = !calendar; if (!calendar) pickedDay = null
                }
                if (query.isBlank()) {
                    IconButton(onClick = { month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", tint = Color.White) }
                    Text(ym.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    IconButton(onClick = { if (ym < YearMonth.now()) month = ym.plusMonths(1).toString() }, enabled = ym < YearMonth.now()) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", tint = if (ym < YearMonth.now()) Color.White else InkLine)
                    }
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search payee, note, tag, category, amount", color = MutedOnInk) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MutedOnInk) },
                trailingIcon = { MicButton("Search for a payee, note or category", tint = MutedOnInk) { query = it.trim().trimEnd('.', '?') } },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Ink2, unfocusedContainerColor = Ink2, focusedBorderColor = Haldi, unfocusedBorderColor = InkLine,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Haldi,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filter.entries) { f ->
                    Pill(f.label, if (f == filter) Haldi else Ink2, if (f == filter) Ink else MutedOnInk) { filter = f }
                }
            }
        }

        if (list.isEmpty()) {
            EmptyState("🧾", if (query.isNotBlank()) "No matches" else "Nothing here yet",
                if (query.isNotBlank()) "Try a different word or amount." else "Entries you add for this month will show up here.")
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (calendar && query.isBlank()) item(key = "calendar") {
                val daily = remember(list, month) {
                    MutableList(ym.lengthOfMonth()) { 0L }.also { d -> list.filter { it.type == TxType.EXPENSE && YearMonth.from(it.date) == ym }.forEach { d[it.date.dayOfMonth - 1] += it.amountPaise } }
                }
                KcCard(padding = 12.dp) {
                    CalendarHeat(ym, daily, pickedDay) { i -> pickedDay = if (pickedDay == i) null else i }
                    Text(pickedDay?.let { "${ym.atDay(it + 1).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))}: ${Money.format(daily[it])} spent · tap again to see all" }
                        ?: "Darker days = more spent. Tap a day to see only that day.",
                        style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                }
            }
            val shown = if (calendar && pickedDay != null && query.isBlank()) list.filter { it.date.dayOfMonth == pickedDay!! + 1 && YearMonth.from(it.date) == ym } else list
            item {
                Text("${shown.size} entries" + (if (total > 0 && shown === list) " · ${Money.format(total)} spent" else "") + if (!selecting) " · long-press to select" else "",
                    style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted, modifier = Modifier.padding(start = 4.dp))
            }
            shown.groupBy { it.date }.forEach { (date, dayList) ->
                item(key = "d-$date") {
                    DayGroup(date, dayList, data.catMap, attached, selectedIds, data.hiddenIds, onLong = { toggle(it.id) }) { t ->
                        if (selecting) toggle(t.id) else nav.navigate(EditRoute(id = t.id))
                    }
                }
            }
        }
    }
    BulkDialogs(vm, data, selectedIds, moving, confirmDelete,
        onMoved = { moving = false; selectedIds = emptySet() }, onDeleted = { confirmDelete = false; selectedIds = emptySet() },
        onDismiss = { moving = false; confirmDelete = false })
}

@Composable
private fun BulkDialogs(vm: AppViewModel, data: com.kharchacheck.app.ui.AppData, ids: Set<Long>, moving: Boolean, confirmDelete: Boolean,
                        onMoved: () -> Unit, onDeleted: () -> Unit, onDismiss: () -> Unit) {
    val chosen = data.txns.filter { it.id in ids }
    if (moving) CategorySheet(data.activeCategories, 0, onPick = { c -> vm.recategorize(chosen, c); onMoved() }, onNew = onDismiss, onDismiss = onDismiss)
    if (confirmDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${chosen.size} entries?") },
        text = { Text("You can undo right after.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.deleteMany(chosen); onDeleted() }) { Text("Delete", color = com.kharchacheck.app.ui.theme.Coral) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}

@Composable
private fun DayGroup(date: LocalDate, list: List<Txn>, cats: Map<Long, com.kharchacheck.app.core.model.Category>, attached: Set<Long>,
                     selected: Set<Long>, hidden: Set<Long>, onLong: (Txn) -> Unit, onOpen: (Txn) -> Unit) {
    val today = LocalDate.now()
    val label = when (date) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)) }
    val spent = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountPaise }
    KcCard(padding = 10.dp) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, modifier = Modifier.weight(1f))
            if (spent > 0) Text("−${Money.format(spent)}", style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted)
        }
        list.forEach { t -> TxnRow(t, cats[t.categoryId], attached = t.id in attached, selected = t.id in selected, hidden = t.id in hidden, onLongClick = { onLong(t) }) { onOpen(t) } }
    }
}

@Composable
fun InboxScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    SubScreen("Review inbox", onBack = { nav.popBackStack() }, actions = {
        if (data.inbox.isNotEmpty()) Pill("Confirm all", Haldi, Ink, Modifier.padding(end = 8.dp)) { vm.confirmAll(data.inbox.map { it.id }) }
    }) {
        item {
            Text("These came from bank messages or shared text. Tap any entry to change amount, category, payee, date, mode or notes. " +
                "Confirmed entries teach the app: tick “Always put … in …” while editing.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        }
        if (data.inbox.isEmpty()) item { EmptyState("✅", "All caught up", "Nothing waiting for review.") }
        items(data.inbox, key = { it.id }) { t ->
            KcCard(padding = 10.dp) {
                TxnRow(t, data.catMap[t.categoryId]) { nav.navigate(EditRoute(id = t.id)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    Pill("✓ Looks right", Kc.colors.goodBg, Kc.colors.good) { vm.confirmAll(listOf(t.id)) }
                    Pill("Edit", Kc.colors.chip, Kc.colors.text) { nav.navigate(EditRoute(id = t.id)) }
                    Spacer(Modifier.weight(1f))
                    Pill("Not mine", Kc.colors.warnBg, com.kharchacheck.app.ui.theme.Coral) { vm.delete(t) }
                }
            }
        }
    }
}

