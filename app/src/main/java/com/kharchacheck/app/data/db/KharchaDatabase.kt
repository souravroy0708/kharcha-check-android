package com.kharchacheck.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kharchacheck.app.data.security.DbKeyStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [CategoryEntity::class, TxnEntity::class, BudgetEntity::class, GoalEntity::class, PayeeRuleEntity::class, RecurringEntity::class, AttachmentEntity::class, DueEntity::class, BillEntity::class, HoldingEntity::class, NetItemEntity::class, NetSnapshotEntity::class],
    version = 1,
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

    companion object {
        private const val NAME = "kharcha_v2.db"

        /**
         * Every schema change ships with a migration, because real users have data in version 1.
         * Recipe (see docs/DATABASE.md): bump `version` above, add e.g.
         *   object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) {
         *       db.execSQL("ALTER TABLE transactions ADD COLUMN foo INTEGER NOT NULL DEFAULT 0") } }
         * and commit the new app/schemas/…/2.json produced by the build. CI blocks schema changes without this.
         */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf()

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
                // Deliberately NO fallbackToDestructiveMigration(): a missing migration must fail loudly in
                // testing, never silently wipe someone's money history.
                .build()
        }
    }
}
