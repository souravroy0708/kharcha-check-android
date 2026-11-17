@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kharchacheck.app.feature

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.kharchacheck.app.container
import com.kharchacheck.app.core.model.Attachment
import com.kharchacheck.app.core.model.AttachmentKind
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.Source
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.Dedupe
import com.kharchacheck.app.domain.read.Classifier
import com.kharchacheck.app.domain.read.StatementParser
import com.kharchacheck.app.read.AutoReader
import com.kharchacheck.app.ui.AppData
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.CategoryIcon
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.nav.ImportRoute
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.MutedOnInk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row found in a statement, turned into an editable draft entry. */
data class ImportItem(
    val key: Int, val txn: Txn, val selected: Boolean, val verdict: Dedupe.Verdict, val matchedWith: String?,
    val sure: Boolean, val narration: String,
)

sealed interface ImportPhase {
    data object Starting : ImportPhase
    data class Reading(val page: Int, val total: Int) : ImportPhase
    data class Password(val wrong: Boolean) : ImportPhase
    data class Review(val items: List<ImportItem>, val creditCard: Boolean, val seenBefore: LocalDate?, val card: StatementParser.CardSummary? = null) : ImportPhase
    data class Failed(val message: String) : ImportPhase
}

class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val _phase = MutableStateFlow<ImportPhase>(ImportPhase.Starting)
    val phase = _phase.asStateFlow()
    private var job: Job? = null
    private var started = false
    private var fileSha: String? = null

    fun restart(a: Attachment, data: AppData) { started = false; start(a, data) }

    fun start(a: Attachment, data: AppData, password: String? = null) {
        if (started && password == null) return
        started = true
        job?.cancel()
        job = viewModelScope.launch {
            _phase.value = ImportPhase.Reading(0, 0)
            val bytes = runCatching { c.files.bytes(a) }.getOrNull() ?: run { _phase.value = ImportPhase.Failed("The file couldn't be opened."); return@launch }
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            fileSha = sha
            val seen = c.settings.current().importedFiles.firstOrNull { it.startsWith("$sha|") }
                ?.substringAfter('|')?.toLongOrNull()?.let(LocalDate::ofEpochDay)
            when (val out = c.reader.statement(a, bytes, password) { p, t -> _phase.value = ImportPhase.Reading(p, t) }) {
                is AutoReader.Outcome.NeedsPassword -> _phase.value = ImportPhase.Password(out.wrong)
                is AutoReader.Outcome.Failed -> _phase.value = ImportPhase.Failed(out.message)
                is AutoReader.Outcome.Rows -> _phase.value =
                    if (out.result.rows.isEmpty()) ImportPhase.Failed("No transactions were found. If your bank offers a CSV or Excel download, attach that instead; it's the most reliable.")
                    else ImportPhase.Review(build(out.result, data), out.result.creditCard, seen, out.result.card)
            }
        }
    }

    private suspend fun build(res: StatementParser.Result, d: AppData): List<ImportItem> {
        val byKey = d.activeCategories.associateBy { it.key }
        val rules = c.finance.rulesList()
        val zone = ZoneId.systemDefault()
        val drafts = res.rows.map { r ->
            val cls = Classifier.classify(r.description, r.credit, res.creditCard)
            val lowered = (cls.payee + " " + r.description).lowercase(Locale.ENGLISH)
            val ruled = rules.firstOrNull { lowered.contains(it.pattern) }?.let { rule -> d.catMap[rule.categoryId]?.takeIf { !it.archived } }
            val cat = ruled ?: byKey[cls.categoryKey] ?: byKey.getValue("uncategorised")
            val mode = when {
                res.creditCard -> PayMode.CARD
                Regex("upi", RegexOption.IGNORE_CASE).containsMatchIn(r.description) -> PayMode.UPI
                Regex("""\batm\b|cash""", RegexOption.IGNORE_CASE).containsMatchIn(r.description) -> PayMode.CASH
                Regex("neft|imps|rtgs|nach|ach", RegexOption.IGNORE_CASE).containsMatchIn(r.description) -> PayMode.NET_BANKING
                Regex("""\bpos\b|card""", RegexOption.IGNORE_CASE).containsMatchIn(r.description) -> PayMode.CARD
                else -> PayMode.OTHER
            }
            val ref = r.ref.ifBlank { Regex("""\b(\d{12})\b""").find(r.description)?.groupValues?.get(1).orEmpty() }
            Triple(r, Txn(
                amountPaise = r.amountPaise, type = cat.bucket.type, categoryId = cat.id,
                epochMillis = r.date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), zoneId = zone.id,
                mode = mode, payee = cls.payee, note = r.description.take(160), source = Source.STATEMENT, refNo = ref,
            ), Unit)
        }
        val matches = Dedupe.check(d.allTxns, drafts.map { it.second })  // includes hidden entries
        return drafts.mapIndexed { i, (r, t, _) ->
            val m = matches[i]
            ImportItem(i, t, selected = m.verdict == Dedupe.Verdict.NEW && t.amountPaise > 0, verdict = m.verdict,
                matchedWith = m.existing?.let { e -> if (d.isHidden(e) && !d.revealed) "a hidden entry" else "${e.payee.ifBlank { d.catMap[e.categoryId]?.name ?: "entry" }} on ${e.date.format(dayFmt)}" },
                sure = r.sure, narration = r.description)
        }
    }

    /** Rows without a confirmed amount can't be ticked until the user enters one. */
    fun toggle(key: Int) = update { it.map { x -> if (x.key == key && x.txn.amountPaise > 0) x.copy(selected = !x.selected) else x } }
    fun setAll(on: Boolean, includeDuplicates: Boolean) = update {
        it.map { x -> if ((x.verdict == Dedupe.Verdict.DUPLICATE && !includeDuplicates) || x.txn.amountPaise == 0L) x.copy(selected = false) else x.copy(selected = on) }
    }
    fun edit(key: Int, payee: String, amountPaise: Long, date: LocalDate, note: String) = update {
        it.map { x ->
            if (x.key != key) x else {
                val zone = ZoneId.systemDefault()
                x.copy(txn = x.txn.copy(payee = payee.trim(), amountPaise = amountPaise, note = note.trim(),
                    epochMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()), sure = true, selected = true)
            }
        }
    }
    fun setCategory(key: Int, cat: Category) = update { it.map { x -> if (x.key == key) x.copy(txn = x.txn.copy(categoryId = cat.id, type = cat.bucket.type)) else x } }

    private fun update(f: (List<ImportItem>) -> List<ImportItem>) {
        val p = _phase.value as? ImportPhase.Review ?: return
        _phase.value = p.copy(items = f(p.items))
    }

    fun save(asDrafts: Boolean, onDone: (List<Long>) -> Unit) = viewModelScope.launch {
        val p = _phase.value as? ImportPhase.Review ?: return@launch
        val ids = c.finance.importAll(p.items.filter { it.selected && it.txn.amountPaise > 0 }.map { it.txn }, asDrafts)
        fileSha?.let { c.settings.rememberImportedFile(it) }
        onDone(ids)
    }

    fun readingReport(): String? = c.reader.lastReport

    override fun onCleared() { job?.cancel() }
}

private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@Composable
fun ImportScreen(vm: AppViewModel, nav: NavHostController, route: ImportRoute) {
    val d by vm.data.collectAsStateWithLifecycle()
    val files by vm.attachments.collectAsStateWithLifecycle()
    val data = d ?: return
    val a = files.firstOrNull { it.id == route.attachmentId }
    val ivm: ImportViewModel = viewModel()
    val phase by ivm.phase.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<ImportItem?>(null) }
    var editing by remember { mutableStateOf<ImportItem?>(null) }
    var reportAsk by remember { mutableStateOf(false) }
    val platform = com.kharchacheck.app.ui.LocalPlatform.current

    LaunchedEffect(a?.id) { if (a != null) ivm.start(a, data) }

    Column(Modifier.fillMaxSize().background(Kc.colors.paper)) {
        Row(Modifier.fillMaxWidth().background(Ink).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("✨ Read statement", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(a?.label?.ifBlank { a.originalName.ifBlank { a.kind.label } } ?: "", style = MaterialTheme.typography.labelSmall, color = MutedOnInk, maxLines = 1)
            }
        }
        when (val p = phase) {
            ImportPhase.Starting, is ImportPhase.Reading -> Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val r = p as? ImportPhase.Reading
                Text(if (r != null && r.total > 1) "Reading page ${r.page} of ${r.total}…" else "Reading your statement…", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text)
                if (r != null && r.total > 0) LinearProgressIndicator(progress = { r.page.toFloat() / r.total }, modifier = Modifier.fillMaxWidth(), color = Haldi)
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Haldi)
                Text("Done on your phone. A one-year statement usually takes under a minute. Each row is checked against the running balance.",
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
            }
            is ImportPhase.Password -> PasswordStep(p) { pw -> if (a != null) ivm.start(a, data, pw) }
            is ImportPhase.Failed -> {
                if (a != null) KindSwitch(a) { k -> vm.setStatementKind(a, k); ivm.restart(a.copy(kind = k), data) }
                EmptyState("🧐", "Couldn't read this one", p.message, "Back") { nav.popBackStack() }
                if (a?.isPdf == true) TextButton(onClick = { reportAsk = true }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Send a reading report so this bank can be supported") }
            }
            is ImportPhase.Review -> {
                if (a != null) KindSwitch(a) { k -> vm.setStatementKind(a, k); ivm.restart(a.copy(kind = k), data) }
                if (a?.isPdf == true) Text("Amounts look wrong? Send a reading report", style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clickable { reportAsk = true })
                p.card?.let { cs -> val due = cs.dueDate; if (due != null && !due.isBefore(LocalDate.now().minusDays(1))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Kc.colors.warnBg).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("💳 Card bill: ${cs.totalDuePaise?.let { Money.format(it) } ?: "amount"} due ${due.format(dayFmt)}", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                            Text("Pay the total (not just the minimum${cs.minDuePaise?.let { " of " + Money.format(it) } ?: ""}) to avoid ~40% yearly interest.",
                                style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                        }
                        Pill("🔔 Remind me", Haldi, Ink) { vm.addCardBill(a?.label?.ifBlank { null } ?: "Credit card", due, cs.totalDuePaise) }
                    }
                } }
                Review(p, data, ivm, onPick = { picking = it }, onEdit = { editing = it }) { ids, drafts ->
                    if (ids.isEmpty()) vm.say("Nothing added")
                    else vm.confirmImported(ids, if (drafts) "${ids.size} drafts added to Review" else "Added ${ids.size} entries from your statement ✓")
                    nav.popBackStack()
                }
            }
        }
    }

    if (reportAsk) AlertDialog(
        onDismissRequest = { reportAsk = false },
        title = { Text("Send a reading report?") },
        text = { Text("Creates a small text file showing how the app read this statement's layout: where each word and amount sits, the columns it found, and the amounts it chose. " +
            "Names and account / reference numbers are replaced with x and 9. Dates and amounts are kept, because they're needed to fix the reader. You can open the file and check it before sending.") },
        confirmButton = { TextButton(onClick = {
            reportAsk = false
            val r = ivm.readingReport()
            if (r == null) vm.say("Read the statement first, then send the report.")
            else platform.shareFile(r.toByteArray(Charsets.UTF_8), "text/plain", "kharcha-reading-report.txt")
        }) { Text("Create report") } },
        dismissButton = { TextButton(onClick = { reportAsk = false }) { Text("Cancel") } },
    )
    editing?.let { item -> RowEditDialog(item, onDismiss = { editing = null }) { payee, amt, date, note -> ivm.edit(item.key, payee, amt, date, note); editing = null } }
    picking?.let { item ->
        CategorySheet(data.activeCategories, item.txn.categoryId,
            onPick = { ivm.setCategory(item.key, it); picking = null }, onNew = { picking = null }, onDismiss = { picking = null })
    }
}

@Composable
private fun PasswordStep(p: ImportPhase.Password, onUnlock: (String) -> Unit) {
    var pw by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🔒 This statement is password-protected", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text)
        Text("Most banks use something like your date of birth (DDMMYYYY), or part of your name with your date of birth, or customer ID. It's printed in the email the statement came with. " +
            "The password is used once on this phone to open the file and is never stored.",
            style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        OutlinedTextField(pw, { pw = it }, label = { Text("PDF password") }, singleLine = true, isError = p.wrong,
            supportingText = { if (p.wrong) Text("That password didn't work. Try again.") },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth())
        BigButton("Unlock and read", enabled = pw.isNotBlank()) { onUnlock(pw) }
    }
}

@Composable
private fun Review(p: ImportPhase.Review, d: AppData, ivm: ImportViewModel, onPick: (ImportItem) -> Unit, onEdit: (ImportItem) -> Unit, onDone: (List<Long>, Boolean) -> Unit) {
    var showDupes by rememberSaveable { mutableStateOf(false) }
    val dupes = p.items.count { it.verdict == Dedupe.Verdict.DUPLICATE }
    val possible = p.items.count { it.verdict == Dedupe.Verdict.POSSIBLE }
    val fresh = p.items.count { it.verdict == Dedupe.Verdict.NEW }
    val needAmount = p.items.count { it.txn.amountPaise == 0L && it.verdict != Dedupe.Verdict.DUPLICATE }
    val unsure = p.items.count { !it.sure && it.txn.amountPaise > 0 && it.verdict != Dedupe.Verdict.DUPLICATE }
    val visible = if (showDupes) p.items else p.items.filter { it.verdict != Dedupe.Verdict.DUPLICATE }
    val chosen = p.items.filter { it.selected }
    val outSum = chosen.filter { it.txn.type != TxType.INCOME }.sumOf { it.txn.amountPaise }
    val inSum = chosen.filter { it.txn.type == TxType.INCOME }.sumOf { it.txn.amountPaise }
    val first = p.items.minOfOrNull { it.txn.date }; val last = p.items.maxOfOrNull { it.txn.date }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Found ${p.items.size} transactions", style = MaterialTheme.typography.headlineSmall, color = Kc.colors.text)
                    if (first != null && last != null) Text("${first.format(dayFmt)} to ${last.format(dayFmt)}", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("✨ $fresh new", Kc.colors.goodBg, Kc.colors.good)
                        if (dupes > 0) Pill("✓ $dupes already in app", Kc.colors.chip, Kc.colors.muted) { showDupes = !showDupes }
                        if (possible > 0) Pill("? $possible maybe", Kc.colors.warnBg, Coral)
                        if (needAmount > 0) Pill("✎ $needAmount need amount", Kc.colors.warnBg, Coral)
                    }
                    if (needAmount > 0) Text("$needAmount rows have no amount because it couldn't be confirmed against the balance. Rather than guess, they're left blank: tap one to enter it, or skip it.",
                        style = MaterialTheme.typography.bodySmall, color = Coral)
                    p.seenBefore?.let { Text("You imported this file on ${it.format(dayFmt)}. Rows already in the app are skipped automatically.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.text) }
                    if (dupes > 0) Text(if (showDupes) "Showing already-imported rows (unticked). Tap ✓ above to hide them." else "Already-imported rows are hidden and won't be added again.",
                        style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    if (possible > 0) Text("\"Maybe\" rows match an entry you already have (same amount, within a day). They're unticked; tick them if they're different.",
                        style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    if (unsure > 0) Text("⚠️ $unsure rows have an amount that couldn't be cross-checked (no balance shown). Glance at them.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    Text("Tap a row to tick/untick · ✎ to edit payee, amount, date or note · the icon to change category.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Select all new", Kc.colors.chip, Kc.colors.text) { ivm.setAll(true, includeDuplicates = false) }
                        Pill("Clear", Kc.colors.chip, Kc.colors.text) { ivm.setAll(false, includeDuplicates = true) }
                    }
                }
            }
            items(visible, key = { it.key }) { item ->
                val cat = d.catMap[item.txn.categoryId]
                val credit = item.txn.type == TxType.INCOME
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (item.verdict == Dedupe.Verdict.DUPLICATE) Kc.colors.chip else Kc.colors.card)
                        .clickable { if (item.txn.amountPaise == 0L) onEdit(item) else ivm.toggle(item.key) }.padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(item.selected, { if (item.txn.amountPaise == 0L) onEdit(item) else ivm.toggle(item.key) })
                    Box(Modifier.clickable { onPick(item) }) { CategoryIcon(cat, 36.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(item.txn.payee.ifBlank { cat?.name ?: "Entry" }, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text, maxLines = 1)
                        val status = when (item.verdict) {
                            Dedupe.Verdict.DUPLICATE -> "already in app" + (item.matchedWith?.let { ": $it" } ?: "")
                            Dedupe.Verdict.POSSIBLE -> "maybe same as ${item.matchedWith ?: "an entry"}"
                            Dedupe.Verdict.NEW -> when { item.txn.amountPaise == 0L -> "amount not confirmed"; !item.sure -> "⚠️ check"; else -> null }
                        }
                        Text(listOfNotNull(item.txn.date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)), cat?.name, status).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = if (item.verdict != Dedupe.Verdict.NEW || !item.sure) Coral else Kc.colors.muted, maxLines = 1)
                    }
                    if (item.txn.amountPaise == 0L) Text("✎ Enter amount", style = MaterialTheme.typography.labelLarge, color = Coral)
                    else Text((if (credit) "+" else "−") + Money.format(item.txn.amountPaise, withPaise = true), style = MaterialTheme.typography.titleSmall,
                        color = if (credit) Kc.colors.good else Kc.colors.text)
                    Text("✎", style = MaterialTheme.typography.titleMedium, color = Kc.colors.muted,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onEdit(item) }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Kc.colors.card).navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chosen.isNotEmpty()) Text("${chosen.size} selected · out ${Money.format(outSum)} · in ${Money.format(inSum)}", style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { ivm.save(asDrafts = true) { onDone(it, true) } }, enabled = chosen.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp)) { Text("Save as drafts") }
                BigButton(if (chosen.isEmpty()) "Select entries" else "Add ${chosen.size}", Modifier.weight(1f), enabled = chosen.isNotEmpty()) { ivm.save(asDrafts = false) { onDone(it, false) } }
            }
        }
    }
}

@Composable
private fun KindSwitch(a: Attachment, onChange: (AttachmentKind) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("This is a", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        listOf(AttachmentKind.BANK_STATEMENT to "🏦 Bank statement", AttachmentKind.CARD_STATEMENT to "💳 Card statement").forEach { (k, label) ->
            val on = a.kind == k || (k == AttachmentKind.BANK_STATEMENT && a.kind != AttachmentKind.CARD_STATEMENT)
            Pill(label, if (on) Haldi else Kc.colors.chip, if (on) Ink else Kc.colors.text) { if (!on) onChange(k) }
        }
    }
}

@Composable
private fun RowEditDialog(item: ImportItem, onDismiss: () -> Unit, onSave: (String, Long, LocalDate, String) -> Unit) {
    var payee by rememberSaveable { mutableStateOf(item.txn.payee) }
    var amount by rememberSaveable { mutableStateOf(item.txn.amountPaise.let { if (it == 0L) "" else if (it % 100 == 0L) (it / 100).toString() else "%d.%02d".format(it / 100, it % 100) }) }
    var note by rememberSaveable { mutableStateOf(item.txn.note) }
    var date by rememberSaveable { mutableStateOf(item.txn.date.toEpochDay()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val paise = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.narration, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, maxLines = 3)
                if (item.txn.amountPaise == 0L) Text("The amount for this row couldn't be confirmed, so it was left blank instead of guessing. Check your statement and type it in.",
                    style = MaterialTheme.typography.bodySmall, color = Coral)
                OutlinedTextField(payee, { payee = it.take(40) }, label = { Text("Payee / merchant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { v -> amount = v.filter { it.isDigit() || it == '.' }.take(12) }, label = { Text("Amount") }, prefix = { Text("₹ ") },
                    singleLine = true, isError = paise == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("📅 " + LocalDate.ofEpochDay(date).format(dayFmt)) }
                OutlinedTextField(note, { note = it.take(200) }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                Text("To switch between spend, income or transfer, change the category (tap the icon).", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
        },
        confirmButton = { TextButton(enabled = paise != null, onClick = { onSave(payee, paise ?: 0, LocalDate.ofEpochDay(date), note) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) {
        val ds = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = {
                ds.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                picking = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = ds) }
    }
}
