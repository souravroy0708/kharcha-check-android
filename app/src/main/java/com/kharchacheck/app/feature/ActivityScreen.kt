package com.kharchacheck.app.feature

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

private enum class Filter(val label: String) { ALL("All"), NEED("Needs"), WANT("Wants"), LEAK("Leaks"), INCOME("Income"), INVEST("Invest"), TRANSFER("Transfers"), REGRET("😬 Regret"), REVIEW("To review") }

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

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(Ink)
                .statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activity", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.weight(1f))
                Pill("📄 Import", Haldi, Ink, Modifier.padding(end = 4.dp)) { nav.navigate(StatementsRoute(ym.toString())) }
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
            item {
                Text("${list.size} entries" + if (total > 0) " · ${Money.format(total)} spent" else "",
                    style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted, modifier = Modifier.padding(start = 4.dp))
            }
            list.groupBy { it.date }.forEach { (date, dayList) ->
                item(key = "d-$date") { DayGroup(date, dayList, data.catMap, attached) { nav.navigate(EditRoute(id = it.id)) } }
            }
        }
    }
}

@Composable
private fun DayGroup(date: LocalDate, list: List<Txn>, cats: Map<Long, com.kharchacheck.app.core.model.Category>, attached: Set<Long>, onOpen: (Txn) -> Unit) {
    val today = LocalDate.now()
    val label = when (date) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)) }
    val spent = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountPaise }
    KcCard(padding = 10.dp) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, modifier = Modifier.weight(1f))
            if (spent > 0) Text("−${Money.format(spent)}", style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted)
        }
        list.forEach { t -> TxnRow(t, cats[t.categoryId], attached = t.id in attached) { onOpen(t) } }
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

