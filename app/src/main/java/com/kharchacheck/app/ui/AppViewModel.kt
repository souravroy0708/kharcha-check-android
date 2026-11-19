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
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.PayMode
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
import com.kharchacheck.app.domain.BudgetAlerts
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
import com.kharchacheck.app.widget.KharchaWidget
import com.kharchacheck.app.work.DailyWorker
import com.kharchacheck.app.work.NudgeWorker
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
    /** Entries the screens show: hidden ("private") entries are left out unless the user has unlocked them. */
    val txns: List<Txn>,
    val budgets: Map<Long, Long>,
    val goals: List<Goal>,
    val report: MonthReport,
    val game: GameState,
    val challenge: ChallengeProgress?,
    val inbox: List<Txn>,
    val todaySpendPaise: Long,
    /** Every entry including hidden ones. Only for duplicate checks, never for display. */
    val allTxns: List<Txn> = txns,
    val hiddenIds: Set<Long> = emptySet(),
    val revealed: Boolean = false,
    /** Limits as the user set them ([budgets] adds last month's carry-over where it's switched on). */
    val baseBudgets: Map<Long, Long> = budgets,
    /** Category → yyyymm carry-over started; and what carried into this month. */
    val rollover: Map<Long, Int> = emptyMap(),
    val carry: Map<Long, Long> = emptyMap(),
) {
    fun isHidden(t: Txn) = t.id in hiddenIds
    val activeCategories: List<Category> get() = categories.filter { !it.archived }
}

/** A snackbar message, optionally with one action (usually "Undo"). */
data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

data class Win(val emoji: String, val title: String, val body: String)

private data class Raw(val s: Settings, val c: List<Category>, val t: List<Txn>, val b: Map<Long, Long>, val g: List<Goal>, val ro: Map<Long, Int> = emptyMap())

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val repo get() = c.finance
    private val ctx get() = getApplication<Application>()

    val settings: StateFlow<Settings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Hidden entries are shown only after unlocking, until the app goes to the background. */
    private val _revealed = MutableStateFlow(false)
    val revealed: StateFlow<Boolean> = _revealed.asStateFlow()

    val data: StateFlow<AppData?> = combine(c.settings.settings, repo.categories, repo.txns, repo.budgets.combine(repo.budgetRollover) { b, ro -> b to ro }, repo.goals) { s, cats, t, b, g -> Raw(s, cats, t, b.first, g, b.second) }
        .combine(_revealed) { raw, rev -> derive(raw, rev) }
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

    val accounts: StateFlow<List<com.kharchacheck.app.domain.Account>> = c.tools.accounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val trips: StateFlow<List<com.kharchacheck.app.domain.Trip>> = c.tools.trips.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveAccount(a: com.kharchacheck.app.domain.Account) = viewModelScope.launch { c.tools.saveAccount(a); say("Saved ✓") }
    fun deleteAccount(a: com.kharchacheck.app.domain.Account) = viewModelScope.launch { c.tools.deleteAccount(a.id); say("${a.name} removed. Its entries are kept.") }
    fun makeDefaultAccount(a: com.kharchacheck.app.domain.Account) = viewModelScope.launch { c.tools.makeDefault(a.id); say("${a.name} is now the default ✓") }
    fun linkUnlinked(a: com.kharchacheck.app.domain.Account) = viewModelScope.launch {
        val n = c.tools.linkUnlinked(a.id); say(if (n > 0) "Linked $n entries to ${a.name} ✓" else "Nothing to link")
    }
    fun saveTrip(t: com.kharchacheck.app.domain.Trip, onSaved: (Long) -> Unit = {}) = viewModelScope.launch { val id = c.tools.saveTrip(t); say("Saved ✓"); onSaved(id) }
    fun deleteTrip(t: com.kharchacheck.app.domain.Trip) = viewModelScope.launch {
        val photos = c.tools.deleteTrip(t.id)
        photos.forEach { runCatching { c.files.removeById(it) } }
        say("${t.name} removed. Its expense entries are kept.")
    }

    // ---- Trip details: bookings & costs, places, people, diary ----
    val tripItems: StateFlow<List<com.kharchacheck.app.domain.TripItem>> = c.tools.tripItems.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tripPlaces: StateFlow<List<com.kharchacheck.app.domain.TripPlace>> = c.tools.tripPlaces.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tripPeople: StateFlow<List<com.kharchacheck.app.domain.TripPerson>> = c.tools.tripPeople.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tripDiary: StateFlow<List<com.kharchacheck.app.domain.DiaryEntry>> = c.tools.tripDiary.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Saves a booking / cost item. If it's already recorded as an expense, that entry follows the new amount and date. */
    fun saveTripItem(i: com.kharchacheck.app.domain.TripItem) = viewModelScope.launch {
        c.tools.saveTripItem(i)
        val id = i.txnId
        if (id != null) repo.txn(id)?.let { t ->
            if (t.amountPaise != i.costPaise || (i.startAt != null && t.epochMillis != i.startAt)) {
                repo.saveTxn(t.copy(amountPaise = i.costPaise, epochMillis = i.startAt ?: t.epochMillis, updatedAt = System.currentTimeMillis()))
            }
        }
        say("Saved ✓")
    }
    fun deleteTripItem(i: com.kharchacheck.app.domain.TripItem) = viewModelScope.launch {
        c.tools.deleteTripItem(i.id); say(if (i.txnId != null) "Removed. Its expense entry is kept." else "Removed")
    }

    /**
     * Records a booking as an expense entry, tagged to the trip so it counts in the trip, budgets and reports.
     * Uses the booking's date, a category that fits (train → Travel, cab → Cabs…) and the default account.
     */
    fun recordTripItem(trip: com.kharchacheck.app.domain.Trip, i: com.kharchacheck.app.domain.TripItem) = viewModelScope.launch {
        val d = data.value ?: return@launch
        if (i.costPaise <= 0) { say("Add the cost first"); return@launch }
        val cat = d.categories.firstOrNull { it.key == i.kind.categoryKey && it.bucket.type == TxType.EXPENSE && !it.archived }
            ?: d.categories.firstOrNull { it.key == "travel" && it.bucket.type == TxType.EXPENSE }
            ?: d.categories.firstOrNull { it.bucket.type == TxType.EXPENSE } ?: return@launch
        val route = if (i.from.isNotBlank() || i.to.isNotBlank()) " ${i.from} → ${i.to}".trimEnd() else ""
        val note = listOf("${i.kind.label}$route", i.refNo.takeIf { it.isNotBlank() }?.let { "${i.kind.refLabel} $it" }, i.details.takeIf { it.isNotBlank() })
            .filterNotNull().joinToString(" · ").take(200)
        val now = System.currentTimeMillis()
        val t = Txn(
            amountPaise = i.costPaise, type = TxType.EXPENSE, categoryId = cat.id, epochMillis = i.startAt?.takeIf { it <= now } ?: now,
            zoneId = java.time.ZoneId.systemDefault().id, mode = PayMode.UPI, payee = i.title.take(40), note = note,
            tags = com.kharchacheck.app.domain.Trips.addTag("", trip.tag), source = Source.MANUAL, review = ReviewState.CONFIRMED,
            accountId = accounts.value.firstOrNull { !it.archived }?.id,
        )
        val txnId = repo.saveTxn(t)
        c.tools.saveTripItem(i.copy(paid = true, txnId = txnId))
        say("Added to expenses ✓ (${Money.format(i.costPaise)}, ${cat.name})")
    }

    fun saveTripPlace(p: com.kharchacheck.app.domain.TripPlace) = viewModelScope.launch { c.tools.saveTripPlace(p) }
    fun deleteTripPlace(p: com.kharchacheck.app.domain.TripPlace) = viewModelScope.launch { c.tools.deleteTripPlace(p.id) }
    fun saveTripPerson(p: com.kharchacheck.app.domain.TripPerson) = viewModelScope.launch { c.tools.saveTripPerson(p) }
    fun deleteTripPerson(p: com.kharchacheck.app.domain.TripPerson) = viewModelScope.launch { c.tools.deleteTripPerson(p.id) }
    fun saveDiary(d: com.kharchacheck.app.domain.DiaryEntry) = viewModelScope.launch { c.tools.saveDiary(d); say("Saved to your diary ✓") }
    fun deleteDiary(d: com.kharchacheck.app.domain.DiaryEntry) = viewModelScope.launch {
        c.tools.deleteDiary(d.id); d.photoIds.forEach { runCatching { c.files.removeById(it) } }; say("Diary entry removed")
    }
    /** Photos are shrunk, encrypted and kept as trip photos (never cleaned up as unused receipts). */
    fun addTripPhoto(uri: Uri, onAdded: (Attachment) -> Unit) = viewModelScope.launch {
        runCatching { c.files.addTripPhoto(uri) }.onSuccess(onAdded).onFailure { say(fileError(it)) }
    }
    fun removeTripPhoto(id: Long) = viewModelScope.launch { runCatching { c.files.removeById(id) } }
    fun setBudgetRollover(categoryId: Long, on: Boolean) = viewModelScope.launch {
        repo.setBudgetRollover(categoryId, on)
        say(if (on) "Unspent money will carry into next month ✓" else "Carry-over off")
    }

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
            if (s.onboarded && s.morningTip) NudgeWorker.scheduleAll(ctx, s.nudgeTimes(), s.nudgeSlotsOff)
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

    private fun derive(raw: Raw, revealed: Boolean): AppData {
        val hidden = raw.s.hiddenIds
        val r = if (revealed || hidden.isEmpty()) raw else raw.copy(t = raw.t.filterNot { it.id in hidden })
        val map = r.c.associateBy { it.id }
        val today = LocalDate.now()
        // Carry-over budgets: last month's unspent money is added to this month's limit, everywhere limits are used.
        val carry = com.kharchacheck.app.domain.BudgetRollover.carry(r.b, r.ro, r.t, YearMonth.from(today))
        val limits = if (carry.isEmpty()) r.b else r.b.mapValues { (id, v) -> v + (carry[id] ?: 0L) }
        val report = ReportEngine.build(YearMonth.from(today), r.t, map, reportConfig(r.s, limits))
        val game = Gamification.build(r.t, map, r.g, r.s.noSpendDays, r.s.challengesWon, r.s.monthlyBudgetPaise, today,
            practicedDays = r.s.practicedTipDays, planStepsDone = r.s.planDone.size,
            wishesSkipped = r.s.wishlist.count { it.decision == com.kharchacheck.app.domain.WishDecision.SKIPPED })
        val ch = Gamification.challenge(r.s.activeChallenge)?.let {
            Gamification.progress(it, LocalDate.ofEpochDay(r.s.challengeStartDay), r.t, map, r.s.noSpendDays, today)
        }
        val todaySpend = r.t.filter { it.date == today && it.type == TxType.EXPENSE && map[it.categoryId]?.bucket?.isSpend != false }.sumOf { it.amountPaise }
        return AppData(r.s, r.c, map, r.t, limits, r.g, report, game, ch, r.t.filter { it.review == ReviewState.NEEDS_REVIEW }, todaySpend,
            allTxns = raw.t, hiddenIds = hidden, revealed = revealed, baseBudgets = r.b, rollover = r.ro, carry = carry)
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
             split: SplitRequest? = null, hidden: Boolean? = null, after: () -> Unit = {}) = viewModelScope.launch {
        val nudge = budgetNudge(t)   // computed from the snapshot before this entry is added
        val id = repo.saveTxn(t.copy(review = ReviewState.CONFIRMED))
        nudge?.let { msg -> viewModelScope.launch { kotlinx.coroutines.delay(1_500); say(msg) } }  // after the "Added" message
        if (hidden != null) c.settings.setHidden(listOf(id), hidden)
        c.files.link(attachmentIds, id)
        if (split != null && split.names.isNotEmpty()) {
            c.tools.addSplit(id, split.names, split.eachPaise, t.payee.ifBlank { "Split bill" } + " · total " + com.kharchacheck.app.core.model.Money.format(split.totalPaise), t.date)
        }
        if (rememberPayeeRule && t.payee.isNotBlank()) repo.rememberPayee(t.payee, t.categoryId)
        if (repeatMonthly) repo.repeatMonthly(t)
        after()
    }

    fun delete(t: Txn) = viewModelScope.launch {
        repo.deleteTxn(t); c.settings.setHidden(listOf(t.id), false)
        _messages.trySend(UiMessage("Deleted ${com.kharchacheck.app.core.model.Money.format(t.amountPaise)}", "Undo") { restore(t) })
    }

    fun restore(t: Txn) = viewModelScope.launch { repo.saveTxn(t) }

    /** Warns when a new spend crosses 80% or 100% of its category budget, or of the monthly budget. */
    private fun budgetNudge(t: Txn): String? {
        val d = data.value ?: return null
        if (t.id != 0L || t.type != TxType.EXPENSE || t.month != YearMonth.now()) return null
        val cat = d.catMap[t.categoryId]?.takeIf { it.bucket.isSpend } ?: return null
        d.budgets[cat.id]?.let { limit ->
            val before = d.report.categories.firstOrNull { it.category.id == cat.id }?.paise ?: 0
            BudgetAlerts.crossing(before, before + t.amountPaise, limit)?.let { return BudgetAlerts.message(cat.name, before + t.amountPaise, limit, it) }
        }
        val monthly = d.settings.monthlyBudgetPaise
        val before = d.report.spendPaise
        return BudgetAlerts.crossing(before, before + t.amountPaise, monthly)?.let { BudgetAlerts.message("monthly", before + t.amountPaise, monthly, it) }
    }

    // ---- Bulk edit (Activity multi-select) ----
    fun deleteMany(list: List<Txn>) = viewModelScope.launch {
        repo.deleteMany(list); c.settings.setHidden(list.map { it.id }, false)
        _messages.trySend(UiMessage("Deleted ${list.size} entries", "Undo") { viewModelScope.launch { list.forEach { repo.saveTxn(it) } } })
    }
    fun recategorize(list: List<Txn>, cat: Category) = viewModelScope.launch {
        list.forEach { repo.saveTxn(it.copy(categoryId = cat.id, type = cat.bucket.type, review = ReviewState.CONFIRMED)) }
        _messages.trySend(UiMessage("Moved ${list.size} to ${cat.emoji} ${cat.name}", "Undo") { viewModelScope.launch { list.forEach { repo.saveTxn(it) } } })
    }
    fun confirmImported(ids: List<Long>, text: String) {
        _messages.trySend(UiMessage(text, "Undo") { viewModelScope.launch { repo.deleteIds(ids); say("Import undone") } })
    }

    /** One statement import = the entries it created (same source and the same creation time). */
    fun deleteImportBatch(ids: List<Long>) = viewModelScope.launch {
        val before = data.value?.allTxns?.filter { it.id in ids.toSet() }.orEmpty()
        repo.deleteIds(ids); c.settings.setHidden(ids, false)
        _messages.trySend(UiMessage("Deleted ${ids.size} imported entries", "Undo") { viewModelScope.launch { before.forEach { repo.saveTxn(it) } } })
    }

    // ---- Hidden ("private") entries ----
    fun setHidden(list: List<Txn>, hidden: Boolean) = viewModelScope.launch {
        c.settings.setHidden(list.map { it.id }, hidden)
        if (hidden && !_revealed.value) say(if (list.size == 1) "Entry hidden 🙈 Find it in Settings → Hidden entries" else "${list.size} entries hidden 🙈")
        else if (!hidden) say(if (list.size == 1) "Entry visible again" else "${list.size} entries visible again")
    }
    fun reveal() { _revealed.value = true }
    fun hideAgain() { _revealed.value = false }
    fun setMaskAmounts(v: Boolean) = viewModelScope.launch { c.settings.setMaskAmounts(v) }

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
    fun deleteGoal(id: Long) = viewModelScope.launch { repo.deleteGoal(id); c.settings.deleteGoalPlan(id) }

    /** Creates or updates a goal together with its plan (target = inflation-adjusted future cost). */
    fun savePlannedGoal(g: Goal, plan: com.kharchacheck.app.domain.GoalPlan) = viewModelScope.launch {
        val id = repo.saveGoal(g)
        c.settings.saveGoalPlan(plan.copy(goalId = id))
        say("${g.emoji} ${g.name} planned ✓")
    }

    /** Adding money to a goal also logs it as a Save & Invest entry, so reports stay truthful. */
    fun addToGoal(g: Goal, paise: Long) = viewModelScope.launch {
        val cat = data.value?.categories?.firstOrNull { it.key == "goal_savings" } ?: return@launch
        repo.saveTxn(Txn(amountPaise = paise, type = TxType.INVEST, categoryId = cat.id, epochMillis = System.currentTimeMillis(), payee = g.name, goalId = g.id))
        val updated = g.savedPaise + paise
        if (g.targetPaise in 1..updated && !g.done) _win.value = Win(g.emoji, "Goal reached!", "${g.name} is fully funded. Paisa ne kaam kiya!")
    }

    fun setRecurringActive(r: RecurringRule, on: Boolean) = viewModelScope.launch { repo.setRecurringActive(r, on) }
    fun saveRecurring(r: RecurringRule) = viewModelScope.launch { repo.saveRecurring(r); say("Updated ✓") }
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
        // Offline: keep working from the last saved prices, and say so only when it matters.
        if (!isOnline()) {
            if (force || w.schemes.value.isEmpty()) say("You're offline. Fund prices update when you're back online; everything else works offline.")
            w.snapshotNow()
            return@launch
        }
        _navBusy.value = true
        runCatching { w.refresh(maxAgeHours = 12, force = force) }
            .onSuccess { fresh -> if (fresh && force) say("Latest NAVs downloaded ✓") }
            .onFailure { e -> if (force || w.schemes.value.isEmpty()) say(e.message ?: "Couldn't update NAVs") }
        _navBusy.value = false
        w.snapshotNow()
    }
    fun closeWealth() = w.trimMemory()

    /** True when the phone has a working internet connection. Only fund prices (and optional voice) ever need it. */
    fun isOnline(): Boolean = runCatching {
        val cm = getApplication<Application>().getSystemService(android.net.ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)
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
    fun setBackupIncludeFiles(v: Boolean) = viewModelScope.launch { c.settings.setBackupIncludeFiles(v) }
    fun backupNow() { AutoBackupWorker.runNow(ctx); say("Backing up…") }

    // ---- Crash reports ----
    fun pendingCrash(): String? = CrashReporter.pending(ctx)
    fun clearCrash() = CrashReporter.clear(ctx)

    // ---- Wishlist (cooling-off before buying) ----
    fun addWish(name: String, pricePaise: Long, usesPerMonth: Int, wantSignals: Int) = viewModelScope.launch {
        val days = com.kharchacheck.app.domain.Wishlist.waitDays(pricePaise)
        c.settings.updateWishlist { it + com.kharchacheck.app.domain.WishItem(System.currentTimeMillis(), name.trim(), pricePaise, LocalDate.now(), days, usesPerMonth, wantSignals) }
        say("🛒 Parked for $days days. If you still want it then, buy it guilt-free.")
    }
    fun decideWish(id: Long, d: com.kharchacheck.app.domain.WishDecision) = viewModelScope.launch {
        val item = c.settings.current().wishlist.firstOrNull { it.id == id } ?: return@launch
        c.settings.updateWishlist { l -> l.map { if (it.id == id) it.copy(decision = d, decidedOn = LocalDate.now()) else it } }
        if (d == com.kharchacheck.app.domain.WishDecision.SKIPPED) say("🧘 Skipped ${item.name}: ${com.kharchacheck.app.core.model.Money.format(item.pricePaise)} stays yours. +30 XP")
    }
    fun extendWish(id: Long, days: Int) = viewModelScope.launch { c.settings.updateWishlist { l -> l.map { if (it.id == id) it.copy(waitDays = it.waitDays + days) else it } } }
    fun deleteWish(id: Long) = viewModelScope.launch { c.settings.updateWishlist { l -> l.filterNot { it.id == id } } }
    fun setWorkHours(h: Int) = viewModelScope.launch { c.settings.setWorkHours(h) }

    // ---- Tax-saving tracker ----
    fun setTaxProfile(regime: com.kharchacheck.app.domain.TaxRegime, slab: Int, selfSenior: Boolean, parentsSenior: Boolean) =
        viewModelScope.launch { c.settings.setTaxProfile(regime.name, slab, selfSenior, parentsSenior) }
    fun assignTax(t: Txn, bucket: com.kharchacheck.app.domain.TaxBucket?) = viewModelScope.launch { c.settings.assignTax(t.id, bucket) }
    fun setTaxManual(fy: Int, bucket: com.kharchacheck.app.domain.TaxBucket, paise: Long) = viewModelScope.launch { c.settings.setTaxManual("$fy|${bucket.name}", paise) }

    // ---- Subscriptions ----
    fun cancelSubscription(sub: com.kharchacheck.app.domain.Subscription) = viewModelScope.launch {
        c.settings.setSubscription(sub.key, LocalDate.now(), kept = false)
        say("✂️ ${sub.payee} marked cancelled: ${com.kharchacheck.app.core.model.Money.format(sub.yearlyPaise)} a year saved. You'll be warned if it charges again.")
    }
    fun keepSubscription(sub: com.kharchacheck.app.domain.Subscription, keep: Boolean) = viewModelScope.launch { c.settings.setSubscription(sub.key, null, keep) }
    fun remindSubscription(sub: com.kharchacheck.app.domain.Subscription) = viewModelScope.launch {
        c.tools.saveBill(Bill(name = sub.payee, emoji = sub.category?.emoji ?: "🔁", dueDay = sub.lastPaid.dayOfMonth, amountPaise = sub.monthlyPaise, remindDaysBefore = 3))
        say("🔔 You'll be reminded 3 days before ${sub.payee} renews")
    }
    /** From a credit-card statement's summary box. Updates the bill if one with the same name exists. */
    fun addCardBill(name: String, due: LocalDate, totalPaise: Long?) = viewModelScope.launch {
        val existing = c.tools.bills().firstOrNull { it.name.equals(name, true) }
        c.tools.saveBill((existing ?: Bill(name = name, emoji = "💳", dueDay = due.dayOfMonth)).copy(dueDay = due.dayOfMonth, amountPaise = totalPaise ?: 0))
        say("💳 Reminder set: ${totalPaise?.let { com.kharchacheck.app.core.model.Money.format(it) + " " } ?: ""}due on ${due.dayOfMonth} ${due.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}")
    }

    // ---- Daily tip, salary plan ----
    fun markTipPracticed() = viewModelScope.launch {
        val today = LocalDate.now().toEpochDay()
        val was = today in c.settings.current().practicedTipDays
        c.settings.togglePracticed(today)
        if (!was) say("Nice! Tip practised ✅ +15 XP")
    }
    fun togglePlanStep(id: String) = viewModelScope.launch {
        val was = id in c.settings.current().planDone
        c.settings.togglePlanStep(id)
        if (!was) say("Step done 🪜 +40 XP")
    }
    fun setPlanProfile(dependents: Boolean, risk: String, emergencySaved: Long) = viewModelScope.launch { c.settings.setPlanProfile(dependents, risk, emergencySaved) }
    fun setNudgeTime(slot: com.kharchacheck.app.domain.NudgeSlot, h: Int, m: Int) = viewModelScope.launch {
        c.settings.setNudgeTime(slot, h, m)
        val st = c.settings.current(); if (st.morningTip) NudgeWorker.scheduleAll(ctx, st.nudgeTimes(), st.nudgeSlotsOff)
    }
    fun setNudgeSlot(slot: com.kharchacheck.app.domain.NudgeSlot, on: Boolean) = viewModelScope.launch {
        c.settings.setNudgeSlot(slot, on)
        val st = c.settings.current(); if (st.morningTip) NudgeWorker.scheduleAll(ctx, st.nudgeTimes(), st.nudgeSlotsOff)
    }
    fun setMorningTip(on: Boolean, h: Int, m: Int) = viewModelScope.launch {
        c.settings.setMorningTip(on, h, m)
        val st = c.settings.current()
        if (on) NudgeWorker.scheduleAll(ctx, st.nudgeTimes(), st.nudgeSlotsOff) else NudgeWorker.cancelAll(ctx)
    }

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
        if (s.morningTip) NudgeWorker.scheduleAll(ctx, s.nudgeTimes(), s.nudgeSlotsOff)
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
