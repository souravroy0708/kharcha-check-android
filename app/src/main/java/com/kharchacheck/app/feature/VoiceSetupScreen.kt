package com.kharchacheck.app.feature

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import com.kharchacheck.app.domain.VoiceEntry
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.components.Voice
import com.kharchacheck.app.ui.components.rememberVoiceInput
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.core.model.Money

/**
 * Settings → Voice entry setup. Voice needs three things, and phones differ, so this screen checks each one live
 * (re-checked every time you come back from Settings) and gives the exact fix:
 *  1. Microphone permission for Kharcha Check.
 *  2. A speech service (usually the Google app or "Speech Recognition & Synthesis from Google").
 *  3. Microphone permission for THAT app, which records the audio. When it's missing Android says
 *     "Google voice search isn't available", the most common reason voice doesn't work.
 */
@Composable
fun VoiceSetupScreen(vm: AppViewModel, nav: NavHostController) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    var heard by remember { mutableStateOf<String?>(null) }
    val test = rememberVoiceInput("Say something like “250 for chai at Ramesh”") { heard = it }

    // Re-read on every tick (resume / after a permission answer).
    val appMic = remember(tick) { Voice.hasMic(context) }
    val service = remember(tick) { Voice.speechService(context) }
    val serviceAvailable = remember(tick) { Voice.serviceAvailable(context) }
    val onDevice = remember(tick) { Voice.onDeviceAvailable(context) }
    val online = remember(tick) { vm.isOnline() }
    val name = service?.label ?: "Google"

    SubScreen("Voice entry setup", onBack = { nav.popBackStack() }) {
        item {
            Text("Voice entry needs these. Each one is checked live; tap the button next to anything that isn’t ready.",
                style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        }
        item {
            Check(
                ok = appMic, title = "Microphone for Kharcha Check",
                okText = "Allowed. Used only while the 🎤 sheet is open.",
                badText = "Not allowed yet. Tap Allow, then choose “While using the app”. If nothing pops up, Android has blocked it: tap Open settings → Permissions → Microphone → Allow.",
                fix = "Allow" to { askMic.launch(Manifest.permission.RECORD_AUDIO) },
                fix2 = "Open settings" to { Voice.openAppSettings(context) },
            )
        }
        item {
            Check(
                ok = serviceAvailable || onDevice, title = "A speech service on this phone",
                okText = when {
                    onDevice && service != null -> "Ready: on-device recognition, with “$name” as backup."
                    onDevice -> "Ready: on-device recognition."
                    else -> "Ready: “$name”."
                },
                badText = "None found. Install “Speech Recognition & Synthesis from Google” (free), then come back.",
                fix = "Open Play Store" to { Voice.openSpeechServicesInStore(context) },
            )
        }
        if (service != null) item {
            Check(
                ok = service.enabled && service.hasMic, title = "Microphone for “$name”",
                okText = "Allowed. “$name” turns your speech into text.",
                badText = if (!service.enabled) "The “$name” app is turned off. Open its settings, tap Enable, then allow Microphone under Permissions."
                else "Not allowed. This is why Android says “voice search isn’t available”. Open “$name” settings → Permissions → Microphone → Allow while using the app.",
                fix = "Open $name settings" to { Voice.openAppSettings(context, service.packageName) },
            )
        }
        item {
            Check(
                ok = onDevice || online, title = "Offline or online",
                okText = if (onDevice) "Speech is turned into text on this phone. Works without internet." else "You’re online, so voice works now.",
                badText = "You’re offline and this phone has no on-device speech. Connect to the internet, or download English (India) for offline voice typing: Voice settings → Google voice typing → Offline speech recognition.",
                fix = "Open voice settings" to { Voice.openVoiceInputSettings(context) },
                warnOnly = true,
            )
        }
        item {
            KcCard {
                Text("Test it", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                Text("Say an expense like “450 for lunch at Paradise yesterday, by UPI”. Nothing is saved.",
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                Button(onClick = { heard = null; test?.invoke() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Haldi, contentColor = Ink)) { Text("🎤  Start test", fontWeight = FontWeight.Bold) }
                heard?.let { h ->
                    val r = VoiceEntry.parse(h)
                    Text("Heard: “$h”", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                    val parts = buildList {
                        r.amountPaise?.let { add("amount ${Money.format(it)}") }
                        r.categoryKey?.let { add("category $it") }
                        if (r.payee.isNotBlank()) add("payee ${r.payee}")
                        if (r.daysAgo == 1) add("yesterday")
                        r.mode?.let { add(it.name.lowercase().replace('_', ' ')) }
                    }
                    Text(if (parts.isEmpty()) "Voice works ✓ (no amount found in that sentence)" else "Voice works ✓ Understood: ${parts.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun Check(
    ok: Boolean, title: String, okText: String, badText: String,
    fix: Pair<String, () -> Unit>, fix2: Pair<String, () -> Unit>? = null, warnOnly: Boolean = false,
) {
    KcCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (ok) "✅" else if (warnOnly) "⚠️" else "❌", fontSize = 22.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                Text(if (ok) okText else badText, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
            }
        }
        if (!ok) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = fix.second, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text(fix.first) }
            fix2?.let { OutlinedButton(onClick = it.second, modifier = Modifier.weight(1f)) { Text(it.first) } }
        }
    }
}
