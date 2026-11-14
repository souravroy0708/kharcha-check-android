@file:OptIn(ExperimentalMaterial3Api::class)

package com.kharchacheck.app.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Attachment
import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.ReviewState
import com.kharchacheck.app.core.model.Source
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.core.model.Worth
import com.kharchacheck.app.domain.AmountExpression
import com.kharchacheck.app.domain.Dues
import com.kharchacheck.app.domain.InvestMath
import com.kharchacheck.app.domain.read.ReceiptParser
import com.kharchacheck.app.ui.AppData
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.components.AttachmentThumb
import com.kharchacheck.app.ui.components.AttachmentViewer
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.CategoryIcon
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.Segmented
import com.kharchacheck.app.ui.nav.EditRoute
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Ink2
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.MutedOnInk
import com.kharchacheck.app.ui.theme.color
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Form state for one entry. Lives in a ViewModel so rotation or a phone call never loses a half-typed entry. */
class EditTxnState : ViewModel() {
    var loaded by mutableStateOf(false)
    var original: Txn? = null
    var type by mutableStateOf(TxType.EXPENSE)
    var expr by mutableStateOf("")
    var categoryId by mutableLongStateOf(0L)
    var epochMillis by mutableLongStateOf(System.currentTimeMillis())
    var mode by mutableStateOf(PayMode.UPI)
    var payee by mutableStateOf("")
    var note by mutableStateOf("")
    var tags by mutableStateOf("")
    var worth by mutableStateOf(Worth.NONE)
    var goalId by mutableStateOf<Long?>(null)
    var source by mutableStateOf(Source.MANUAL)
    var refNo by mutableStateOf("")
    var rememberRule by mutableStateOf(false)
    var repeat by mutableStateOf(false)
    var splitOn by mutableStateOf(false)
    var hidden by mutableStateOf(false)
    var splitNames by mutableStateOf("")
    var attachmentIds by mutableStateOf<List<Long>>(emptyList())
    /** Attachment waiting to be auto-read once it appears in the attachments list. */
    var pendingRead by mutableStateOf<Long?>(null)
    var readNote by mutableStateOf<String?>(null)
    var reading by mutableStateOf(false)
    var keypad by mutableStateOf(true)
    var startedAt = System.currentTimeMillis()

    val amountPaise: Long? get() = AmountExpression.evaluate(expr)

    fun fill(t: Txn, editing: Boolean) {
        original = if (editing) t else null
        type = t.type; expr = plain(t.amountPaise); categoryId = t.categoryId; epochMillis = t.epochMillis; mode = t.mode
        payee = t.payee; note = t.note; tags = t.tags; worth = t.worth; goalId = t.goalId; source = t.source; refNo = t.refNo
        keypad = !editing && t.amountPaise == 0L
    }

    fun resetForAnother() {
        original = null; expr = ""; categoryId = 0; payee = ""; note = ""; tags = ""; worth = Worth.NONE; goalId = null
        source = Source.MANUAL; refNo = ""; rememberRule = false; repeat = false; splitOn = false; hidden = false; splitNames = ""; attachmentIds = emptyList(); pendingRead = null; readNote = null; keypad = true; startedAt = System.currentTimeMillis()
    }

    fun build(): Txn? {
        val amt = amountPaise ?: return null
        if (categoryId == 0L) return null
        val o = original
        return Txn(
            id = o?.id ?: 0, amountPaise = amt, type = type, categoryId = categoryId, epochMillis = epochMillis,
            zoneId = o?.zoneId ?: ZoneId.systemDefault().id, mode = mode, payee = payee.trim(), note = note.trim(), tags = tags.trim(),
            worth = if (type == TxType.EXPENSE) worth else Worth.NONE, source = source, review = ReviewState.CONFIRMED, refNo = refNo,
            goalId = if (type == TxType.INVEST) goalId else null, createdAt = o?.createdAt ?: System.currentTimeMillis(),
        )
    }

    fun plain(p: Long) = if (p <= 0) "" else if (p % 100 == 0L) (p / 100).toString() else "%d.%02d".format(p / 100, p % 100)
}

@Composable
fun EditTxnScreen(vm: AppViewModel, nav: NavHostController, route: EditRoute) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val st: EditTxnState = viewModel()
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var newCategory by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    val allAttachments by vm.attachments.collectAsStateWithLifecycle()
    val platform = LocalPlatform.current

    // Auto-read a newly attached screenshot (offline OCR). Only empty fields are filled.
    // Keyed only on pendingRead, which is cleared at the very end so the read is never cancelled midway.
    LaunchedEffect(st.pendingRead) {
        val id = st.pendingRead ?: return@LaunchedEffect
        val a = withTimeoutOrNull(5_000) { vm.attachments.first { list -> list.any { it.id == id } }.first { it.id == id } }
        if (a != null && data.settings.autoRead && a.isImage) {
            st.reading = true
            val r = vm.readReceipt(a)
            st.reading = false
            st.readNote = applyReceipt(st, r, data)
        }
        st.pendingRead = null
    }

    LaunchedEffect(route) {
        if (st.loaded) return@LaunchedEffect
        when {
            route.id > 0 -> vm.loadTxn(route.id)?.let {
                st.fill(it, editing = true)
                st.hidden = route.id in data.hiddenIds
                st.attachmentIds = vm.attachments.value.filter { a -> a.txnId == route.id }.map { a -> a.id }
            }
            route.fromDraft -> vm.takeDraft()?.let {
                st.fill(it, editing = false)
                st.attachmentIds = vm.takeDraftAttachments()
                // A shared screenshot has no amount yet: keep the keypad open, the screenshot is right below.
                st.keypad = it.amountPaise == 0L
                if (it.amountPaise == 0L) st.pendingRead = st.attachmentIds.firstOrNull()
            }
            else -> {
                st.type = runCatching { TxType.valueOf(route.type) }.getOrDefault(TxType.EXPENSE)
                if (route.categoryId > 0) st.categoryId = route.categoryId
            }
        }
        st.loaded = true
    }

    val editing = st.original != null
    val categories = data.activeCategories.filter { it.bucket.type == st.type }
    val selected = data.catMap[st.categoryId]
    val suggestions = remember(st.type, data.txns.size, st.categoryId) { suggestionsFor(data, st.type, st.categoryId) }
    val canSave = st.amountPaise != null && st.categoryId != 0L && selected?.bucket?.type == st.type

    fun close() { nav.popBackStack() }
    fun save(another: Boolean) {
        val built = st.build() ?: return
        // Split: you paid the whole bill; only your share counts as your spending, the rest becomes dues.
        val names = if (st.splitOn && !editing && st.type == TxType.EXPENSE) Dues.parseNames(st.splitNames) else emptyList()
        val (t, split) = if (names.isNotEmpty()) {
            val (mine, each) = Dues.splitEqual(built.amountPaise, names.size)
            val note = listOf(built.note, "Split with ${names.joinToString()} · total ${Money.format(built.amountPaise)}").filter { it.isNotBlank() }.joinToString("\n")
            built.copy(amountPaise = mine, note = note) to AppViewModel.SplitRequest(names, each, built.amountPaise)
        } else built to null
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.save(t, st.rememberRule, repeatMonthly = st.repeat && !editing, attachmentIds = st.attachmentIds, split = split,
            hidden = if (editing || st.hidden) st.hidden else null) {
            val secs = (System.currentTimeMillis() - st.startedAt) / 1000
            vm.say(if (editing) "Saved ✓" else "Added ${Money.format(t.amountPaise)}" + if (secs in 1..9) " in ${secs}s ⚡" else " ✓")
            if (another) st.resetForAnother() else close()
        }
    }

    BackHandler(enabled = st.keypad && editing) { st.keypad = false }

    Column(Modifier.fillMaxSize().background(Kc.colors.paper).imePadding()) {
        // ---- Ink header: type + amount ----
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(Ink)
                .statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { close() }) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White) }
                Text(if (editing) "Edit entry" else "New entry", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                if (editing) {
                    IconButton(onClick = { st.original?.let { o -> st.fill(o.copy(id = 0, epochMillis = System.currentTimeMillis(), source = Source.MANUAL, refNo = ""), editing = false); st.attachmentIds = emptyList(); vm.say("Copied. Change anything, then save.") } }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate", tint = Color.White)
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White) }
                }
            }
            val types = TxType.entries
            Segmented(types.map { it.label.replace("Save & Invest", "Invest") }, types.indexOf(st.type), onDark = true) { i ->
                if (types[i] != st.type) { st.type = types[i]; st.categoryId = 0; st.goalId = null }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink2)
                    .border(2.dp, if (st.keypad) Haldi else Color.Transparent, RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button) { focus.clearFocus(); st.keypad = true }.padding(vertical = 12.dp, horizontal = 16.dp)
                    .semantics { contentDescription = "Amount ${st.amountPaise?.let { Money.format(it, true) } ?: "empty"}. Tap to edit." },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(st.amountPaise?.let { Money.format(it, withPaise = true) } ?: "₹0", style = MaterialTheme.typography.displayMedium,
                    color = if (st.amountPaise != null) Color.White else MutedOnInk, maxLines = 1)
                Text(if (AmountExpression.hasOperator(st.expr)) st.expr else if (st.keypad) "Tip: type 120+45 to add items" else "Tap to change amount",
                    style = MaterialTheme.typography.bodySmall, color = MutedOnInk, maxLines = 1)
            }
        }

        // ---- Details ----
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (st.reading) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Haldi.copy(alpha = 0.25f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kc.colors.text)
                Spacer(Modifier.width(10.dp))
                Text("Reading the screenshot on your phone…", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
            }
            st.readNote?.let { note ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Haldi.copy(alpha = 0.25f)).clickable { st.readNote = null }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text, modifier = Modifier.weight(1f))
                    Text("✕", color = Kc.colors.muted, modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (st.source != Source.MANUAL) {
                Pill(if (st.source == Source.SMS) "From bank SMS · every field is editable" else "From shared text · every field is editable", Kc.colors.chip, Kc.colors.muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Category", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, modifier = Modifier.weight(1f))
                Text("All ${categories.size} ›", style = MaterialTheme.typography.labelLarge, color = if (Kc.colors.dark) Haldi else Ink,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { showSheet = true }.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            CategoryGrid(suggestions, st.categoryId) { st.categoryId = it.id; if (st.amountPaise != null) st.keypad = false }
            if (selected != null && selected.bucket == Bucket.LEAK && st.type == TxType.EXPENSE) LeakNudge(data, selected)

            OutlinedTextField(
                value = st.payee, onValueChange = { st.payee = it.take(40) },
                label = { Text(when (st.type) { TxType.INCOME -> "From (optional)"; TxType.TRANSFER -> "To / from (optional)"; else -> "Paid to / what for (optional)" }) },
                singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) st.keypad = false },
            )
            if (st.payee.isNotBlank() && selected != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { st.rememberRule = !st.rememberRule }) {
                    Checkbox(st.rememberRule, { st.rememberRule = it })
                    Text("Always put “${st.payee.trim()}” in ${selected.name}", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                }
            }

            // When
            Text("When", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
            val zdt = Instant.ofEpochMilli(st.epochMillis).atZone(ZoneId.systemDefault())
            val date = zdt.toLocalDate(); val today = LocalDate.now()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Choice("Today", date == today) { st.epochMillis = withDate(st.epochMillis, today) } }
                item { Choice("Yesterday", date == today.minusDays(1)) { st.epochMillis = withDate(st.epochMillis, today.minusDays(1)) } }
                item { Choice("📅 " + date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)), date != today && date != today.minusDays(1)) { showDate = true } }
                item { Choice("🕘 " + zdt.toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)), false) { showTime = true } }
            }

            if (st.type != TxType.INCOME) {
                Text("Paid with", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PayMode.entries) { m -> Choice(m.label, st.mode == m) { st.mode = m } }
                }
            }

            if (st.type == TxType.EXPENSE) {
                Text("Was it worth it?", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Worth.NEEDED, Worth.WORTH_IT, Worth.REGRET).forEach { w ->
                        Choice("${w.emoji} ${w.label}", st.worth == w) { st.worth = if (st.worth == w) Worth.NONE else w }
                    }
                }
            }

            if (st.type == TxType.INVEST && data.goals.isNotEmpty()) {
                Text("Towards a goal?", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Choice("None", st.goalId == null) { st.goalId = null } }
                    items(data.goals, key = { it.id }) { g -> Choice("${g.emoji} ${g.name}", st.goalId == g.id) { st.goalId = g.id } }
                }
            }

            if (!editing && st.type == TxType.EXPENSE) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Kc.colors.card).clickable { st.splitOn = !st.splitOn }.padding(end = 12.dp)) {
                    Checkbox(st.splitOn, { st.splitOn = it })
                    Column(Modifier.weight(1f)) {
                        Text("🤝 Split this bill", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                        Text("You paid for others too. Only your share counts as your spend; the rest is tracked as money owed to you.",
                            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                }
                if (st.splitOn) {
                    OutlinedTextField(st.splitNames, { st.splitNames = it.take(200) }, label = { Text("Split with (names, comma separated)") },
                        placeholder = { Text("e.g. Rahul, Priya") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) st.keypad = false })
                    val names = Dues.parseNames(st.splitNames)
                    val total = st.amountPaise
                    if (names.isNotEmpty() && total != null) {
                        val (mine, each) = Dues.splitEqual(total, names.size)
                        Text("Equal split between ${names.size + 1}: your share ${Money.format(mine, true)}, each of ${names.joinToString()} owes you ${Money.format(each, true)}.",
                            style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                    }
                }
            }
            if (!editing && st.type != TxType.TRANSFER) {
                val day = Instant.ofEpochMilli(st.epochMillis).atZone(ZoneId.systemDefault()).dayOfMonth
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Kc.colors.card).clickable { st.repeat = !st.repeat }.padding(end = 12.dp)) {
                    Checkbox(st.repeat, { st.repeat = it })
                    Column(Modifier.weight(1f)) {
                        Text("🔁 Repeat every month on the ${ordinal(day)}", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                        Text("For rent, EMI, SIP, salary, subscriptions. Added to Review on the day; you confirm it.",
                            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                }
            }

            OutlinedTextField(st.note, { st.note = it.take(200) }, label = { Text("Note (optional)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) st.keypad = false })
            OutlinedTextField(st.tags, { st.tags = it.take(60) }, label = { Text("Tags (optional, e.g. trip-goa, office)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) st.keypad = false })
            com.kharchacheck.app.ui.components.SwitchRow("🙈 Hide this entry",
                "Private: left out of lists, totals, reports, exports and notifications. See it only after unlocking in Settings → Hidden entries.", st.hidden) { st.hidden = it }

            // Receipt / UPI screenshot, for reference. Stored encrypted; shown only inside the app.
            Text("Receipt or screenshot", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
            val mine = st.attachmentIds.mapNotNull { id -> allAttachments.firstOrNull { it.id == id } }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(mine, key = { it.id }) { a -> AttachmentThumb(vm, a, 72.dp) { focus.clearFocus(); viewing = a } }
                if (mine.size < 5) item {
                    Column(
                        Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(Kc.colors.card)
                            .border(1.dp, Kc.colors.line, RoundedCornerShape(14.dp))
                            .clickable { platform.pickImage { uri -> vm.addReceipt(uri) { a ->
                                st.attachmentIds = st.attachmentIds + a.id
                                if (st.amountPaise == null) st.pendingRead = a.id
                            } } }
                            .semantics { contentDescription = "Attach a receipt or payment screenshot" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text("📎", fontSize = 22.sp)
                        Text("Add", style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
                    }
                }
            }
            if (mine.isEmpty()) Text("Attach a bill photo or a GPay / PhonePe / Paytm payment screenshot. Tip: share a screenshot straight to Kharcha Check to start a new entry with it.",
                style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            Spacer(Modifier.height(8.dp))
        }

        // ---- Keypad + save ----
        Column(Modifier.fillMaxWidth().background(Kc.colors.card).navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (st.keypad) Keypad { key ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (key == "✓") st.keypad = false else st.expr = AmountExpression.press(st.expr, key)
            }
            if (!canSave) Text(
                when { st.amountPaise == null -> "Enter an amount"; else -> "Pick a category" },
                style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!editing) TextButton(onClick = { save(another = true) }, enabled = canSave, modifier = Modifier.weight(1f)) { Text("Save & add another") }
                BigButton(if (editing) "Save changes" else "Save", Modifier.weight(1f), enabled = canSave) { save(another = false) }
            }
        }
    }

    if (showSheet) CategorySheet(categories, st.categoryId, onPick = { st.categoryId = it.id; showSheet = false }, onNew = { showSheet = false; newCategory = true }, onDismiss = { showSheet = false })
    if (newCategory) CategoryEditorDialog(null, Bucket.forType(st.type).first(), allowedBuckets = Bucket.forType(st.type), onDismiss = { newCategory = false }) { c -> vm.saveCategory(c); newCategory = false }

    if (showDate) {
        val now = System.currentTimeMillis()
        val ds = rememberDatePickerState(
            initialSelectedDateMillis = Instant.ofEpochMilli(st.epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= now },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                ds.selectedDateMillis?.let { st.epochMillis = withDate(st.epochMillis, Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                showDate = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = ds) }
    }
    if (showTime) {
        val t = Instant.ofEpochMilli(st.epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
        val ts = rememberTimePickerState(t.hour, t.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { st.epochMillis = withTime(st.epochMillis, LocalTime.of(ts.hour, ts.minute)); showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = ts) },
        )
    }
    viewing?.let { a ->
        AttachmentViewer(vm, a, onDismiss = { viewing = null }, onDelete = {
            vm.removeAttachment(a); st.attachmentIds = st.attachmentIds - a.id; viewing = null
        })
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this entry?") },
        text = { Text("You can undo right after.") },
        confirmButton = { TextButton(onClick = { st.original?.let(vm::delete); confirmDelete = false; close() }) { Text("Delete", color = Coral) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
    )
}

/** Fills only what's still empty, so a read never overwrites what the user typed. Returns a note to show. */
private fun applyReceipt(st: EditTxnState, r: ReceiptParser.Result?, d: AppData): String? {
    if (r == null) return "Couldn't read that image. You can still type the details."
    if (r.status == ReceiptParser.Status.FAILED) return "This looks like a failed payment, so nothing was filled in."
    if (!r.foundSomething) return "Couldn't find payment details in that image. You can still type them."
    val filled = mutableListOf<String>()
    if (st.categoryId == 0L) {
        val cat = d.activeCategories.firstOrNull { it.key == r.categoryKey && it.bucket.type == r.type }
        if (cat != null) { st.type = r.type; st.categoryId = cat.id; filled += "category" }
        else if (st.original == null) st.type = r.type
    }
    if (st.amountPaise == null && r.amountPaise != null) { st.expr = st.plain(r.amountPaise); filled += "amount" }
    if (st.payee.isBlank() && r.payee.isNotBlank()) { st.payee = r.payee; filled += if (r.type == TxType.INCOME) "sender" else "payee" }
    if (st.original == null && r.dateTime != null) {
        st.epochMillis = minOf(r.dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), System.currentTimeMillis()); filled += "date"
    }
    if (st.refNo.isBlank() && r.refNo.isNotBlank()) st.refNo = r.refNo
    if (st.original == null) st.mode = PayMode.UPI
    if (filled.isEmpty()) return null
    if ("amount" in filled) st.keypad = false
    val check = if ("amount" in filled && !r.amountSure) " Please double-check the amount." else " Please check before saving."
    val pending = if (r.status == ReceiptParser.Status.PENDING) " The payment looked pending." else ""
    return "✨ Filled ${filled.joinToString(", ")} from the screenshot.$check$pending"
}

private fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

private fun withDate(epoch: Long, date: LocalDate): Long {
    val z = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(epoch).atZone(z).toLocalTime()
    val candidate = date.atTime(time).atZone(z).toInstant().toEpochMilli()
    return minOf(candidate, System.currentTimeMillis())
}

private fun withTime(epoch: Long, time: LocalTime): Long {
    val z = ZoneId.systemDefault()
    return Instant.ofEpochMilli(epoch).atZone(z).toLocalDate().atTime(time).atZone(z).toInstant().toEpochMilli()
}

/** Most-used categories for this type first, then sensible defaults; always includes the current pick. */
private fun suggestionsFor(d: AppData, type: TxType, selectedId: Long): List<Category> {
    val pool = d.activeCategories.filter { it.bucket.type == type }
    val used = d.txns.asSequence().filter { it.type == type }.groupingBy { it.categoryId }.eachCount()
        .entries.sortedByDescending { it.value }.mapNotNull { e -> pool.firstOrNull { it.id == e.key } }
    val list = (listOfNotNull(pool.firstOrNull { it.id == selectedId }) + used + pool).distinctBy { it.id }
    return list.take(if (type == TxType.EXPENSE) 12 else 8)
}

@Composable
private fun CategoryGrid(list: List<Category>, selectedId: Long, onPick: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val on = c.id == selectedId
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(if (on) Haldi.copy(alpha = if (Kc.colors.dark) 0.25f else 0.35f) else Kc.colors.card)
                            .border(2.dp, if (on) Haldi else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable(role = Role.RadioButton) { onPick(c) }.padding(vertical = 10.dp, horizontal = 4.dp)
                            .semantics { contentDescription = "${c.name}, ${c.bucket.label}" + if (on) ", selected" else "" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(c.emoji, fontSize = 24.sp)
                        Text(c.name.substringBefore(" ("), style = MaterialTheme.typography.labelSmall,
                            color = Kc.colors.text, maxLines = 2, textAlign = TextAlign.Center)
                        Box(Modifier.padding(top = 4.dp).size(width = 18.dp, height = 3.dp).clip(RoundedCornerShape(50)).background(c.bucket.color()))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LeakNudge(d: AppData, c: Category) {
    val monthly = d.report.leaksPaise.coerceAtLeast(1_000_00)
    val future = InvestMath.sipFutureValue(monthly, d.settings.expectedReturnPct.toDouble(), 10)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Kc.colors.warnBg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🕳️", fontSize = 22.sp); Spacer(Modifier.width(10.dp))
        Text("${c.name} is a leak. Leaks so far this month: ${Money.format(d.report.leaksPaise)}. " +
            "${Money.format(monthly)} a month invested ≈ ${Money.compact(future)} in 10 years.",
            style = MaterialTheme.typography.bodySmall, color = Kc.colors.text)
    }
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
        color = if (on) Ink else Kc.colors.text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Haldi else Kc.colors.card)
            .border(1.dp, if (on) Haldi else Kc.colors.line, RoundedCornerShape(50))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val rows = listOf(listOf("1", "2", "3", "⌫"), listOf("4", "5", "6", "+"), listOf("7", "8", "9", "−"), listOf(".", "0", "00", "✓"))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.forEach { k ->
                    val special = k in setOf("⌫", "+", "−", "✓")
                    Box(
                        Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (k == "✓") Haldi else if (special) Kc.colors.chip else Kc.colors.paper)
                            .clickable { onKey(k) }
                            .semantics { contentDescription = when (k) { "⌫" -> "Delete"; "✓" -> "Done"; "−" -> "Minus"; "+" -> "Plus"; else -> k } },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, style = MaterialTheme.typography.headlineSmall, color = if (k == "✓") Ink else Kc.colors.text) }
                }
            }
        }
    }
}

@Composable
fun CategorySheet(list: List<Category>, selectedId: Long, onPick: (Category) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Kc.colors.paper) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(q, { q = it }, label = { Text("Search categories") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        val filtered = list.filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            filtered.groupBy { it.bucket }.toSortedMap().forEach { (bucket, cats) ->
                item(key = "h-$bucket") {
                    Row(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(bucket.color())); Spacer(Modifier.width(8.dp))
                        Text(bucket.label, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                        Spacer(Modifier.width(8.dp))
                        Text(bucket.blurb, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                }
                items(cats, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (c.id == selectedId) Haldi.copy(alpha = 0.3f) else Color.Transparent)
                            .clickable { onPick(c) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryIcon(c, 36.dp); Spacer(Modifier.width(12.dp))
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, color = Kc.colors.text, modifier = Modifier.weight(1f))
                        if (c.id == selectedId) Text("✓", color = Kc.colors.text)
                    }
                }
            }
            item {
                TextButton(onClick = onNew, modifier = Modifier.padding(top = 8.dp)) { Text("＋ Create your own category") }
            }
        }
    }
}
