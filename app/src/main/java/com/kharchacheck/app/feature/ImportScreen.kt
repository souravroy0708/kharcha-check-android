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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** One row found in a statement, turned into an editable draft entry. */
data class ImportItem(val key: Int, val txn: Txn, val selected: Boolean, val duplicate: Boolean, val sure: Boolean, val narration: String)

sealed interface ImportPhase {
    data object Starting : ImportPhase
    data class Reading(val page: Int, val total: Int) : ImportPhase
    data class Password(val wrong: Boolean) : ImportPhase
    data class Review(val items: List<ImportItem>, val creditCard: Boolean) : ImportPhase
    data class Failed(val message: String) : ImportPhase
}

class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val _phase = MutableStateFlow<ImportPhase>(ImportPhase.Starting)
    val phase = _phase.asStateFlow()
    private var job: Job? = null
    private var started = false

    /** Re-reads from scratch, e.g. after switching between bank and credit card. */
    fun restart(a: Attachment, data: AppData) { started = false; start(a, data) }

    fun start(a: Attachment, data: AppData, password: String? = null) {
        if (started && password == null) return
        started = true
        job?.cancel()
        job = viewModelScope.launch {
            _phase.value = ImportPhase.Reading(0, 0)
            val bytes = runCatching { c.files.bytes(a) }.getOrNull() ?: run { _phase.value = ImportPhase.Failed("The file couldn't be opened."); return@launch }
            when (val out = c.reader.statement(a, bytes, password) { p, t -> _phase.value = ImportPhase.Reading(p, t) }) {
                is AutoReader.Outcome.NeedsPassword -> _phase.value = ImportPhase.Password(out.wrong)
                is AutoReader.Outcome.Failed -> _phase.value = ImportPhase.Failed(out.message)
                is AutoReader.Outcome.Rows -> {
                    if (out.result.rows.isEmpty()) {
                        _phase.value = ImportPhase.Failed("No transactions were found. Scanned or photographed statements read less reliably; if your bank offers a CSV download, attach that instead.")
                    } else _phase.value = ImportPhase.Review(build(out.result, data), out.result.creditCard)
                }
            }
        }
    }

    private suspend fun build(res: StatementParser.Result, d: AppData): List<ImportItem> {
        val byKey = d.activeCategories.associateBy { it.key }
        val rules = c.finance.rulesList()
        val zone = ZoneId.systemDefault()
        return res.rows.mapIndexed { i, r ->
            val cls = Classifier.classify(r.description, r.credit, res.creditCard)
            val lowered = (cls.payee + " " + r.description).lowercase(Locale.ENGLISH)
            val ruled = rules.firstOrNull { lowered.contains(it.pattern) }?.let { rule -> d.catMap[rule.categoryId]?.takeIf { !it.archived } }
            val cat = ruled ?: byKey[cls.categoryKey] ?: byKey.getValue("uncategorised")
            val type = cat.bucket.type
            val mode = when {
                res.creditCard -> PayMode.CARD
                Regex("upi", RegexOption.IGNORE_CASE).containsMatchIn(r.description) -> PayMode.UPI
                Regex("atm|cash", RegexOption.IGNORE_CASE).containsMatchIn(r.description) -> PayMode.CASH
                Regex("neft|imps|rtgs|nach|ach", RegexOption.IGNORE_CASE).containsMatchIn(r.description) -> PayMode.NET_BANKING
                Regex("pos|card", RegexOption.IGNORE_CASE).containsMatchIn(r.description) -> PayMode.CARD
                else -> PayMode.OTHER
            }
            val ref = r.ref.ifBlank { Regex("""\b(\d{12})\b""").find(r.description)?.groupValues?.get(1).orEmpty() }
            val t = Txn(
                amountPaise = r.amountPaise, type = type, categoryId = cat.id,
                epochMillis = r.date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), zoneId = zone.id,
                mode = mode, payee = cls.payee, note = r.description.take(120), source = Source.STATEMENT, refNo = ref,
            )
            val dup = d.txns.any { e ->
                (ref.isNotEmpty() && e.refNo == ref) ||
                    (e.amountPaise == r.amountPaise && abs(e.date.toEpochDay() - r.date.toEpochDay()) <= 1 && (e.type == TxType.INCOME) == r.credit)
            }
            ImportItem(i, t, selected = !dup, duplicate = dup, sure = r.sure, narration = r.description)
        }
    }

    fun toggle(key: Int) = update { it.map { x -> if (x.key == key) x.copy(selected = !x.selected) else x } }
    fun setAll(on: Boolean) = update { it.map { x -> x.copy(selected = on) } }
    fun edit(key: Int, payee: String, amountPaise: Long) =
        update { it.map { x -> if (x.key == key) x.copy(txn = x.txn.copy(payee = payee.trim(), amountPaise = amountPaise), sure = true) else x } }

    fun setCategory(key: Int, cat: Category) = update { it.map { x -> if (x.key == key) x.copy(txn = x.txn.copy(categoryId = cat.id, type = cat.bucket.type)) else x } }

    private fun update(f: (List<ImportItem>) -> List<ImportItem>) {
        val p = _phase.value as? ImportPhase.Review ?: return
        _phase.value = p.copy(items = f(p.items))
    }

    fun save(asDrafts: Boolean, onDone: (Int) -> Unit) = viewModelScope.launch {
        val p = _phase.value as? ImportPhase.Review ?: return@launch
        val n = c.finance.importAll(p.items.filter { it.selected }.map { it.txn }, asDrafts)
        onDone(n)
    }

    override fun onCleared() { job?.cancel() }
}

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

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

    LaunchedEffect(a?.id) { if (a != null) ivm.start(a, data) }

    Column(Modifier.fillMaxSize().background(Kc.colors.paper)) {
        Row(Modifier.fillMaxWidth().background(Ink).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("✨ Read statement", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(a?.label?.ifBlank { a.kind.label } ?: "", style = MaterialTheme.typography.labelSmall, color = MutedOnInk, maxLines = 1)
            }
        }
        when (val p = phase) {
            ImportPhase.Starting, is ImportPhase.Reading -> Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val r = p as? ImportPhase.Reading
                Text(if (r != null && r.total > 1) "Reading page ${r.page} of ${r.total}…" else "Reading your statement…", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text)
                if (r != null && r.total > 0) LinearProgressIndicator(progress = { r.page.toFloat() / r.total }, modifier = Modifier.fillMaxWidth(), color = Haldi)
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Haldi)
                Text("This happens entirely on your phone. Nothing is uploaded. A 5-page statement takes about 15–30 seconds.",
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
            }
            is ImportPhase.Password -> PasswordStep(p) { pw -> if (a != null) ivm.start(a, data, pw) }
            is ImportPhase.Failed -> {
                if (a != null) KindSwitch(a) { k -> vm.setStatementKind(a, k); ivm.restart(a.copy(kind = k), data) }
                EmptyState("🧐", "Couldn't read this one", p.message, "Back") { nav.popBackStack() }
            }
            is ImportPhase.Review -> {
                if (a != null) KindSwitch(a) { k -> vm.setStatementKind(a, k); ivm.restart(a.copy(kind = k), data) }
                Review(p, data, ivm, onPick = { picking = it }, onEdit = { editing = it }) { n, drafts ->
                    vm.say(when {
                        n == 0 -> "Nothing added"
                        drafts -> "$n drafts added to your Review inbox ✓"
                        else -> "Added $n ${if (n == 1) "entry" else "entries"} from your statement ✓"
                    })
                    nav.popBackStack()
                }
            }
        }
    }

    editing?.let { item -> RowEditDialog(item, onDismiss = { editing = null }) { payee, amt -> ivm.edit(item.key, payee, amt); editing = null } }
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
private fun Review(p: ImportPhase.Review, d: AppData, ivm: ImportViewModel, onPick: (ImportItem) -> Unit, onEdit: (ImportItem) -> Unit, onDone: (Int, Boolean) -> Unit) {
    val chosen = p.items.count { it.selected }
    val dups = p.items.count { it.duplicate }
    val unsure = p.items.count { !it.sure }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Found ${p.items.size} transactions", style = MaterialTheme.typography.headlineSmall, color = Kc.colors.text)
                    if (dups > 0) Text("$dups look already logged, so they're unticked.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                    if (unsure > 0) Text("⚠️ $unsure couldn't be confirmed against the balance. Check their amount and direction.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                    Text("Tap a category icon to change it, ✎ to fix the payee or amount. “Save as drafts” puts them in your Review inbox to confirm one by one.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Select all", Kc.colors.chip, Kc.colors.text) { ivm.setAll(true) }
                        Pill("Clear", Kc.colors.chip, Kc.colors.text) { ivm.setAll(false) }
                    }
                }
            }
            itemsIndexed(p.items, key = { _, it -> it.key }) { _, item ->
                val cat = d.catMap[item.txn.categoryId]
                val credit = item.txn.type == TxType.INCOME
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Kc.colors.card).clickable { ivm.toggle(item.key) }.padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(item.selected, { ivm.toggle(item.key) })
                    Box(Modifier.clickable { onPick(item) }) { CategoryIcon(cat, 36.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(item.txn.payee.ifBlank { cat?.name ?: "Entry" }, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text, maxLines = 1)
                        Text(listOfNotNull(item.txn.date.format(dayFmt), cat?.name, if (item.duplicate) "already logged?" else null, if (!item.sure) "⚠️ check" else null).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = if (item.duplicate || !item.sure) Coral else Kc.colors.muted, maxLines = 1)
                    }
                    Text((if (credit) "+" else "−") + Money.format(item.txn.amountPaise, withPaise = true), style = MaterialTheme.typography.titleSmall,
                        color = if (credit) Kc.colors.good else Kc.colors.text)
                    Text("✎", style = MaterialTheme.typography.titleMedium, color = Kc.colors.muted,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onEdit(item) }.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Kc.colors.card).navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { ivm.save(asDrafts = true) { onDone(it, true) } }, enabled = chosen > 0, modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                shape = RoundedCornerShape(18.dp)) { Text("Save as drafts") }
            BigButton(if (chosen == 0) "Select entries" else "Add & confirm $chosen", Modifier.weight(1f), enabled = chosen > 0) { ivm.save(asDrafts = false) { onDone(it, false) } }
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
private fun RowEditDialog(item: ImportItem, onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var payee by rememberSaveable { mutableStateOf(item.txn.payee) }
    var amount by rememberSaveable { mutableStateOf(item.txn.amountPaise.let { if (it % 100 == 0L) (it / 100).toString() else "%d.%02d".format(it / 100, it % 100) }) }
    val paise = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fix this entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.narration, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, maxLines = 3)
                OutlinedTextField(payee, { payee = it.take(40) }, label = { Text("Payee") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { v -> amount = v.filter { it.isDigit() || it == '.' }.take(12) }, label = { Text("Amount") }, prefix = { Text("₹ ") },
                    singleLine = true, isError = paise == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Text("To switch between spend, income or transfer, change the category instead.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
        },
        confirmButton = { TextButton(enabled = paise != null, onClick = { onSave(payee, paise ?: 0) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
