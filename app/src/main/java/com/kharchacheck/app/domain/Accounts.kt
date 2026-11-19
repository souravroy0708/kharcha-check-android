package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import java.time.YearMonth

/** Where money sits: each bank account, cash in hand, each credit card, each wallet. */
enum class AccountKind(val label: String, val emoji: String) {
    BANK("Bank account", "🏦"), CASH("Cash", "💵"), CARD("Credit card", "💳"), WALLET("Wallet", "📱");

    /** Picking an account also sets how it was paid, so the user taps once. */
    val payMode: PayMode? get() = when (this) { CASH -> PayMode.CASH; CARD -> PayMode.CARD; WALLET -> PayMode.WALLET; BANK -> null }
}

data class Account(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val kind: AccountKind,
    /** Balance on the day it was added. For a credit card this is negative: what you owe. */
    val openingPaise: Long = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

object Balances {
    /**
     * Running balance per account: opening + money in − money out. A transfer moves money from one account
     * to another (paying a card bill from the bank, ATM cash, moving to savings). Entries not linked to any
     * account don't change any balance.
     */
    fun of(accounts: List<Account>, txns: List<Txn>): Map<Long, Long> {
        val bal = accounts.associate { it.id to it.openingPaise }.toMutableMap()
        for (t in txns) {
            val from = t.accountId
            when (t.type) {
                TxType.INCOME -> if (from != null && from in bal) bal[from] = bal.getValue(from) + t.amountPaise
                TxType.EXPENSE, TxType.INVEST -> if (from != null && from in bal) bal[from] = bal.getValue(from) - t.amountPaise
                TxType.TRANSFER -> {
                    if (from != null && from in bal) bal[from] = bal.getValue(from) - t.amountPaise
                    val to = t.toAccountId
                    if (to != null && to in bal && to != from) bal[to] = bal.getValue(to) + t.amountPaise
                }
            }
        }
        return bal
    }

    /** Money in and out of one account in a month (transfers count on both sides). */
    fun flow(accountId: Long, txns: List<Txn>, month: YearMonth): Pair<Long, Long> {
        var inP = 0L; var outP = 0L
        for (t in txns) {
            if (t.month != month) continue
            if (t.accountId == accountId) { if (t.type == TxType.INCOME) inP += t.amountPaise else outP += t.amountPaise }
            if (t.type == TxType.TRANSFER && t.toAccountId == accountId && t.accountId != accountId) inP += t.amountPaise
        }
        return inP to outP
    }

    /** Cash, bank and wallets minus what's owed on cards. */
    fun net(accounts: List<Account>, balances: Map<Long, Long>): Long = accounts.filter { !it.archived }.sumOf { balances[it.id] ?: 0L }

    fun cardsOwed(accounts: List<Account>, balances: Map<Long, Long>): Long =
        accounts.filter { !it.archived && it.kind == AccountKind.CARD }.sumOf { -(balances[it.id] ?: 0L) }.coerceAtLeast(0)
}
