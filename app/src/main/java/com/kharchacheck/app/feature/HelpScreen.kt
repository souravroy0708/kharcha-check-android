package com.kharchacheck.app.feature

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.kharchacheck.app.BuildConfig
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.nav.AccountsRoute
import com.kharchacheck.app.ui.nav.BudgetsRoute
import com.kharchacheck.app.ui.nav.EditRoute
import com.kharchacheck.app.ui.nav.ReportRoute
import com.kharchacheck.app.ui.nav.SettingsRoute
import com.kharchacheck.app.ui.nav.StatementsRoute
import com.kharchacheck.app.ui.nav.StorageRoute
import com.kharchacheck.app.ui.nav.TripsRoute
import com.kharchacheck.app.ui.nav.VoiceSetupRoute
import com.kharchacheck.app.ui.openTab
import com.kharchacheck.app.ui.theme.Kc
import java.time.YearMonth

private class HelpTopic(val emoji: String, val question: String, val steps: List<String>, val action: String? = null, val go: ((NavHostController) -> Unit)? = null)

private val TOPICS = listOf(
    HelpTopic("➕", "How do I add what I spent?", listOf(
        "Tap the big yellow + button at the bottom.",
        "Type the amount. You can type 120+45 and it adds them up.",
        "Pick what it was for (Food, Petrol, Rent…).",
        "Tap Save. That's it.",
    ), "Add one now") { it.navigate(EditRoute()) },
    HelpTopic("🎤", "Can I just say it instead of typing?", listOf(
        "On Home, tap 🎤 Speak (or long-press the app icon and choose “Speak it”).",
        "Say it like you'd tell a friend: “250 for chai at Ramesh, by cash”.",
        "The amount, category and shop fill in. Check them and tap Save.",
        "If the mic doesn't work, the setup check shows exactly what to allow.",
    ), "Check voice setup") { it.navigate(VoiceSetupRoute) },
    HelpTopic("📄", "Can it read my bank statement?", listOf(
        "Download your statement (PDF, Excel or CSV) from your bank app or email.",
        "Open Tools → Import statement and pick the file. Password-protected PDFs work too.",
        "Check the list it found, untick anything you don't want, and tap Add.",
        "Entries already in the app are skipped, so nothing is added twice.",
    ), "Import a statement") { it.navigate(StatementsRoute(YearMonth.now().toString())) },
    HelpTopic("📱", "Can it read a GPay / PhonePe screenshot?", listOf(
        "Take a screenshot of the payment screen.",
        "Tap Share on the screenshot and choose Kharcha Check.",
        "The amount and who you paid fill in. Pick the category and Save.",
    )),
    HelpTopic("✏️", "I made a mistake. How do I fix or delete an entry?", listOf(
        "Open the Activity tab and tap the entry.",
        "Change anything and tap Save changes.",
        "To delete, tap the bin icon. Deleted by mistake? Tap Undo at the bottom straight away.",
    )),
    HelpTopic("🎯", "How do I stop overspending?", listOf(
        "Set a monthly budget in Settings, and limits for categories like Food delivery in Budgets.",
        "You'll get a gentle alert at 80% and at 100%.",
        "Home shows “Safe to spend per day” so you know what's left.",
    ), "Open budgets") { it.navigate(BudgetsRoute) },
    HelpTopic("📊", "Where did my money go this month?", listOf(
        "Open the Monthly report from Home.",
        "See spending by category, Needs vs Wants vs Leaks, and your top shops.",
        "Swipe to previous months to compare.",
    ), "Open report") { it.navigate(ReportRoute) },
    HelpTopic("🏦", "Can I see the balance in my bank, cash and cards?", listOf(
        "Open Tools → Accounts & balances and add each account with today's balance.",
        "When you add a spend, pick which account it came from.",
        "Paying a card bill or taking ATM cash? Add it as a Transfer.",
    ), "Open accounts") { it.navigate(AccountsRoute) },
    HelpTopic("✈️", "How do I track a trip?", listOf(
        "Open Tools → Trips & events and add the trip with its dates.",
        "Add tickets and bookings, and log each spend in one line (“240 chai at the beach”).",
        "Add who's going to see the cost per person and who owes whom.",
        "Write a diary with photos to look back on later in Memories.",
    ), "Open trips") { it.navigate(TripsRoute) },
    HelpTopic("💾", "What if I lose or change my phone?", listOf(
        "Your entries are only on this phone, so turn on backup.",
        "Settings → Backup → choose a folder (Google Drive works) and set a password you'll remember.",
        "On the new phone: install Kharcha Check → Settings → Restore → pick the backup → enter the password.",
        "Without a backup, entries can't be recovered. No copy exists anywhere else.",
    ), "Set up backup") { it.openTab(SettingsRoute) },
    HelpTopic("🙈", "Someone is looking at my screen. Can I hide amounts?", listOf(
        "Tap the 👁 eye at the top of Home. Big amounts show as ₹ ••••.",
        "Tap it again to show them.",
        "For more privacy: Settings → App lock (fingerprint) and Hidden entries.",
    )),
    HelpTopic("🔠", "The text is too small.", listOf(
        "Settings → Text size → choose Large or Extra large.",
        "It changes the whole app straight away.",
    ), "Open settings") { it.openTab(SettingsRoute) },
    HelpTopic("📶", "Does it need internet?", listOf(
        "No. Everything works offline.",
        "Only mutual fund prices update online; the last prices are kept for offline use.",
    ), "See storage & offline") { it.navigate(StorageRoute) },
)

/** Settings → Help & how-to: the questions people actually ask, answered in plain words, each with a "show me" button. */
@Composable
fun HelpScreen(nav: NavHostController) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(-1) }
    SubScreen("Help & how-to", onBack = { nav.popBackStack() }) {
        item {
            Text("Tap a question to see the steps.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        }
        TOPICS.forEachIndexed { i, t ->
            item(key = "help$i") {
                KcCard(Modifier.animateContentSize(), onClick = { open = if (open == i) -1 else i }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.emoji, fontSize = 24.sp)
                        Text(t.question, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        Text(if (open == i) "−" else "+", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted)
                    }
                    if (open == i) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            t.steps.forEachIndexed { n, step ->
                                Row {
                                    Text("${n + 1}.", style = MaterialTheme.typography.bodyLarge, color = Kc.colors.muted, modifier = Modifier.padding(end = 8.dp))
                                    Text(step, style = MaterialTheme.typography.bodyLarge, color = Kc.colors.text)
                                }
                            }
                        }
                        if (t.action != null && t.go != null) OutlinedButton(onClick = { t.go.invoke(nav) }, modifier = Modifier.fillMaxWidth()) { Text(t.action) }
                    }
                }
            }
        }
        item {
            KcCard(padding = 14.dp) {
                Text("Still stuck?", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                Text("Write to us in any language. We read every message.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                OutlinedButton(onClick = {
                    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.SUPPORT_EMAIL))
                        .putExtra(Intent.EXTRA_SUBJECT, "Kharcha Check help (v${BuildConfig.VERSION_NAME})")
                    runCatching { context.startActivity(i) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Email support") }
            }
        }
    }
}
