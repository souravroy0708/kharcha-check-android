package com.kharchacheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharchacheck.app.AppController

/**
 * 3-Screen Onboarding Flow (Spec Page 8):
 * 1. The Privacy Promise (no account, no server, no INTERNET permission — verifiable in Android Settings)
 * 2. Zero-Permission Fast Entry (Quick Add numpad, 1-tap paste, and share-to-app)
 * 3. Optional App Lock (4-digit PIN + FLAG_SECURE hide in recent apps)
 */
@Composable
fun OnboardingFlow(app: AppController, actions: PhoneActions) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var pinInput by rememberSaveable { mutableStateOf("") }
    var hideRecents by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Navy)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Progress dots (1 of 3, 2 of 3, 3 of 3)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= step) Emerald else Navy3)
                )
            }
        }

        when (step) {
            0 -> {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(Emerald),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Navy, modifier = Modifier.size(36.dp))
                }
                Text(
                    "Kharcha Check",
                    color = Emerald,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Paisa kahan gaya — ab pata chalega.",
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    "See where your salary went each month, with every byte of data staying on your phone.",
                    color = MutedOnDark,
                    style = MaterialTheme.typography.bodyLarge,
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Navy2)
                        .border(1.dp, NavyLine, RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Provable Privacy Promise", color = Emerald, style = MaterialTheme.typography.titleLarge)
                    PromiseBullet("No account, no cloud server, no analytics or ads.")
                    PromiseBullet("Ships WITHOUT the Android INTERNET permission — physically impossible to upload your data.")
                    PromiseBullet("No sensitive SMS permissions required — zero risk of Google Play Protect blocking.")
                    PromiseBullet("Neutral facts at month-end: no shaming, no lifestyle scores, no investment spam.")
                    TextButton(onClick = { actions.onOpenAppInfoPermissions() }) {
                        Text("Verify 'No Internet Permission' in Android Settings →", color = Amber)
                    }
                }

                BigButton("Next: How Easy It Is to Log Expenses →", emerald = true) {
                    step = 1
                }
            }

            1 -> {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(Emerald),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = Navy, modifier = Modifier.size(36.dp))
                }
                Text(
                    "Zero-Friction Expense Logging",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    "No background SMS reading needed. Kharcha Check gives you 3 lightning-fast ways to track expenses in seconds:",
                    color = MutedOnDark,
                    style = MaterialTheme.typography.bodyLarge,
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Navy2)
                        .border(1.dp, NavyLine, RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    FeatureRow("⚡ 3-Second Quick Add", "Type amount on the calculator numpad, tap a category chip, done! Defaults to current time and last-used account.")
                    FeatureRow("📋 1-Tap Smart Paste", "Copy any bank or UPI payment message, tap 'Paste' on the Home screen, and it automatically extracts amount, merchant, and category.")
                    FeatureRow("↗️ Share to Kharcha Check", "From Google Pay, PhonePe, Paytm, or Messages, tap 'Share' to Kharcha Check to record instantly without typing.")
                }

                BigButton("Next: Security & App Lock →", emerald = true) {
                    step = 2
                }
            }

            else -> {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(Emerald),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Navy, modifier = Modifier.size(36.dp))
                }
                Text(
                    "App Lock & Privacy Shield (Optional)",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    "Protect your financial summary if you hand your phone to someone else.",
                    color = MutedOnDark,
                    style = MaterialTheme.typography.bodyLarge,
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Navy2)
                        .border(1.dp, NavyLine, RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppField(
                        label = "Optional 4-digit PIN (Leave blank to skip)",
                        value = pinInput,
                        onChange = { pinInput = it.filter { c -> c.isDigit() }.take(4) },
                        keyboard = KeyboardType.NumberPassword,
                        placeholder = "e.g., 2580",
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Hide screen in Recent Apps (FLAG_SECURE)", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text("Blurs Kharcha Check in the Android app switcher and blocks screenshots.", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = hideRecents,
                            colors = SwitchDefaults.colors(checkedTrackColor = Emerald, checkedThumbColor = Navy),
                            onCheckedChange = { hideRecents = it },
                        )
                    }
                }

                BigButton("Start Using Kharcha Check →", emerald = true) {
                    if (pinInput.length == 4) {
                        app.prefs.pinHash = app.prefs.hashPin(pinInput)
                        app.prefs.appLock = true
                    }
                    app.prefs.hideInRecents = hideRecents
                    app.hideInRecents = hideRecents
                    app.prefs.onboardingDone = true
                    app.onboardingDone = true
                    app.unlocked = true
                    actions.onRequestNotificationPermission()
                }
            }
        }
    }
}

@Composable
private fun PromiseBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("✓", color = Emerald, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FeatureRow(title: String, desc: String) {
    Column {
        Text(title, color = Emerald, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(desc, color = MutedOnDark, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LockScreen(app: AppController) {
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Navy)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Box(Modifier.size(64.dp).clip(CircleShape).background(Emerald), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Navy, modifier = Modifier.size(32.dp))
        }
        Text("Unlock Kharcha Check", color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Text(
            text = if (wrong) "Incorrect PIN. Try again." else "Enter your 4-digit PIN",
            color = if (wrong) Coral else MutedOnDark,
            style = MaterialTheme.typography.bodyLarge,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (i < entered.length) Emerald else Navy3)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (k.isBlank()) Color.Transparent else Navy2)
                            .clickable(enabled = k.isNotBlank()) {
                                wrong = false
                                if (k == "⌫") {
                                    entered = entered.dropLast(1)
                                } else if (entered.length < 4) {
                                    entered += k
                                    if (entered.length == 4) {
                                        if (app.prefs.checkPin(entered)) {
                                            app.unlocked = true
                                        } else {
                                            wrong = true
                                            entered = ""
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(k, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
