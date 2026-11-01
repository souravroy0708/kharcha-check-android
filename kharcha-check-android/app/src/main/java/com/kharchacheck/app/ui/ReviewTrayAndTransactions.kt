package com.kharchacheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharchacheck.app.AppController
import com.kharchacheck.app.Money
import com.kharchacheck.app.PayMode
import com.kharchacheck.app.TransactionEntry
import com.kharchacheck.app.WorthTag

/**
 * Review Tray Screen (Spec Page 4, 5 & 8):
 * "Auto-detected entries needing a check, one-tap confirm or edit. When the user fixes a category once,
 * offer 'always do this for this merchant'."
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewTrayScreen(app: AppController, onClose: () -> Unit) {
    val lang = app.language
    val items = app.reviewTrayItems

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(CardBg)
                .border(1.dp, PaperLine, RoundedCornerShape(50))
                .clickable(onClick = onClose)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Back", style = MaterialTheme.typography.labelLarge)
        }

        Text(t("Review Tray"), style = MaterialTheme.typography.headlineLarge)
        Text(
            "Low-confidence or new merchant SMS entries wait here. Pick a category once and Kharcha Check will offer to remember it forever.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )

        if (items.isEmpty()) {
            AppCard(title = "All caught up ✓") {
                Text("Zero entries waiting in the Review Tray.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        } else {
            items.forEach { tx ->
                var chosenCatId by remember(tx.id) { mutableLongStateOf(if (tx.categoryId == 14L) 1L else tx.categoryId) }
                var editedMerchant by remember(tx.id) { mutableStateOf(tx.merchantName) }

                AppCard(
                    title = "${Money.format(tx.amountPaise)} · ${tx.merchantName}",
                    subtitle = "${tx.mode.labelEn} · ${tx.prettyTime} · Ref: ${tx.refNo.ifBlank { "N/A" }}",
                ) {
                    AppField(
                        label = "Merchant Name",
                        value = editedMerchant,
                        onChange = { editedMerchant = it.take(40) },
                    )
                    Text("Pick Category:", style = MaterialTheme.typography.labelMedium, color = Muted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        app.categories.filter { it.id <= 11L }.forEach { cat ->
                            val on = chosenCatId == cat.id
                            SmallPill("${cat.emoji} ${cat.localizedName(lang)}", emerald = on) {
                                chosenCatId = cat.id
                            }
                        }
                    }
                    BigButton(
                        text = "✓ Confirm & Categorise",
                        emerald = true,
                        onClick = {
                            app.confirmReviewItem(tx, chosenCatId, editedMerchant)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Transactions Screen (Spec Page 8: "Search and filter by date, category, merchant, mode, account").
 */
@Composable
fun TransactionsTab(app: AppController) {
    val lang = app.language
    var selectedTxForEdit by remember { mutableStateOf<TransactionEntry?>(null) }

    val filtered = remember(
        app.monthTransactions,
        app.searchQuery,
        app.filterCategoryId,
        app.filterMode,
        app.filterAccountId,
    ) {
        app.monthTransactions.filter { tx ->
            val q = app.searchQuery.trim().lowercase()
            val matchQuery = q.isBlank() ||
                tx.merchantName.lowercase().contains(q) ||
                tx.note.lowercase().contains(q) ||
                tx.refNo.lowercase().contains(q)
            val matchCat = app.filterCategoryId == null || tx.categoryId == app.filterCategoryId
            val matchMode = app.filterMode == null || tx.mode == app.filterMode
            val matchAcct = app.filterAccountId == null || tx.accountId == app.filterAccountId
            matchQuery && matchCat && matchMode && matchAcct
        }
    }

    selectedTxForEdit?.let { tx ->
        EditTransactionDialog(
            tx = tx,
            app = app,
            onDismiss = { selectedTxForEdit = null },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("Transactions"), style = MaterialTheme.typography.headlineLarge)
            if (app.reviewTrayItems.isNotEmpty()) {
                SmallPill("Review Tray (${app.reviewTrayItems.size})", amber = true) {
                    app.reviewTrayOpen = true
                }
            }
        }

        // Search input
        OutlinedTextField(
            value = app.searchQuery,
            onValueChange = { app.searchQuery = it },
            placeholder = { Text("Search merchant, note, or UTR ref…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (app.searchQuery.isNotBlank()) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier.clickable { app.searchQuery = "" },
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedBorderColor = PrimaryAccent,
                unfocusedBorderColor = PaperLine,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Category Filter Row
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SmallPill("All Categories", emerald = app.filterCategoryId == null) { app.filterCategoryId = null }
            app.categories.forEach { cat ->
                SmallPill(
                    text = "${cat.emoji} ${cat.localizedName(lang)}",
                    emerald = app.filterCategoryId == cat.id,
                ) {
                    app.filterCategoryId = if (app.filterCategoryId == cat.id) null else cat.id
                }
            }
        }

        // Mode & Account Filter Row
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SmallPill("All Modes", amber = app.filterMode == null) { app.filterMode = null }
            PayMode.entries.forEach { m ->
                SmallPill(m.labelEn, amber = app.filterMode == m) {
                    app.filterMode = if (app.filterMode == m) null else m
                }
            }
            app.accounts.forEach { a ->
                SmallPill(a.displayTitle, coral = app.filterAccountId == a.id) {
                    app.filterAccountId = if (app.filterAccountId == a.id) null else a.id
                }
            }
        }

        AppCard(
            title = "${filtered.size} Entries",
            subtitle = "Tap any entry to tag Worth (Needed / Fine / Avoidable) or change category",
        ) {
            filtered.forEach { tx ->
                TransactionRowItem(
                    tx = tx,
                    category = app.categoryMap[tx.categoryId],
                    account = app.accountMap[tx.accountId],
                    lang = lang,
                    onClick = { selectedTxForEdit = tx },
                )
            }
        }

        Spacer(Modifier.height(72.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTransactionDialog(
    tx: TransactionEntry,
    app: AppController,
    onDismiss: () -> Unit,
) {
    val lang = app.language
    var catId by remember(tx.id) { mutableLongStateOf(tx.categoryId) }
    var worth by remember(tx.id) { mutableStateOf(tx.worth) }
    var merchant by remember(tx.id) { mutableStateOf(tx.merchantName) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Money.format(tx.amountPaise)} · ${tx.merchantName}", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppField(label = "Merchant", value = merchant, onChange = { merchant = it.take(40) })

                Text("Was it worth it?", style = MaterialTheme.typography.labelMedium, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(WorthTag.NEEDED, WorthTag.FINE, WorthTag.AVOIDABLE).forEach { w ->
                        SmallPill(
                            text = w.labelEn,
                            emerald = worth == w && w == WorthTag.NEEDED,
                            amber = worth == w && w == WorthTag.FINE,
                            coral = worth == w && w == WorthTag.AVOIDABLE,
                        ) { worth = w }
                    }
                }

                Text("Category:", style = MaterialTheme.typography.labelMedium, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.categories.filter { it.id <= 13L }.forEach { c ->
                        SmallPill("${c.emoji} ${c.localizedName(lang)}", emerald = catId == c.id) {
                            catId = c.id
                        }
                    }
                }
            }
        },
        confirmButton = {
            BigButton("Save Changes", emerald = true) {
                app.setWorth(tx.id, worth)
                app.confirmReviewItem(tx, catId, merchant)
                onDismiss()
            }
        },
        dismissButton = {
            Row {
                androidx.compose.material3.TextButton(
                    onClick = {
                        app.db.deleteTransaction(tx.id)
                        app.reload()
                        onDismiss()
                    }
                ) {
                    Text("Delete", color = Coral)
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Muted)
                }
            }
        },
    )
}
