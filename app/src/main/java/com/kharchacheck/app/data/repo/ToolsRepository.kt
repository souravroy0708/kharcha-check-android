package com.kharchacheck.app.data.repo

import com.kharchacheck.app.data.db.KharchaDatabase
import com.kharchacheck.app.data.db.toEntity
import com.kharchacheck.app.data.db.toModel
import androidx.room.withTransaction
import com.kharchacheck.app.domain.Account
import com.kharchacheck.app.domain.Bill
import com.kharchacheck.app.domain.Trip
import com.kharchacheck.app.domain.Due
import com.kharchacheck.app.domain.DueDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/** Split dues (who owes whom) and bill due-date reminders. */
class ToolsRepository(private val db: KharchaDatabase) {
    val dues: Flow<List<Due>> = db.dues().observe().map { l -> l.map { it.toModel() } }
    val bills: Flow<List<Bill>> = db.bills().observe().map { l -> l.map { it.toModel() } }

    suspend fun saveDue(d: Due) { db.dues().upsert(d.toEntity()) }
    suspend fun deleteDue(id: Long) = db.dues().delete(id)
    suspend fun settle(d: Due) { db.dues().upsert(d.copy(settled = true).toEntity()) }
    suspend fun settlePerson(person: String) = db.dues().settlePerson(person)

    /** One "owes you" per person for a split bill, linked to the entry. */
    suspend fun addSplit(txnId: Long, names: List<String>, eachPaise: Long, note: String, date: LocalDate) {
        names.forEach { n -> db.dues().upsert(Due(person = n, amountPaise = eachPaise, direction = DueDirection.THEY_OWE, note = note, date = date, txnId = txnId).toEntity()) }
    }

    suspend fun bills(): List<Bill> = db.bills().all().map { it.toModel() }
    suspend fun saveBill(b: Bill) { db.bills().upsert(b.toEntity()) }
    suspend fun deleteBill(id: Long) = db.bills().delete(id)
    suspend fun markPaid(b: Bill, month: YearMonth) { db.bills().upsert(b.copy(paidMonth = month).toEntity()) }

    // ---- Accounts (balances) ----
    val accounts: Flow<List<Account>> = db.accounts().observe().map { l -> l.map { it.toModel() } }
    suspend fun saveAccount(a: Account): Long {
        val order = if (a.id == 0L) (db.accounts().all().maxOfOrNull { it.sortOrder } ?: -1) + 1 else a.sortOrder
        return db.accounts().upsert(a.copy(sortOrder = order).toEntity())
    }
    suspend fun deleteAccount(id: Long) = db.withTransaction { db.accounts().unlinkFrom(id); db.accounts().unlinkTo(id); db.accounts().delete(id) }
    suspend fun makeDefault(id: Long) = db.accounts().makeFirst(id)
    suspend fun linkUnlinked(accountId: Long): Int = db.accounts().linkUnlinked(accountId)

    // ---- Trips & events ----
    val trips: Flow<List<Trip>> = db.trips().observe().map { l -> l.map { it.toModel() } }
    suspend fun saveTrip(t: Trip) { db.trips().upsert(t.toEntity()) }
    suspend fun deleteTrip(id: Long) = db.trips().delete(id)
}
