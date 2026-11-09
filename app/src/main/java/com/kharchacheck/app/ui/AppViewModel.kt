package com.kharchacheck.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kharchacheck.app.container
import com.kharchacheck.app.core.model.Attachment
import com.kharchacheck.app.core.model.AttachmentKind
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Goal
import com.kharchacheck.app.core.model.ReviewState
import com.kharchacheck.app.core.model.Source
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.data.files.FileVault
import com.kharchacheck.app.data.repo.Settings
import com.kharchacheck.app.data.repo.ThemeMode
import com.kharchacheck.app.domain.BackupCrypto
import com.kharchacheck.app.domain.ChallengeProgress
import com.kharchacheck.app.domain.ChallengeStatus
import com.kharchacheck.app.domain.CsvWriter
import com.kharchacheck.app.domain.GameState
import com.kharchacheck.app.domain.Gamification
import com.kharchacheck.app.domain.MonthReport
import com.kharchacheck.app.data.security.SecretBox
import com.kharchacheck.app.domain.Bill
import com.kharchacheck.app.domain.Holding
import com.kharchacheck.app.domain.HoldingKind
import com.kharchacheck.app.domain.NetItem
import com.kharchacheck.app.domain.Scheme
import com.kharchacheck.app.work.AutoBackupWorker
import com.kharchacheck.app.work.CrashReporter
import com.kharchacheck.app.domain.Due
import com.kharchacheck.app.domain.RecurringRule
import com.kharchacheck.app.domain.read.ReceiptParser
import com.kharchacheck.app.domain.ReportEngine
import com.kharchacheck.app.domain.sms.SmsParser
import com.kharchacheck.app.export.ReportPdf
import com.kharchacheck.app.sms.SmsSupport
import com.kharchacheck.app.widget.KharchaWidget
import com.kharchacheck.app.work.DailyWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/** Everything the screens need, derived once per data change on a background thread. */
data class AppData(
    val settings: Settings,
    val categories: List<Category>,
    val catMap: Map<Long, Category>,
    val txns: List<Txn>,
    val budgets: Map<Long, Long>,
    val goals: List<Goal>,
    val report: MonthReport,
    val game: GameState,
    val challenge: ChallengeProgress?,
    val inbox: List<Txn>,
    val todaySpendPaise: Long,
) {
    val activeCategories: List<Category> get() = categories.filter { !it.archived }
}

data class UiMessage(val text: String, val undo: Txn? = null)

data class Win(val emoji: String, val title: String, val body: String)

private data class Raw(val s: Settings, val c: List<Category>, val t: List<Txn>, val b: Map<Long, Long>, val g: List<Goal>)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val repo get() = c.finance
    private val ctx get() = getApplication<Application>()

    val settings: StateFlow<Settings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val data: StateFlow<AppData?> = combine(c.settings.settings, repo.categories, repo.txns, repo.budgets, repo.goals) { s, cats, t, b, g -> Raw(s, cats, t, b, g) }
        .map { derive(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _reportMonth = MutableStateFlow(YearMonth.now())
    val reportMonth: StateFlow<YearMonth> = _reportMonth.asStateFlow()

    val selectedReport: StateFlow<MonthReport?> = combine(data, _reportMonth) { d, m ->
        d?.let { if (m == YearMonth.now()) it.report else ReportEngine.build(m, it.txns, it.catMap, reportConfig(it.settings, it.budgets)) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _win = MutableStateFlow<Win?>(null)
    val win: StateFlow<Win?> = _win.asStateFlow()

    /** Draft created from shared or pasted bank text; the edit screen picks it up. */
    var pendingDraft: Txn? = null

    val recurring: StateFlow<List<RecurringRule>> = repo.recurring.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dues: StateFlow<List<Due>> = c.tools.dues.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bills: StateFlow<List<Bill>> = c.tools.bills.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val attachments: StateFlow<List<Attachment>> = c.files.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Bumped whenever the profile photo changes, so avatars reload. 0 = not checked yet. */
    private val _photoVersion = MutableStateFlow(0L)
    val photoVersion: StateFlow<Long> = _photoVersion.asStateFlow()

    private var pendingAttachments: List<Long> = emptyList()

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    init {
        viewModelScope.launch {
            c.settings.ensureWisdomSeed()
            val s = c.settings.current()
            _locked.value = s.appLock
            if (s.onboarded) DailyWorker.schedule(ctx, s.reminderHour, s.reminderMinute)
            val added = repo.generateRecurring()
            if (added > 0) say("$added repeating ${if (added == 1) "entry" else "entries"} added. Check them in Review.")
        }
        viewModelScope.launch {
            data.collect { d ->
                if (d == null) return@collect
                checkWins(d)
                KharchaWidget.push(ctx, KharchaWidget.State(d.settings.onboarded, d.game.logStreak, d.game.loggedToday, d.settings.wisdomSeed))
            }
        }
    }

    private fun reportConfig(s: Settings, budgets: Map<Long, Long>) = ReportEngine.Config(
        fallbackIncomePaise = s.monthlyIncomePaise,
        budgetPaise = s.monthlyBudgetPaise, categoryBudgets = budgets, smallTicketPaise = s.smallTicketRupees * 100L,
        expectedReturnPct = s.expectedReturnPct.toDouble(), noSpendDays = s.noSpendDays,
    )

    private fun derive(r: Raw): AppData {
        val map = r.c.associateBy { it.id }
        val today = LocalDate.now()
        val report = ReportEngine.build(YearMonth.from(today), r.t, map, reportConfig(r.s, r.b))
        val game = Gamification.build(r.t, map, r.g, r.s.noSpendDays, r.s.challengesWon, r.s.monthlyBudgetPaise, today)
        val ch = Gamification.challenge(r.s.activeChallenge)?.let {
            Gamification.progress(it, LocalDate.ofEpochDay(r.s.challengeStartDay), r.t, map, r.s.noSpendDays, today)
        }
        val todaySpend = r.t.filter { it.date == today && it.type == TxType.EXPENSE && map[it.categoryId]?.bucket?.isSpend != false }.sumOf { it.amountPaise }
        return AppData(r.s, r.c, map, r.t, r.b, r.g, report, game, ch, r.t.filter { it.review == ReviewState.NEEDS_REVIEW }, todaySpend)
    }

    /** New badge or finished challenge → one celebration at a time, never repeated. */
    private fun checkWins(d: AppData) {
        if (_win.value != null || !d.settings.onboarded) return
        val fresh = d.game.badges.filter { it.earned && it.badge.id !in d.settings.celebratedBadges }
        if (fresh.isNotEmpty()) {
            val b = fresh.first().badge
            _win.value = Win(b.emoji, "Badge unlocked: ${b.name}", b.how + ". +50 XP")
            viewModelScope.launch { c.settings.markCelebrated(fresh.map { it.badge.id }.toSet()) }
            return
        }
        d.challenge?.let { p ->
            if (p.status == ChallengeStatus.WON) {
                _win.value = Win(p.challenge.emoji, "Challenge won!", "${p.challenge.title} done. +100 XP. Ready for the next one?")
                viewModelScope.launch { c.settings.endChallenge(won = true) }
            }
        }
    }

    fun dismissWin() { _win.value = null }
    fun say(msg: String) { _messages.trySend(UiMessage(msg)) }
    fun unlock() { _locked.value = false }
    fun lock() { if (settings.value?.appLock == true) _locked.value = true }
    fun setReportMonth(m: YearMonth) { if (m <= YearMonth.now()) _reportMonth.value = m }

    // ---- Entries ----
    /** People who share a bill with you, and what each of them owes. */
    data class SplitRequest(val names: List<String>, val eachPaise: Long, val totalPaise: Long)

    fun save(t: Txn, rememberPayeeRule: Boolean, repeatMonthly: Boolean = false, attachmentIds: List<Long> = emptyList(),
             split: SplitRequest? = null, after: () -> Unit = {}) = viewModelScope.launch {
        val id = repo.saveTxn(t.copy(review = ReviewState.CONFIRMED))
        c.files.link(attachmentIds, id)
        if (split != null && split.names.isNotEmpty()) {
            c.tools.addSplit(id, split.names, split.eachPaise, t.payee.ifBlank { "Split bill" } + " · total " + com.kharchacheck.app.core.model.Money.format(split.totalPaise), t.date)
        }
        if (rememberPayeeRule && t.payee.isNotBlank()) repo.rememberPayee(t.payee, t.categoryId)
        if (repeatMonthly) repo.repeatMonthly(t)
        after()
    }

    fun delete(t: Txn) = viewModelScope.launch {
        repo.deleteTxn(t)
        _messages.trySend(UiMessage("Deleted ${com.kharchacheck.app.core.model.Money.format(t.amountPaise)}", undo = t))
    }

    fun restore(t: Txn) = viewModelScope.launch { repo.saveTxn(t) }

    fun confirmAll(ids: List<Long>) = viewModelScope.launch { repo.confirm(ids); say("Confirmed ${ids.size} entries ✓") }

    fun toggleNoSpendToday() = viewModelScope.launch {
        val today = LocalDate.now().toEpochDay()
        val was = today in c.settings.current().noSpendDays
        c.settings.toggleNoSpend(today)
        if (!was) say("No-spend day marked 🌿 +20 XP")
    }

    /**
     * Parses shared/pasted bank text into an editable draft, then calls [onReady] to open the editor.
     * Nothing is saved until the user taps Save.
     */
    fun draftFromText(text: String, onReady: () -> Unit) {
        val out = SmsParser.parse("", text, trustSender = true)
        if (out !is SmsParser.Outcome.Matched) { say("Couldn't find an amount in that text. Add it manually instead."); return }
        viewModelScope.launch {
            pendingDraft = repo.draftFromParsed(out.parsed, System.currentTimeMillis(), Source.SHARED)
            onReady()
        }
    }

    fun takeDraft(): Txn? = pendingDraft.also { pendingDraft = null }
    fun takeDraftAttachments(): List<Long> = pendingAttachments.also { pendingAttachments = emptyList() }

    /** A GPay / PhonePe / any UPI screenshot shared to the app: opens a new entry with it attached. */
    fun draftFromImage(uri: Uri, onReady: () -> Unit) = viewModelScope.launch {
        runCatching { c.files.addReceipt(uri) }
            .onSuccess { a ->
                pendingAttachments = listOf(a.id)
                pendingDraft = Txn(amountPaise = 0, type = TxType.EXPENSE, categoryId = 0, epochMillis = System.currentTimeMillis(), source = Source.SHARED)
                onReady()
            }
            .onFailure { say(fileError(it)) }
    }

    // ---- Attachments ----
    fun addReceipt(uri: Uri, onAdded: (Attachment) -> Unit) = viewModelScope.launch {
        runCatching { c.files.addReceipt(uri) }.onSuccess(onAdded).onFailure { say(fileError(it)) }
    }

    fun addStatement(uri: Uri, kind: AttachmentKind, month: YearMonth, label: String, onAdded: (Attachment) -> Unit = {}) = viewModelScope.launch {
        runCatching { c.files.addStatement(uri, kind, month, label) }
            .onSuccess { say("${kind.label} saved, encrypted on this phone ✓"); onAdded(it) }
            .onFailure { say(fileError(it)) }
    }

    /** A statement opened or shared from Gmail, Drive or Files: stored encrypted, then read. */
    fun importStatementFile(uri: Uri, onReady: (Long) -> Unit) = viewModelScope.launch {
        runCatching { c.files.addStatement(uri, AttachmentKind.BANK_STATEMENT, YearMonth.now(), "") }
            .onSuccess { a ->
                // File names like "CreditCard_Statement.pdf" are a strong hint; the reader also detects card statements by their text.
                if (Regex("card|cc_|_cc|credit", RegexOption.IGNORE_CASE).containsMatchIn(a.originalName)) c.files.setKind(a, AttachmentKind.CARD_STATEMENT, a.month)
                onReady(a.id)
            }
            .onFailure { say(fileError(it)) }
    }

    fun setStatementKind(a: Attachment, kind: AttachmentKind) = viewModelScope.launch { c.files.setKind(a, kind, a.month) }

    /** Offline OCR of a receipt / payment screenshot. Null if reading is off or nothing could be read. */
    suspend fun readReceipt(a: Attachment): ReceiptParser.Result? {
        if (settings.value?.autoRead == false || !a.isImage) return null
        val bytes = attachmentBytes(a) ?: return null
        return runCatching { c.reader.receipt(bytes) }.getOrNull()
    }

    fun removeAttachment(a: Attachment) = viewModelScope.launch { c.files.remove(a) }
    suspend fun attachmentBytes(a: Attachment): ByteArray? = runCatching { c.files.bytes(a) }.getOrNull()

    private fun fileError(e: Throwable) = when (e) {
        is FileVault.TooLargeException -> "That file is over ${e.limitMb} MB. Try a smaller one."
        is FileVault.UnsupportedTypeException -> "Only photos, screenshots and PDF files can be attached."
        else -> "Couldn't attach that file. ${e.message.orEmpty()}"
    }

    // ---- Profile photo ----
    fun setProfilePhoto(uri: Uri) = viewModelScope.launch {
        runCatching { c.files.setProfilePhoto(uri) }
            .onSuccess { _photoVersion.value = System.currentTimeMillis() }
            .onFailure { say(fileError(it)) }
    }
    fun removeProfilePhoto() { c.files.removeProfilePhoto(); _photoVersion.value = System.currentTimeMillis() }
    suspend fun profilePhoto(): ByteArray? = c.files.profilePhoto()

    suspend fun topCategoryIds(type: TxType) = repo.topCategoryIds(type)
    suspend fun loadTxn(id: Long) = repo.txn(id)

    // ---- Categories, budgets, goals ----
    fun saveCategory(cat: Category) = viewModelScope.launch { repo.saveCategory(cat); say("Category saved") }
    fun archiveCategory(cat: Category, moveTo: Long?) = viewModelScope.launch { repo.archiveCategory(cat, moveTo); say("${cat.name} hidden") }
    fun setCategoryBudget(id: Long, paise: Long) = viewModelScope.launch { repo.setCategoryBudget(id, paise) }
    fun saveGoal(g: Goal) = viewModelScope.launch { repo.saveGoal(g) }
    fun deleteGoal(id: Long) = viewModelScope.launch { repo.deleteGoal(id) }

    /** Adding money to a goal also logs it as a Save & Invest entry, so reports stay truthful. */
    fun addToGoal(g: Goal, paise: Long) = viewModelScope.launch {
        val cat = data.value?.categories?.firstOrNull { it.key == "goal_savings" } ?: return@launch
        repo.saveTxn(Txn(amountPaise = paise, type = TxType.INVEST, categoryId = cat.id, epochMillis = System.currentTimeMillis(), payee = g.name, goalId = g.id))
        val updated = g.savedPaise + paise
        if (g.targetPaise in 1..updated && !g.done) _win.value = Win(g.emoji, "Goal reached!", "${g.name} is fully funded. Paisa ne kaam kiya!")
    }

    fun setRecurringActive(r: RecurringRule, on: Boolean) = viewModelScope.launch { repo.setRecurringActive(r, on) }
    fun deleteRecurring(r: RecurringRule) = viewModelScope.launch { repo.deleteRecurring(r.id); say("Stopped repeating") }

    // ---- Wealth: investments & net worth ----
    private val w get() = c.wealth
    val holdings: StateFlow<List<Holding>> = w.holdings.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val netItems: StateFlow<List<NetItem>> = w.items.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val netHistory: StateFlow<Map<YearMonth, Long>> = w.snapshots.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val schemes: StateFlow<List<Scheme>> get() = w.schemes
    private val _navBusy = MutableStateFlow(false)
    val navBusy: StateFlow<Boolean> = _navBusy.asStateFlow()
    val navUpdatedAt: Long get() = w.navUpdatedAt

    /** Opens the wealth screen: cached NAVs instantly, then a fresh download if older than 12 h. */
    fun openWealth(force: Boolean = false) = viewModelScope.launch {
        w.loadCache()
        val needsNav = force || holdings.value.any { it.kind == HoldingKind.MUTUAL_FUND } || w.schemes.value.isEmpty()
        if (!needsNav) return@launch
        _navBusy.value = true
        runCatching { w.refresh(maxAgeHours = 12, force = force) }
            .onSuccess { fresh -> if (fresh && force) say("Latest NAVs downloaded ✓") }
            .onFailure { e -> if (force || w.schemes.value.isEmpty()) say(e.message ?: "Couldn't update NAVs") }
        _navBusy.value = false
        w.snapshotNow()
    }
    fun closeWealth() = w.trimMemory()
    fun saveHolding(h: Holding) = viewModelScope.launch { w.saveHolding(h); w.snapshotNow() }
    fun deleteHolding(h: Holding) = viewModelScope.launch { w.deleteHolding(h.id); w.snapshotNow() }
    fun saveNetItem(i: NetItem) = viewModelScope.launch { w.saveItem(i); w.snapshotNow() }
    fun deleteNetItem(i: NetItem) = viewModelScope.launch { w.deleteItem(i.id); w.snapshotNow() }

    // ---- Automatic backup ----
    fun enableAutoBackup(folder: Uri, password: CharArray) = viewModelScope.launch {
        SecretBox(ctx, "backup").put(password); password.fill('\u0000')
        c.settings.setAutoBackup(true, folder.toString())
        AutoBackupWorker.schedule(ctx); AutoBackupWorker.runNow(ctx)
        say("Automatic backup is on. First backup is running now ✓")
    }
    fun disableAutoBackup() = viewModelScope.launch {
        c.settings.setAutoBackup(false, null); SecretBox(ctx, "backup").clear(); AutoBackupWorker.cancel(ctx)
        say("Automatic backup turned off")
    }
    fun backupNow() { AutoBackupWorker.runNow(ctx); say("Backing up…") }

    // ---- Crash reports ----
    fun pendingCrash(): String? = CrashReporter.pending(ctx)
    fun clearCrash() = CrashReporter.clear(ctx)

    // ---- Dues & bills ----
    fun saveDue(d: Due) = viewModelScope.launch { c.tools.saveDue(d) }
    fun settleDue(d: Due) = viewModelScope.launch { c.tools.settle(d); say("Settled with ${d.person} ✓") }
    fun settlePerson(person: String) = viewModelScope.launch { c.tools.settlePerson(person); say("All settled with $person 🤝") }
    fun deleteDue(d: Due) = viewModelScope.launch { c.tools.deleteDue(d.id) }
    fun saveBill(b: Bill) = viewModelScope.launch { c.tools.saveBill(b) }
    fun deleteBill(b: Bill) = viewModelScope.launch { c.tools.deleteBill(b.id); say("${b.name} removed") }
    fun markBillPaid(b: Bill, month: YearMonth) = viewModelScope.launch { c.tools.markPaid(b, month); say("${b.emoji} ${b.name} marked paid ✓") }

    // ---- Challenges ----
    fun startChallenge(id: String) = viewModelScope.launch {
        c.settings.startChallenge(id, LocalDate.now().toEpochDay()); say("Challenge started. You've got this 💪")
    }
    fun endChallenge() = viewModelScope.launch { c.settings.endChallenge(won = false) }

    // ---- Settings ----
    fun finishOnboarding(name: String, income: Long, budget: Long) = viewModelScope.launch {
        c.settings.completeOnboarding(name, income, budget)
        val s = c.settings.current(); DailyWorker.schedule(ctx, s.reminderHour, s.reminderMinute)
    }
    fun setName(v: String) = viewModelScope.launch { c.settings.setName(v) }
    fun setIncome(v: Long) = viewModelScope.launch { c.settings.setIncome(v) }
    fun setBudget(v: Long) = viewModelScope.launch { c.settings.setBudget(v) }
    fun setAge(v: Int) = viewModelScope.launch { c.settings.setAge(v) }
    fun setExpectedReturn(v: Int) = viewModelScope.launch { c.settings.setExpectedReturn(v) }
    fun setSmallTicket(v: Int) = viewModelScope.launch { c.settings.setSmallTicket(v) }
    fun setAppLock(v: Boolean) = viewModelScope.launch { c.settings.setAppLock(v) }
    fun setSecureScreen(v: Boolean) = viewModelScope.launch { c.settings.setSecureScreen(v) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { c.settings.setTheme(v) }
    fun setReminder(on: Boolean, h: Int, m: Int) = viewModelScope.launch {
        c.settings.setReminder(on, h, m); DailyWorker.schedule(ctx, h, m)
    }
    fun setAutoRead(on: Boolean) = viewModelScope.launch { c.settings.setAutoRead(on) }
    fun setSmsCapture(on: Boolean) = viewModelScope.launch { c.settings.setSmsCapture(on) }

    fun importSms(days: Int) = viewModelScope.launch {
        val r = SmsSupport.importInbox(ctx, days)
        say(if (r.added == 0) "No new bank transactions found in the last $days days" else "Found ${r.added} transactions. Review them in the inbox.")
    }

    // ---- Files (all via the system file picker; nothing is uploaded) ----
    fun exportCsv(uri: Uri) = viewModelScope.launch {
        val d = data.value ?: return@launch
        runCatching {
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(CsvWriter.write(d.txns, d.catMap).toByteArray()) } }
        }.onSuccess { say("Exported ${d.txns.size} entries ✓") }.onFailure { say("Export failed: ${it.message}") }
    }

    fun exportPdf(uri: Uri) = viewModelScope.launch {
        val r = selectedReport.value ?: return@launch
        val name = settings.value?.name.orEmpty()
        runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri, "wt")?.use { ReportPdf.write(it, r, name) } } }
            .onSuccess { say("Report saved ✓") }.onFailure { say("Couldn't save PDF: ${it.message}") }
    }

    fun exportBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        runCatching { c.backup.export(uri, password) }
            .onSuccess { say("Backup saved: $it entries, locked with your password ✓") }
            .onFailure { say("Backup failed: ${it.message}") }
    }

    fun importBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        runCatching { c.backup.import(uri, password) }
            .onSuccess { say("Restored $it entries ✓") }
            .onFailure { e -> say(if (e is BackupCrypto.WrongPasswordException) "Wrong password, or the file is damaged" else e.message ?: "Restore failed") }
    }

    fun wipeEverything(after: () -> Unit) = viewModelScope.launch {
        repo.wipeEverything(); c.files.wipe(); _photoVersion.value = System.currentTimeMillis(); c.settings.clearAll(); c.settings.ensureWisdomSeed(); repo.ensureSeeded(); after()
    }

    suspend fun currentSettings(): Settings = c.settings.settings.first()
}
