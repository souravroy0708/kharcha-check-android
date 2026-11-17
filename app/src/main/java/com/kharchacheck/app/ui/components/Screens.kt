package com.kharchacheck.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.MutedOnInk

/** Standard secondary screen: ink top bar with back, scrolling content. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Kc.colors.paper)) {
        Row(
            Modifier.fillMaxWidth().background(Ink).statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
            actions()
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, value: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        }
        if (value != null) { Spacer(Modifier.width(8.dp)); Text(value, style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted) }
        if (onClick != null) { Spacer(Modifier.width(6.dp)); Text("›", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted) }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Kc.colors.text else Kc.colors.muted)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Haldi, uncheckedTrackColor = Kc.colors.chip))
    }
}

/** Rupee amount dialog. Empty or 0 means "clear". */
@Composable
fun MoneyDialog(title: String, initialPaise: Long, hint: String? = null, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (initialPaise > 0) (initialPaise / 100).toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                OutlinedTextField(
                    value = text, onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' }.take(12) },
                    prefix = { Text("₹ ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(Money.parse(text) ?: 0L) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it.take(60) }, label = { Text(label) }, singleLine = true,
                trailingIcon = { MicButton(label) { text = spokenName(it, 60) } }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Password entry for backups. Confirm field only when creating one. */
@Composable
fun PasswordDialog(creating: Boolean, onDismiss: () -> Unit, onDone: (CharArray) -> Unit) {
    var pw by rememberSaveable { mutableStateOf("") }
    var pw2 by rememberSaveable { mutableStateOf("") }
    val ok = pw.length >= 8 && (!creating || pw == pw2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Lock your backup" else "Unlock backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (creating) "Choose a password (8+ characters). Only you know it. If you forget it, the backup can't be opened by anyone, including us."
                    else "Enter the password you used when you made this backup. Restoring replaces everything on this phone.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted,
                )
                OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (creating) OutlinedTextField(pw2, { pw2 = it }, label = { Text("Repeat password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = pw2.isNotEmpty() && pw != pw2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onDone(pw.toCharArray()); pw = ""; pw2 = "" }) { Text(if (creating) "Choose file" else "Pick backup") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun InkNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MutedOnInk)
}
