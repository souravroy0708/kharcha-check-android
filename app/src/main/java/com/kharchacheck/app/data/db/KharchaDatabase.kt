package com.kharchacheck.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kharchacheck.app.data.security.DbKeyStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [CategoryEntity::class, TxnEntity::class, BudgetEntity::class, GoalEntity::class, PayeeRuleEntity::class, RecurringEntity::class, AttachmentEntity::class, DueEntity::class, BillEntity::class, HoldingEntity::class, NetItemEntity::class, NetSnapshotEntity::class, AccountEntity::class, TripEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class KharchaDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun txns(): TxnDao
    abstract fun budgets(): BudgetDao
    abstract fun goals(): GoalDao
    abstract fun rules(): RuleDao
    abstract fun recurring(): RecurringDao
    abstract fun attachments(): AttachmentDao
    abstract fun dues(): DueDao
    abstract fun bills(): BillDao
    abstract fun wealth(): WealthDao
    abstract fun accounts(): AccountDao
    abstract fun trips(): TripDao

    companion object {
        const val NAME = "kharcha_v2.db"

        /**
         * Every schema change ships with a migration, because real users have data in version 1.
         * Recipe (see docs/DATABASE.md): bump `version` above, add e.g.
         *   object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) {
         *       db.execSQL("ALTER TABLE transactions ADD COLUMN foo INTEGER NOT NULL DEFAULT 0") } }
         * and commit the new app/schemas/…/2.json produced by the build. CI blocks schema changes without this.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        /** Size of the WAL file Android may keep after a checkpoint (it can never stay bigger than this). */
        private const val WAL_LIMIT_BYTES = 512 * 1024

        /**
         * The whole database file is AES-256 encrypted with SQLCipher. Its random key is wrapped by a
         * hardware-backed Android Keystore key, so a copied file is useless off this phone.
         * (The v1 file "kharcha_check.db" held demo data only and is not read.)
         */
        fun build(context: Context): KharchaDatabase {
            System.loadLibrary("sqlcipher")
            val keys = DbKeyStore(context)
            val passphrase = keys.passphraseOrNull() ?: run {
                // Key missing or unreadable (e.g. after a Keystore reset): the old file can never be opened.
                context.deleteDatabase(NAME)
                keys.createPassphrase()
            }
            return Room.databaseBuilder(context, KharchaDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(*MIGRATIONS)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Keep the write-ahead log small on disk; StorageManager also checkpoints and VACUUMs it.
                        runCatching { db.query("PRAGMA journal_size_limit = $WAL_LIMIT_BYTES").use { it.moveToFirst() } }
                    }
                })
                // Deliberately NO fallbackToDestructiveMigration(): a missing migration must fail loudly in
                // testing, never silently wipe someone's money history.
                .build()
        }
    }
}

/**
 * v1 → v2: compact transactions. The five small enums become 1-byte integer codes ([Codes]) and the India
 * time zone is stored as "". Every row is copied; nothing is lost. Unknown old values map to the same
 * fallbacks the app always used (Expense, Other, Not rated, Added by you, Confirmed).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amountPaise` INTEGER NOT NULL, " +
                "`type` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `epochMillis` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, " +
                "`mode` INTEGER NOT NULL, `payee` TEXT NOT NULL, `note` TEXT NOT NULL, `tags` TEXT NOT NULL, `worth` INTEGER NOT NULL, " +
                "`source` INTEGER NOT NULL, `review` INTEGER NOT NULL, `refNo` TEXT NOT NULL, `goalId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
        )
        fun cases(column: String, values: List<String>, fallback: Int) =
            "CASE `$column` " + values.mapIndexed { i, v -> "WHEN '$v' THEN $i" }.joinToString(" ") + " ELSE $fallback END"
        val type = cases("type", Codes.TYPE.map { it.name }, 0)
        val mode = cases("mode", Codes.MODE.map { it.name }, Codes.MODE.size - 1) // OTHER
        val worth = cases("worth", Codes.WORTH.map { it.name }, 0)
        val source = cases("source", Codes.SOURCE.map { it.name }, 0)
        val review = cases("review", Codes.REVIEW.map { it.name }, 0)
        db.execSQL(
            "INSERT INTO `transactions_new` (`id`,`amountPaise`,`type`,`categoryId`,`epochMillis`,`zoneId`,`mode`,`payee`,`note`,`tags`," +
                "`worth`,`source`,`review`,`refNo`,`goalId`,`createdAt`,`updatedAt`) " +
                "SELECT `id`,`amountPaise`,$type,`categoryId`,`epochMillis`," +
                "CASE `zoneId` WHEN '${Codes.HOME_ZONE}' THEN '' ELSE `zoneId` END,$mode,`payee`,`note`,`tags`," +
                "$worth,$source,$review,`refNo`,`goalId`,`createdAt`,`updatedAt` FROM `transactions`"
        )
        db.execSQL("DROP TABLE `transactions`")
        db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_epochMillis` ON `transactions` (`epochMillis`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_review` ON `transactions` (`review`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_refNo` ON `transactions` (`refNo`)")
    }
}

/** v2 → v3: accounts with balances, trips & events, carry-over budgets. Purely additive: no existing data changes. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accountId` INTEGER")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `toAccountId` INTEGER")
        db.execSQL("ALTER TABLE `budgets` ADD COLUMN `rolloverFrom` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`emoji` TEXT NOT NULL, `kind` INTEGER NOT NULL, `openingPaise` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, " +
                "`tag` TEXT NOT NULL, `budgetPaise` INTEGER NOT NULL, `startDay` INTEGER NOT NULL, `endDay` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, `ratePaise` INTEGER NOT NULL)"
        )
    }
}
