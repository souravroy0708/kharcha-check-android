package com.kharchacheck.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * On-device SQLite database (`kharcha_check.db`) with Android Keystore field encryption for notes (`CryptoVault`).
 * Stores all 8 Spec Entities:
 * 1. `transactions` (Money in integer paise, UTC epochMillis + timeZoneId)
 * 2. `accounts` (Bank, Card, Wallet, Cash + last4 + senderHeaders)
 * 3. `categories` (14 default categories, fixed vs flexible spend)
 * 4. `merchants` (Normalized display name, aliases, defaultCategoryId)
 * 5. `rules` (User & Learned merchant->category rules)
 * 6. `budgets` (Per-category monthly limit in paise)
 * 7. `recurring` (Detected & user-editable fixed costs: Rent, EMIs, Subscriptions)
 * 8. `month_reviews` (Completed 9-step month-end reviews)
 * Plus `unrecognised_sms` (Masked templates user can copy if bank wording changes, Spec Page 12).
 */
class LocalDb private constructor(private val appContext: Context) :
    SQLiteOpenHelper(appContext, "kharcha_check.db", null, 1) {

    private val vault = CryptoVault(appContext)

    companion object {
        @Volatile private var instance: LocalDb? = null

        fun get(context: Context): LocalDb =
            instance ?: synchronized(this) { instance ?: LocalDb(context.applicationContext).also { instance = it } }

        val DEFAULT_CATEGORIES = listOf(
            CategoryEntity(1L, "Food & Dining", "खाना और बाहर खाना", "🍛", 0xFFF59E0B, isFixed = false),
            CategoryEntity(2L, "Groceries", "राशन और सब्ज़ी", "🛒", 0xFF10B981, isFixed = false),
            CategoryEntity(3L, "Commute & Fuel", "आना-जाना और ईंधन", "🚕", 0xFF3B82F6, isFixed = false),
            CategoryEntity(4L, "Shopping", "शॉपिंग", "🛍️", 0xFFEC4899, isFixed = false),
            CategoryEntity(5L, "Bills & Utilities", "बिल और रिचार्ज", "⚡", 0xFF06B6D4, isFixed = true),
            CategoryEntity(6L, "Rent & Housing", "किराया और घर", "🏠", 0xFF6366F1, isFixed = true),
            CategoryEntity(7L, "EMIs & Loans", "EMI और लोन", "🏦", 0xFF8B5CF6, isFixed = true),
            CategoryEntity(8L, "Subscriptions", "सब्सक्रिप्शन", "📺", 0xFFA855F7, isFixed = true),
            CategoryEntity(9L, "Health & Medical", "स्वास्थ्य और दवा", "💊", 0xFF14B8A6, isFixed = false),
            CategoryEntity(10L, "Entertainment", "मनोरंजन", "🎬", 0xFFF97316, isFixed = false),
            CategoryEntity(11L, "Chai & Small Spends", "चाय और छोटे खर्च", "☕", 0xFFD97706, isFixed = false),
            CategoryEntity(12L, "Salary & Income", "सैलरी और आय", "💰", 0xFF059669, isFixed = false),
            CategoryEntity(13L, "Transfers & CC Bill", "ट्रांसफ़र और कार्ड बिल", "🔄", 0xFF64748B, isFixed = false),
            CategoryEntity(14L, "Uncategorised", "बिना श्रेणी", "❓", 0xFF94A3B8, isFixed = false),
        )

        val DEFAULT_ACCOUNTS = listOf(
            AccountEntity(1L, "HDFC Bank", AccountType.BANK, "4821", "HDFCBK"),
            AccountEntity(2L, "SBI Savings", AccountType.BANK, "9102", "SBIINB,SBIPSG,SBIUPI"),
            AccountEntity(3L, "ICICI Credit Card", AccountType.CARD, "7014", "ICICIB"),
            AccountEntity(4L, "Cash Wallet", AccountType.CASH, "", ""),
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_paise INTEGER NOT NULL,
                type TEXT NOT NULL,
                epoch_millis INTEGER NOT NULL,
                time_zone_id TEXT NOT NULL,
                account_id INTEGER NOT NULL,
                mode TEXT NOT NULL,
                merchant_id INTEGER NOT NULL DEFAULT 0,
                merchant_name TEXT NOT NULL,
                category_id INTEGER NOT NULL,
                note_enc TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL,
                sms_id TEXT NOT NULL DEFAULT '',
                ref_no TEXT NOT NULL DEFAULT '',
                confidence INTEGER NOT NULL DEFAULT 100,
                review_state TEXT NOT NULL,
                worth TEXT NOT NULL DEFAULT 'NONE'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tx_time ON transactions(epoch_millis DESC)")
        db.execSQL("CREATE INDEX idx_tx_review ON transactions(review_state)")

        db.execSQL(
            """
            CREATE TABLE accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                last4 TEXT NOT NULL DEFAULT '',
                sender_headers TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY,
                name_en TEXT NOT NULL,
                name_hi TEXT NOT NULL,
                emoji TEXT NOT NULL,
                color_hex INTEGER NOT NULL,
                parent_id INTEGER,
                is_fixed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE merchants (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                display_name TEXT NOT NULL UNIQUE,
                aliases TEXT NOT NULL DEFAULT '',
                default_category_id INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                match_type TEXT NOT NULL,
                pattern TEXT NOT NULL UNIQUE,
                category_id INTEGER NOT NULL,
                origin TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE budgets (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category_id INTEGER NOT NULL UNIQUE,
                monthly_limit_paise INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE month_reviews (
                month_key TEXT PRIMARY KEY,
                spent_paise INTEGER NOT NULL,
                income_paise INTEGER NOT NULL,
                saved_paise INTEGER NOT NULL,
                avoidable_total_paise INTEGER NOT NULL,
                intended_saving_paise INTEGER NOT NULL,
                targets_json TEXT NOT NULL,
                completed_at_millis INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE unrecognised_sms (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                masked_template TEXT NOT NULL,
                seen_at_millis INTEGER NOT NULL
            )
            """.trimIndent()
        )

        seedDefaults(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    private fun seedDefaults(db: SQLiteDatabase) {
        DEFAULT_CATEGORIES.forEach { c ->
            db.insertWithOnConflict(
                "categories",
                null,
                ContentValues().apply {
                    put("id", c.id)
                    put("name_en", c.nameEn)
                    put("name_hi", c.nameHi)
                    put("emoji", c.emoji)
                    put("color_hex", c.colorHex)
                    put("is_fixed", if (c.isFixed) 1 else 0)
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
        DEFAULT_ACCOUNTS.forEach { a ->
            db.insertWithOnConflict(
                "accounts",
                null,
                ContentValues().apply {
                    put("id", a.id)
                    put("name", a.name)
                    put("type", a.type.name)
                    put("last4", a.last4)
                    put("sender_headers", a.senderHeaders)
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    // ---------- Categories & Accounts ----------

    fun categories(): List<CategoryEntity> {
        val list = ArrayList<CategoryEntity>()
        readableDatabase.rawQuery(
            "SELECT id, name_en, name_hi, emoji, color_hex, is_fixed FROM categories ORDER BY id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    CategoryEntity(
                        id = c.getLong(0),
                        nameEn = c.getString(1),
                        nameHi = c.getString(2),
                        emoji = c.getString(3),
                        colorHex = c.getLong(4),
                        isFixed = c.getInt(5) == 1,
                    )
                )
            }
        }
        return list.ifEmpty { DEFAULT_CATEGORIES }
    }

    fun accounts(): List<AccountEntity> {
        val list = ArrayList<AccountEntity>()
        readableDatabase.rawQuery(
            "SELECT id, name, type, last4, sender_headers FROM accounts ORDER BY id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    AccountEntity(
                        id = c.getLong(0),
                        name = c.getString(1),
                        type = runCatching { AccountType.valueOf(c.getString(2)) }.getOrDefault(AccountType.BANK),
                        last4 = c.getString(3),
                        senderHeaders = c.getString(4),
                    )
                )
            }
        }
        return list.ifEmpty { DEFAULT_ACCOUNTS }
    }

    @Synchronized
    fun saveAccount(name: String, type: AccountType, last4: String, headers: String): Long {
        return writableDatabase.insert(
            "accounts",
            null,
            ContentValues().apply {
                put("name", name.trim())
                put("type", type.name)
                put("last4", last4.filter { it.isDigit() }.take(4))
                put("sender_headers", headers.trim().uppercase())
            }
        )
    }

    fun resolveAccountForSms(last4: String, bankHeader: String): Long {
        val all = accounts()
        if (last4.isNotBlank()) {
            all.firstOrNull { it.last4.equals(last4, ignoreCase = true) }?.let { return it.id }
        }
        if (bankHeader.isNotBlank()) {
            all.firstOrNull { it.senderHeaders.contains(bankHeader, ignoreCase = true) }?.let { return it.id }
        }
        return all.firstOrNull()?.id ?: 1L
    }

    // ---------- Transactions ----------

    private fun readTx(c: Cursor): TransactionEntry {
        val noteEnc = c.getString(10) ?: ""
        return TransactionEntry(
            id = c.getLong(0),
            amountPaise = c.getLong(1),
            type = runCatching { TxType.valueOf(c.getString(2)) }.getOrDefault(TxType.EXPENSE),
            epochMillis = c.getLong(3),
            timeZoneId = c.getString(4) ?: "Asia/Kolkata",
            accountId = c.getLong(5),
            mode = runCatching { PayMode.valueOf(c.getString(6)) }.getOrDefault(PayMode.UPI),
            merchantId = c.getLong(7),
            merchantName = c.getString(8) ?: "",
            categoryId = c.getLong(9),
            note = vault.decryptWithDeviceKey(noteEnc),
            tags = c.getString(11) ?: "",
            source = runCatching { TxSource.valueOf(c.getString(12)) }.getOrDefault(TxSource.MANUAL),
            smsId = c.getString(13) ?: "",
            refNo = c.getString(14) ?: "",
            confidence = c.getInt(15),
            reviewState = runCatching { ReviewState.valueOf(c.getString(16)) }.getOrDefault(ReviewState.CONFIRMED),
            worth = runCatching { WorthTag.valueOf(c.getString(17)) }.getOrDefault(WorthTag.NONE),
        )
    }

    fun allTransactions(): List<TransactionEntry> {
        val list = ArrayList<TransactionEntry>()
        readableDatabase.rawQuery(
            "SELECT id, amount_paise, type, epoch_millis, time_zone_id, account_id, mode, merchant_id, merchant_name, category_id, note_enc, tags, source, sms_id, ref_no, confidence, review_state, worth FROM transactions ORDER BY epoch_millis DESC, id DESC",
            null
        ).use { c ->
            while (c.moveToNext()) list.add(readTx(c))
        }
        return list
    }

    fun hasSmsId(smsId: String): Boolean {
        if (smsId.isBlank()) return false
        readableDatabase.rawQuery("SELECT 1 FROM transactions WHERE sms_id = ? LIMIT 1", arrayOf(smsId)).use { c ->
            return c.moveToFirst()
        }
    }

    @Synchronized
    fun insertOrUpdateTransaction(tx: TransactionEntry): Long {
        val cv = ContentValues().apply {
            put("amount_paise", tx.amountPaise)
            put("type", tx.type.name)
            put("epoch_millis", tx.epochMillis)
            put("time_zone_id", tx.timeZoneId)
            put("account_id", tx.accountId)
            put("mode", tx.mode.name)
            put("merchant_id", tx.merchantId)
            put("merchant_name", tx.merchantName.trim().ifBlank { "Manual Spend" })
            put("category_id", tx.categoryId)
            put("note_enc", vault.encryptWithDeviceKey(tx.note.trim()))
            put("tags", tx.tags.trim())
            put("source", tx.source.name)
            put("sms_id", tx.smsId)
            put("ref_no", tx.refNo)
            put("confidence", tx.confidence)
            put("review_state", tx.reviewState.name)
            put("worth", tx.worth.name)
        }
        return if (tx.id > 0L) {
            writableDatabase.update("transactions", cv, "id = ?", arrayOf(tx.id.toString()))
            tx.id
        } else {
            writableDatabase.insert("transactions", null, cv)
        }
    }

    @Synchronized
    fun deleteTransaction(id: Long) {
        writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun updateTransactionReview(id: Long, categoryId: Long, merchantName: String, state: ReviewState = ReviewState.CONFIRMED) {
        writableDatabase.update(
            "transactions",
            ContentValues().apply {
                put("category_id", categoryId)
                put("merchant_name", merchantName.trim())
                put("review_state", state.name)
            },
            "id = ?",
            arrayOf(id.toString())
        )
    }

    @Synchronized
    fun updateTransactionWorth(id: Long, worth: WorthTag) {
        writableDatabase.update(
            "transactions",
            ContentValues().apply { put("worth", worth.name) },
            "id = ?",
            arrayOf(id.toString())
        )
    }

    // ---------- Rules ("Always do this for this merchant" - Spec Page 5 & 6) ----------

    fun rules(): List<RuleEntity> {
        val list = ArrayList<RuleEntity>()
        readableDatabase.rawQuery("SELECT id, match_type, pattern, category_id, origin FROM rules ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    RuleEntity(
                        id = c.getLong(0),
                        matchType = c.getString(1),
                        pattern = c.getString(2),
                        categoryId = c.getLong(3),
                        origin = c.getString(4),
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun saveRule(pattern: String, categoryId: Long, origin: String = "LEARNED") {
        val clean = pattern.trim()
        if (clean.isBlank()) return
        writableDatabase.insertWithOnConflict(
            "rules",
            null,
            ContentValues().apply {
                put("match_type", "CONTAINS")
                put("pattern", clean)
                put("category_id", categoryId)
                put("origin", origin)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
        // Retroactively apply to existing transactions with matching merchant
        writableDatabase.execSQL(
            "UPDATE transactions SET category_id = ?, review_state = 'CONFIRMED' WHERE LOWER(merchant_name) LIKE ?",
            arrayOf<Any>(categoryId, "%${clean.lowercase()}%")
        )
    }

    @Synchronized
    fun deleteRule(id: Long) {
        writableDatabase.delete("rules", "id = ?", arrayOf(id.toString()))
    }

    // ---------- Budgets ----------

    fun budgets(): List<BudgetEntity> {
        val list = ArrayList<BudgetEntity>()
        readableDatabase.rawQuery("SELECT id, category_id, monthly_limit_paise FROM budgets ORDER BY category_id", null).use { c ->
            while (c.moveToNext()) {
                list.add(BudgetEntity(c.getLong(0), c.getLong(1), c.getLong(2)))
            }
        }
        return list
    }

    @Synchronized
    fun setBudget(categoryId: Long, monthlyLimitPaise: Long) {
        if (monthlyLimitPaise <= 0L) {
            writableDatabase.delete("budgets", "category_id = ?", arrayOf(categoryId.toString()))
        } else {
            writableDatabase.insertWithOnConflict(
                "budgets",
                null,
                ContentValues().apply {
                    put("category_id", categoryId)
                    put("monthly_limit_paise", monthlyLimitPaise)
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    // ---------- Month Reviews ----------

    fun monthReviews(): Map<String, MonthReviewEntity> {
        val map = LinkedHashMap<String, MonthReviewEntity>()
        readableDatabase.rawQuery(
            "SELECT month_key, spent_paise, income_paise, saved_paise, avoidable_total_paise, intended_saving_paise, targets_json, completed_at_millis FROM month_reviews ORDER BY month_key DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val e = MonthReviewEntity(
                    monthKey = c.getString(0),
                    spentPaise = c.getLong(1),
                    incomePaise = c.getLong(2),
                    savedPaise = c.getLong(3),
                    avoidableTotalPaise = c.getLong(4),
                    intendedSavingPaise = c.getLong(5),
                    targetsJson = c.getString(6),
                    completedAtMillis = c.getLong(7),
                )
                map[e.monthKey] = e
            }
        }
        return map
    }

    @Synchronized
    fun saveMonthReview(review: MonthReviewEntity) {
        writableDatabase.insertWithOnConflict(
            "month_reviews",
            null,
            ContentValues().apply {
                put("month_key", review.monthKey)
                put("spent_paise", review.spentPaise)
                put("income_paise", review.incomePaise)
                put("saved_paise", review.savedPaise)
                put("avoidable_total_paise", review.avoidableTotalPaise)
                put("intended_saving_paise", review.intendedSavingPaise)
                put("targets_json", review.targetsJson)
                put("completed_at_millis", review.completedAtMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    // ---------- Unrecognised Bank SMS Log (Spec Page 12) ----------

    @Synchronized
    fun logUnrecognisedSms(sender: String, maskedTemplate: String) {
        writableDatabase.insert(
            "unrecognised_sms",
            null,
            ContentValues().apply {
                put("sender", sender)
                put("masked_template", maskedTemplate.take(180))
                put("seen_at_millis", System.currentTimeMillis())
            }
        )
    }

    fun unrecognisedSmsList(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        readableDatabase.rawQuery("SELECT sender, masked_template FROM unrecognised_sms ORDER BY id DESC LIMIT 20", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0) to c.getString(1))
        }
        return out
    }

    // ---------- Encrypted Export / Import Snapshot ----------

    fun exportFullJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())
        root.put("accounts", JSONArray(accounts().map { it.toJson() }))
        root.put("rules", JSONArray(rules().map { it.toJson() }))
        root.put("budgets", JSONArray(budgets().map { it.toJson() }))
        root.put("transactions", JSONArray(allTransactions().map { it.toJson() }))
        root.put("monthReviews", JSONArray(monthReviews().values.map { it.toJson() }))
        return root.toString()
    }

    @Synchronized
    fun importFullJson(jsonString: String): Int {
        val root = JSONObject(jsonString)
        val txArr = root.optJSONArray("transactions") ?: JSONArray()
        val rulesArr = root.optJSONArray("rules") ?: JSONArray()
        val budgetsArr = root.optJSONArray("budgets") ?: JSONArray()
        val reviewsArr = root.optJSONArray("monthReviews") ?: JSONArray()

        for (i in 0 until rulesArr.length()) {
            val r = RuleEntity.fromJson(rulesArr.getJSONObject(i))
            saveRule(r.pattern, r.categoryId, r.origin)
        }
        for (i in 0 until budgetsArr.length()) {
            val b = BudgetEntity.fromJson(budgetsArr.getJSONObject(i))
            setBudget(b.categoryId, b.monthlyLimitPaise)
        }
        for (i in 0 until reviewsArr.length()) {
            saveMonthReview(MonthReviewEntity.fromJson(reviewsArr.getJSONObject(i)))
        }
        var importedTx = 0
        for (i in 0 until txArr.length()) {
            val tx = TransactionEntry.fromJson(txArr.getJSONObject(i)).copy(id = 0L)
            insertOrUpdateTransaction(tx)
            importedTx++
        }
        return importedTx
    }

    // ---------- Realistic Demo Dataset Loader (Personas from Spec Page 3: Ankit/Meera/Sanjay) ----------

    @Synchronized
    fun seedRealisticSampleData() {
        if (allTransactions().isNotEmpty()) return
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now()
        val currentMonth = YearMonth.from(now)
        val prev1 = currentMonth.minusMonths(1)
        val prev2 = currentMonth.minusMonths(2)
        val prev3 = currentMonth.minusMonths(3)

        fun ts(ym: YearMonth, day: Int, hour: Int, min: Int = 15): Long {
            val safeDay = day.coerceIn(1, ym.lengthOfMonth())
            return ym.atDay(safeDay).atTime(hour, min).atZone(zone).toInstant().toEpochMilli()
        }

        // Seed 3 months of history + current month so 3-month trailing averages, overspend flags, small leaks (< ₹200),
        // recurring fixed costs (Rent, EMI, Netflix), and Review Tray work out of the box!
        val months = listOf(prev3, prev2, prev1, currentMonth)
        for ((idx, ym) in months.withIndex()) {
            val isCurrent = ym == currentMonth
            // 1. Salary on the 1st (₹95,000)
            insertOrUpdateTransaction(
                TransactionEntry(
                    amountPaise = Money.fromRupees(95000.0),
                    type = TxType.INCOME,
                    epochMillis = ts(ym, 1, 10, 30),
                    accountId = 1L,
                    mode = PayMode.NET_BANKING,
                    merchantName = "Monthly Salary",
                    categoryId = 12L,
                    note = "Salary credit via NEFT",
                    source = TxSource.SMS,
                    refNo = "SAL${ym.monthValue}001",
                    reviewState = ReviewState.AUTO,
                )
            )
            // 2. Fixed Costs: House Rent (₹22,000 on 3rd), Home/Bike EMI (₹8,500 on 5th), Netflix (₹649 on 7th), Airtel/BESCOM (₹1,850 on 10th)
            insertOrUpdateTransaction(
                TransactionEntry(
                    amountPaise = Money.fromRupees(22000.0),
                    type = TxType.EXPENSE,
                    epochMillis = ts(ym, 3, 11, 0),
                    accountId = 1L,
                    mode = PayMode.UPI,
                    merchantName = "House Rent",
                    categoryId = 6L,
                    note = "2BHK Flat Rent",
                    source = TxSource.SMS,
                    refNo = "RENT${ym.monthValue}03",
                    reviewState = ReviewState.AUTO,
                    worth = WorthTag.NEEDED,
                )
            )
            insertOrUpdateTransaction(
                TransactionEntry(
                    amountPaise = Money.fromRupees(8500.0),
                    type = TxType.EXPENSE,
                    epochMillis = ts(ym, 5, 9, 30),
                    accountId = 1L,
                    mode = PayMode.NET_BANKING,
                    merchantName = "Loan EMI / Bajaj / HDFC Loan",
                    categoryId = 7L,
                    note = "Auto-debit EMI",
                    source = TxSource.SMS,
                    refNo = "EMI${ym.monthValue}05",
                    reviewState = ReviewState.AUTO,
                    worth = WorthTag.NEEDED,
                )
            )
            insertOrUpdateTransaction(
                TransactionEntry(
                    amountPaise = Money.fromRupees(649.0),
                    type = TxType.EXPENSE,
                    epochMillis = ts(ym, 7, 19, 45),
                    accountId = 3L,
                    mode = PayMode.CARD,
                    merchantName = "Netflix / Spotify / Prime",
                    categoryId = 8L,
                    source = TxSource.SMS,
                    refNo = "SUB${ym.monthValue}07",
                    reviewState = ReviewState.AUTO,
                )
            )
            insertOrUpdateTransaction(
                TransactionEntry(
                    amountPaise = Money.fromRupees(1850.0),
                    type = TxType.EXPENSE,
                    epochMillis = ts(ym, 8, 14, 20),
                    accountId = 1L,
                    mode = PayMode.UPI,
                    merchantName = "Airtel / Jio / Vi",
                    categoryId = 5L,
                    note = "Fiber + Mobile Postpaid",
                    source = TxSource.SMS,
                    refNo = "BILL${ym.monthValue}08",
                    reviewState = ReviewState.AUTO,
                    worth = WorthTag.NEEDED,
                )
            )

            // 3. Groceries (Blinkit, Zepto, DMart)
            val groceryAmounts = if (isCurrent) listOf(1450.0, 890.0, 2320.0, 640.0) else listOf(1600.0, 2100.0, 950.0)
            groceryAmounts.forEachIndexed { gIdx, amt ->
                insertOrUpdateTransaction(
                    TransactionEntry(
                        amountPaise = Money.fromRupees(amt),
                        type = TxType.EXPENSE,
                        epochMillis = ts(ym, 2 + gIdx * 5, 18, 20),
                        accountId = 1L,
                        mode = PayMode.UPI,
                        merchantName = if (gIdx % 2 == 0) "Blinkit" else "DMart",
                        categoryId = 2L,
                        source = TxSource.SMS,
                        refNo = "GROC${ym.monthValue}$gIdx",
                        reviewState = ReviewState.AUTO,
                    )
                )
            }

            // 4. Food & Dining (Higher in current month to trigger Overspend Flag #1 in Month-End Review!)
            val foodSpends = if (isCurrent) {
                listOf(
                    Triple(1, "Swiggy", 680.0),
                    Triple(2, "Zomato", 920.0),
                    Triple(4, "Swiggy", 540.0),
                    Triple(6, "Zomato", 1450.0),
                    Triple(9, "Swiggy", 790.0),
                    Triple(11, "EatClub / Box8", 460.0),
                    Triple(13, "Zomato", 1890.0),
                    Triple(15, "Swiggy", 1120.0),
                )
            } else {
                listOf(
                    Triple(4, "Swiggy", 520.0),
                    Triple(10, "Zomato", 780.0),
                    Triple(18, "Swiggy", 640.0),
                    Triple(24, "Zomato", 890.0),
                )
            }
            foodSpends.forEachIndexed { fIdx, (d, m, amt) ->
                insertOrUpdateTransaction(
                    TransactionEntry(
                        amountPaise = Money.fromRupees(amt),
                        type = TxType.EXPENSE,
                        epochMillis = ts(ym, d, 21, 15),
                        accountId = if (amt > 1000) 3L else 1L,
                        mode = if (amt > 1000) PayMode.CARD else PayMode.UPI,
                        merchantName = m,
                        categoryId = 1L,
                        note = if (amt > 1000) "Weekend dinner order" else "Late night food order",
                        source = TxSource.SMS,
                        refNo = "FD${ym.monthValue}$fIdx",
                        reviewState = ReviewState.AUTO,
                    )
                )
            }

            // 5. Shopping (Higher in current month to trigger Overspend Flag #2!)
            val shopSpends = if (isCurrent) {
                listOf(Triple(5, "Amazon", 3499.0), Triple(12, "Flipkart / Myntra", 2890.0))
            } else {
                listOf(Triple(14, "Amazon", 1499.0))
            }
            shopSpends.forEachIndexed { sIdx, (d, m, amt) ->
                insertOrUpdateTransaction(
                    TransactionEntry(
                        amountPaise = Money.fromRupees(amt),
                        type = TxType.EXPENSE,
                        epochMillis = ts(ym, d, 16, 10),
                        accountId = 3L,
                        mode = PayMode.CARD,
                        merchantName = m,
                        categoryId = 4L,
                        source = TxSource.SMS,
                        refNo = "SHP${ym.monthValue}$sIdx",
                        reviewState = ReviewState.AUTO,
                    )
                )
            }

            // 6. "Small Leaks" (< ₹200 payments: Chai, Rapido, Blinkit top-ups, Snacks — Spec Page 2 & 7)
            val smallLeaks = listOf(
                Triple(1, "Starbucks / Third Wave", 160.0),
                Triple(2, "Rapido", 85.0),
                Triple(3, "Chai Point Tapri", 40.0),
                Triple(4, "Swiggy", 175.0),
                Triple(5, "Rapido", 110.0),
                Triple(6, "Chai Point Tapri", 45.0),
                Triple(7, "Zepto", 180.0),
                Triple(8, "Rapido", 95.0),
                Triple(9, "Chai Point Tapri", 50.0),
                Triple(10, "Blinkit", 145.0),
            )
            smallLeaks.forEachIndexed { lIdx, (d, m, amt) ->
                val catId = when {
                    m.contains("Rapido") -> 3L
                    m.contains("Swiggy") -> 1L
                    m.contains("Zepto") || m.contains("Blinkit") -> 2L
                    else -> 11L
                }
                insertOrUpdateTransaction(
                    TransactionEntry(
                        amountPaise = Money.fromRupees(amt),
                        type = TxType.EXPENSE,
                        epochMillis = ts(ym, d, 17, 10),
                        accountId = 2L,
                        mode = PayMode.UPI,
                        merchantName = m,
                        categoryId = catId,
                        source = TxSource.SMS,
                        refNo = "SLK${ym.monthValue}$lIdx",
                        reviewState = ReviewState.AUTO,
                    )
                )
            }

            // 7. Credit Card Bill Transfer (Marked TRANSFER so never double-counted in expenses — Spec Page 5 & 12)
            insertOrUpdateTransaction(
                TransactionEntry(
                    amountPaise = Money.fromRupees(12400.0 + idx * 500),
                    type = TxType.TRANSFER,
                    epochMillis = ts(ym, 15, 12, 0),
                    accountId = 1L,
                    mode = PayMode.UPI,
                    merchantName = "ICICI Credit Card Bill (CRED)",
                    categoryId = 13L,
                    note = "Excluded from spend to avoid double-counting",
                    source = TxSource.SMS,
                    refNo = "CCBILL${ym.monthValue}",
                    reviewState = ReviewState.AUTO,
                )
            )
        }

        // 8. Two items waiting in the Review Tray (NeedsReview) for the user to confirm or set a rule in 1 tap!
        insertOrUpdateTransaction(
            TransactionEntry(
                amountPaise = Money.fromRupees(420.0),
                type = TxType.EXPENSE,
                epochMillis = System.currentTimeMillis() - 3_600_000L * 5,
                accountId = 1L,
                mode = PayMode.UPI,
                merchantName = "Sri Udupi Grand",
                categoryId = 14L,
                note = "UPI VPA sriudupi@okaxis",
                source = TxSource.SMS,
                refNo = "630591827364",
                confidence = ConfidenceLevel.LOW.score,
                reviewState = ReviewState.NEEDS_REVIEW,
            )
        )
        insertOrUpdateTransaction(
            TransactionEntry(
                amountPaise = Money.fromRupees(750.0),
                type = TxType.EXPENSE,
                epochMillis = System.currentTimeMillis() - 3_600_000L * 18,
                accountId = 2L,
                mode = PayMode.UPI,
                merchantName = "bluestone wellness",
                categoryId = 14L,
                note = "UPI VPA bluestone@ybl",
                source = TxSource.SMS,
                refNo = "630519283746",
                confidence = ConfidenceLevel.LOW.score,
                reviewState = ReviewState.NEEDS_REVIEW,
            )
        )

        // Seed 2 initial category budgets
        setBudget(1L, Money.fromRupees(5000.0)) // Food & Dining ₹5,000 limit
        setBudget(4L, Money.fromRupees(4000.0)) // Shopping ₹4,000 limit
    }

    @Synchronized
    fun wipeAll() {
        for (t in listOf("transactions", "accounts", "categories", "merchants", "rules", "budgets", "month_reviews", "unrecognised_sms")) {
            writableDatabase.delete(t, null, null)
        }
        seedDefaults(writableDatabase)
    }
}
