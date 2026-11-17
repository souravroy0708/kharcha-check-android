package com.kharchacheck.app.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Voice typing through Android's own speech-to-text screen (RecognizerIntent).
 *
 * Privacy: Kharcha Check never records audio and needs no microphone permission. The phone's speech service
 * (usually Google) shows its own mic screen, asks for its own permission, and hands back only the text.
 * We ask it to stay on the phone where it can (EXTRA_PREFER_OFFLINE). Nothing is saved until the user taps Save.
 *
 * No manifest change: if a phone has no speech service, the first tap says so and the mic buttons hide.
 */
object VoiceTyping {
    /** Set once a launch fails, so mic buttons disappear for the rest of the session. */
    @Volatile var unavailable: Boolean = false

    fun intent(prompt: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        // Indian English understands rupees, lakh, Indian names and everyday Hinglish ("kal 300 ka petrol").
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN"))
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
}

/**
 * Returns a function that opens the speech screen and delivers what was said to [onText],
 * or null when voice typing isn't available on this phone (hide the mic then).
 */
@Composable
fun rememberVoiceInput(prompt: String, onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onText)
    var unavailable by remember { mutableStateOf(VoiceTyping.unavailable) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { latest(it) }
        }
    }
    if (unavailable) return null
    return {
        try {
            launcher.launch(VoiceTyping.intent(prompt))
        } catch (e: RuntimeException) { // ActivityNotFoundException, or a speech app that refuses to start
            VoiceTyping.unavailable = true
            unavailable = true
            Toast.makeText(context, "Voice typing isn't available on this phone. Turn on Google voice typing in Settings to use the mic.", Toast.LENGTH_LONG).show()
        }
    }
}

/** A mic for a text field's trailing icon. Hides itself on phones without voice typing. */
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
