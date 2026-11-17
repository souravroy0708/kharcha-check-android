package com.kharchacheck.app.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.components.TxnRow
import com.kharchacheck.app.ui.nav.EditRoute
import com.kharchacheck.app.ui.theme.Kc

/** Private entries: only shown after fingerprint / screen-lock, and hidden again when the app leaves the screen. */
@Composable
fun HiddenScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val p = LocalPlatform.current
    fun unlock() = if (p.canUseDeviceLock()) p.authenticate("Show hidden entries") { vm.reveal() } else vm.reveal()

    SubScreen("🙈 Hidden entries", onBack = { nav.popBackStack() }) {
        if (!data.revealed) {
            item {
                KcCard {
                    Text("🔒", fontSize = 40.sp)
                    Text("Hidden entries are locked", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text)
                    Text("They're left out of every list, total, report, export and notification. Unlock with your fingerprint or screen lock to see them. " +
                        "They hide again as soon as you leave the app.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                    if (!p.canUseDeviceLock()) Text("⚠️ Your phone has no screen lock, so anyone holding it can open this. Set one in phone Settings → Security.",
                        style = MaterialTheme.typography.bodySmall, color = com.kharchacheck.app.ui.theme.Coral)
                    BigButton("Unlock") { unlock() }
                }
            }
            return@SubScreen
        }
        val hidden = data.allTxns.filter { data.isHidden(it) }.sortedByDescending { it.epochMillis }
        item {
            KcCard {
                Text("${hidden.size} hidden " + if (hidden.size == 1) "entry" else "entries", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text)
                val out = hidden.filter { it.type != TxType.INCOME }.sumOf { it.amountPaise }
                if (out > 0) Text("${Money.format(out)} out in total. While unlocked, they also show in lists and totals (marked 🙈).",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                Text("To hide something: open the entry and turn on \"Hide this entry\", or long-press entries in Activity and tap 🙈 Hide.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hidden.isNotEmpty()) OutlinedButton(onClick = { vm.setHidden(hidden, false) }) { Text("Unhide all") }
                    OutlinedButton(onClick = { vm.hideAgain(); nav.popBackStack() }) { Text("Lock now") }
                }
            }
        }
        if (hidden.isEmpty()) item { EmptyState("🙈", "Nothing hidden", "Entries you hide will appear here.") }
        items(hidden, key = { it.id }) { t ->
            KcCard(padding = 10.dp) {
                TxnRow(t, data.catMap[t.categoryId], hidden = true) { nav.navigate(EditRoute(id = t.id)) }
                Row(Modifier.padding(horizontal = 4.dp)) {
                    Spacer(Modifier.weight(1f))
                    Pill("👁 Unhide", Kc.colors.chip, Kc.colors.text) { vm.setHidden(listOf(t), false) }
                }
            }
        }
    }
}
