package com.kharchacheck.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc

/**
 * Voice entry with Android's speech recogniser, inside our own small listening sheet.
 *
 * Why not Google's "voice search" screen: on many phones (Google app disabled, no mic access for it, offline mode)
 * it only says "Google voice search isn't available". Running the recogniser ourselves works on those phones and lets
 * us explain exactly what to fix.
 *
 * Privacy: the microphone permission is asked only when the user taps a 🎤, with a short explanation first.
 * The mic is on only while the listening sheet is open. Recognition runs on the phone where Android supports it
 * (Android 12+ on-device recogniser); otherwise the phone's speech service (usually Google) converts it.
 * Kharcha Check never saves or uploads audio. It only receives the text.
 */
object Voice {
    fun hasMic(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun serviceAvailable(context: Context) = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    fun onDeviceAvailable(context: Context) = Build.VERSION.SDK_INT >= 31 &&
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    fun recognizerIntent(context: Context, prompt: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        // Indian English understands rupees, lakh, Indian names and everyday Hinglish ("kal 300 ka petrol").
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    fun openAppSettings(context: Context) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    /** "Speech Recognition & Synthesis from Google" provides voice typing on most Android phones. */
    fun openSpeechServicesInStore(context: Context) {
        val id = "com.google.android.tts"
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.onFailure { runCatching { context.startActivity(web) } }
    }

    fun openVoiceInputSettings(context: Context) {
        val tries = listOf(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS), Intent(Settings.ACTION_INPUT_METHOD_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        for (i in tries) if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }

    internal fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }
}

private enum class Phase { EXPLAIN, BLOCKED, NO_SERVICE, LISTENING, ERROR }

private class VoiceProblem(val title: String, val body: String, val steps: List<String> = emptyList(), val fix: Fix? = null)
private enum class Fix { VOICE_SETTINGS, STORE }

/**
 * Returns a function that opens the listening sheet and delivers what was said to [onText].
 * The first tap explains and asks for the microphone; if anything is missing, the sheet shows how to fix it.
 * Never null (kept nullable so callers stay simple).
 */
@Composable
fun rememberVoiceInput(prompt: String, onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onText)
    var phase by remember { mutableStateOf<Phase?>(null) }
    var deniedOnce by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<VoiceProblem?>(null) }

    fun listenOrSetup() {
        phase = when {
            !Voice.hasMic(context) -> Phase.EXPLAIN
            !Voice.serviceAvailable(context) -> Phase.NO_SERVICE
            else -> Phase.LISTENING
        }
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listenOrSetup()
        else {
            val activity = with(Voice) { context.findActivity() }
            val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            // Android stops showing its dialog after two "Don't allow" taps: then only Settings can turn it on.
            phase = if (canAskAgain) Phase.EXPLAIN.also { deniedOnce = true } else Phase.BLOCKED
        }
    }

    // Google's own screen, offered only as a fallback when our recogniser fails.
    val googleScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { latest(it) }
        }
    }

    when (phase) {
        null -> Unit
        Phase.EXPLAIN -> GuideDialog(
            icon = "🎤", title = "Speak instead of typing",
            body = if (deniedOnce) "Voice entry needs the microphone. You tapped “Don’t allow”, so nothing was turned on."
            else "To hear you, Kharcha Check needs the microphone. It listens only while the 🎤 sheet is open and never saves or uploads recordings. Only the words reach the app.",
            steps = listOf("Tap “Allow microphone” below", "Choose “While using the app”", "Say something like “250 for chai at Ramesh”"),
            primary = "Allow microphone", onPrimary = { askMic.launch(Manifest.permission.RECORD_AUDIO) },
            onDismiss = { phase = null },
        )
        Phase.BLOCKED -> {
            // Coming back from Settings with the mic allowed goes straight to listening.
            LifecycleResumeEffect(Unit) {
                if (Voice.hasMic(context)) listenOrSetup()
                onPauseOrDispose { }
            }
            GuideDialog(
                icon = "🔇", title = "Microphone is off for Kharcha Check",
                body = "Android won’t ask again, so turn it on in Settings. It takes four taps:",
                steps = listOf("Tap “Open settings” below", "Tap Permissions", "Tap Microphone", "Choose “Allow only while using the app”, then come back"),
                primary = "Open settings", onPrimary = { Voice.openAppSettings(context) },
                onDismiss = { phase = null },
            )
        }
        Phase.NO_SERVICE -> GuideDialog(
            icon = "🗣️", title = "Voice typing isn’t set up on this phone",
            body = "Android needs a speech service to turn your voice into text. Most phones use Google’s free one.",
            steps = listOf(
                "Install or update “Speech Recognition & Synthesis from Google” (button below)",
                "Make sure the Google app isn’t disabled: Settings → Apps → Google → Enable",
                "Settings → System (or Additional settings) → Languages & input → Voice input → choose Google",
                "Come back and tap 🎤 again",
            ),
            primary = "Open Play Store", onPrimary = { Voice.openSpeechServicesInStore(context) },
            secondary = "Try again", onSecondary = { listenOrSetup() },
            onDismiss = { phase = null },
        )
        Phase.LISTENING -> ListeningDialog(
            prompt = prompt,
            onText = { phase = null; latest(it) },
            onProblem = { code, onDevice ->
                if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { phase = Phase.EXPLAIN }
                else { problem = problemFor(code, onDevice); phase = Phase.ERROR }
            },
            onDismiss = { phase = null },
        )
        Phase.ERROR -> {
            val p = problem ?: VoiceProblem("Voice typing stopped", "Please try again.")
            GuideDialog(
                icon = "🎤", title = p.title, body = p.body, steps = p.steps,
                primary = "Try again", onPrimary = { listenOrSetup() },
                secondary = when (p.fix) { Fix.VOICE_SETTINGS -> "Open voice settings"; Fix.STORE -> "Open Play Store"; null -> "Use Google’s voice screen" },
                onSecondary = {
                    when (p.fix) {
                        Fix.VOICE_SETTINGS -> Voice.openVoiceInputSettings(context)
                        Fix.STORE -> Voice.openSpeechServicesInStore(context)
                        null -> { phase = null; runCatching { googleScreen.launch(Voice.recognizerIntent(context, prompt)) } }
                    }
                },
                onDismiss = { phase = null },
            )
        }
    }

    return { listenOrSetup() }
}

private fun problemFor(code: Int, onDevice: Boolean): VoiceProblem = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
        VoiceProblem("Didn’t catch that", "Tap Try again and speak right after the beep, a little closer to the phone. Example: “450 for lunch at Paradise”.")
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, 11 ->
        VoiceProblem(
            "Voice typing needs internet on this phone",
            "Your phone’s speech service couldn’t reach its server. Connect to the internet, or download English (India) to use voice typing offline:",
            listOf("Tap “Open voice settings” below", "Open Google voice typing (or Speech Recognition by Google)", "Offline speech recognition → All → download English (India)"),
            Fix.VOICE_SETTINGS,
        )
    12, 13 -> // ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE
        VoiceProblem(
            "English (India) isn’t ready for voice typing",
            "Download the language once and voice typing also works offline:",
            listOf("Tap “Open voice settings” below", "Open Google voice typing", "Languages → add English (India), then Offline speech recognition → download it"),
            Fix.VOICE_SETTINGS,
        )
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, 10 ->
        VoiceProblem("Voice typing is busy", "Another app is using voice typing. Wait a second and tap Try again.")
    SpeechRecognizer.ERROR_AUDIO ->
        VoiceProblem("Couldn’t use the microphone", "Another app (a call, a recorder or a voice assistant) may be using it. Close that and tap Try again.")
    else -> VoiceProblem(
        "Voice typing stopped",
        "Your phone’s speech service didn’t respond (code $code${if (onDevice) ", on-device" else ""}). Try again, or use Google’s voice screen instead. If it keeps failing, update “Speech Recognition & Synthesis from Google” in the Play Store.",
    )
}

/** The mic sheet: big pulsing mic, live words as you speak, Done to finish early. */
@Composable
private fun ListeningDialog(prompt: String, onText: (String) -> Unit, onProblem: (code: Int, onDevice: Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val done by rememberUpdatedState(onText)
    val failed by rememberUpdatedState(onProblem)
    // Attempt 0 prefers the on-device recogniser (nothing leaves the phone); attempt 1 uses the phone's standard one.
    var attempt by remember { mutableIntStateOf(if (Voice.onDeviceAvailable(context)) 0 else 1) }
    var partial by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(attempt) {
        val onDevice = attempt == 0
        val rec = runCatching {
            if (onDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull()
        if (rec == null) {
            if (onDevice) attempt = 1 else failed(SpeechRecognizer.ERROR_CLIENT, false)
            return@DisposableEffect onDispose { }
        }
        recognizer = rec
        var finished = false
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { ready = true }
            override fun onBeginningOfSpeech() { ready = true }
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) partial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                if (finished) return
                finished = true
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty().ifBlank { partial.trim() }
                if (text.isNotBlank()) done(text) else failed(SpeechRecognizer.ERROR_NO_MATCH, onDevice)
            }
            override fun onError(error: Int) {
                if (finished) return
                // The on-device recogniser may not have English (India) yet: quietly fall back to the standard one.
                if (onDevice && error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                    error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { finished = true; attempt = 1; return }
                finished = true
                if (partial.isNotBlank() && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) done(partial.trim())
                else failed(error, onDevice)
            }
        })
        runCatching { rec.startListening(Voice.recognizerIntent(context, prompt)) }
            .onFailure { finished = true; if (onDevice) attempt = 1 else failed(SpeechRecognizer.ERROR_CLIENT, false) }
        onDispose { runCatching { rec.cancel() }; runCatching { rec.destroy() }; recognizer = null }
    }

    val pulse by animateFloatAsState(1f + level * 0.35f, label = "mic-level")
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Kc.colors.card).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(prompt, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, textAlign = TextAlign.Center)
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(112.dp).scale(pulse).clip(CircleShape).background(Haldi.copy(alpha = 0.25f)))
                Box(Modifier.size(76.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, tint = Ink, modifier = Modifier.size(38.dp))
                }
            }
            Text(
                when { partial.isNotBlank() -> "“$partial”"; ready -> "Listening… speak now"; else -> "Starting the mic…" },
                style = if (partial.isNotBlank()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = if (partial.isNotBlank()) Kc.colors.text else Kc.colors.muted, textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 48.dp),
            )
            Text("e.g. “450 for lunch with team at Paradise yesterday, by UPI”", style = MaterialTheme.typography.bodySmall,
                color = Kc.colors.muted, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { runCatching { recognizer?.stopListening() } },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                ) { Text("Done") }
            }
            Text("Recordings are never saved. Only the words reach Kharcha Check.", fontSize = 12.sp, color = Kc.colors.muted, textAlign = TextAlign.Center)
        }
    }
}

/** Step-by-step help, in the same style as the app's other permission guides. */
@Composable
private fun GuideDialog(
    icon: String, title: String, body: String, steps: List<String>,
    primary: String, onPrimary: () -> Unit,
    secondary: String? = null, onSecondary: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Kc.colors.card).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(icon, fontSize = 36.sp)
            Text(title, style = MaterialTheme.typography.titleLarge, color = Kc.colors.text)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
            steps.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Haldi, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text(s, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text, modifier = Modifier.weight(1f).padding(top = 2.dp))
                }
            }
            Button(
                onClick = onPrimary, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Haldi, contentColor = Ink),
            ) { Text(primary, fontWeight = FontWeight.Bold) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDismiss) { Text("Not now") }
                if (secondary != null) TextButton(onClick = onSecondary) { Text(secondary) }
            }
        }
    }
}

/** A mic for a text field's trailing icon. */
@Composable
fun MicButton(prompt: String, tint: Color = Color.Unspecified, onText: (String) -> Unit) {
    val listen = rememberVoiceInput(prompt, onText) ?: return
    IconButton(onClick = listen) {
        Icon(Icons.Rounded.Mic, contentDescription = "Speak instead of typing", tint = tint)
    }
}

/** For short single-line fields (names, payees): the spoken text replaces what was there, capped to [max]. */
fun spokenName(spoken: String, max: Int): String =
    spoken.trim().trimEnd('.', '!', '?').replaceFirstChar { it.titlecase() }.take(max)

/** For notes: spoken text is added after anything already typed, as a new sentence. */
fun appendSpoken(current: String, spoken: String, max: Int): String {
    val s = spoken.trim().replaceFirstChar { it.titlecase() }
    if (current.isBlank()) return s.take(max)
    val sep = if (current.trimEnd().endsWith('.') || current.trimEnd().endsWith(',')) " " else ". "
    return (current.trimEnd() + sep + s).take(max)
}
