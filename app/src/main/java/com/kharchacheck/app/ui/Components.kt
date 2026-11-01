package com.kharchacheck.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharchacheck.app.AccountEntity
import com.kharchacheck.app.CategoryEntity
import com.kharchacheck.app.Money
import com.kharchacheck.app.ReviewState
import com.kharchacheck.app.TransactionEntry
import com.kharchacheck.app.TxType
import com.kharchacheck.app.WorthTag

@Composable
fun AppCard(
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .border(1.dp, PaperLine, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = TextMain)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                trailing?.invoke()
            }
        }
        content()
    }
}

@Composable
fun BigButton(
    text: String,
    enabled: Boolean = true,
    emerald: Boolean = false,
    amber: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = when {
        emerald -> Emerald
        amber -> Amber
        ThemeState.dark -> Emerald
        else -> Navy
    }
    val fg = when {
        emerald || amber || ThemeState.dark -> Navy
        else -> Color.White
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun GhostButton(
    text: String,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = if (danger) Coral else PrimaryAccent
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, color),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun SmallPill(
    text: String,
    emerald: Boolean = false,
    amber: Boolean = false,
    coral: Boolean = false,
    onClick: () -> Unit,
) {
    val (bg, fg) = when {
        emerald -> EmeraldSoft to Color(0xFF065F46)
        amber -> AmberSoft to AmberInk
        coral -> CoralSoft to Color(0xFF9F1239)
        else -> Paper2 to TextMain
    }
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun AppField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    hint: String? = null,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        supportingText = hint?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CardBg,
            unfocusedContainerColor = CardBg,
            focusedBorderColor = PrimaryAccent,
            unfocusedBorderColor = PaperLine,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun TransactionRowItem(
    tx: TransactionEntry,
    category: CategoryEntity?,
    account: AccountEntity?,
    lang: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(category?.colorHex ?: 0xFF10B981).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(category?.emoji ?: "💸", fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tx.merchantName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tx.reviewState == ReviewState.NEEDS_REVIEW) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AmberSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Check", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = AmberInk)
                    }
                } else if (tx.worth != WorthTag.NONE) {
                    val (wBg, wFg) = when (tx.worth) {
                        WorthTag.AVOIDABLE -> CoralSoft to Coral
                        WorthTag.NEEDED -> EmeraldSoft to EmeraldDark
                        else -> Paper2 to Muted
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(wBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(tx.worth.labelEn, style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = wFg)
                    }
                }
            }
            Text(
                text = "${category?.localizedName(lang) ?: "Spend"} · ${tx.mode.labelEn}" +
                    (account?.let { " · ${it.displayTitle}" } ?: "") + " · ${tx.prettyTime}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tx.note.isNotBlank()) {
                Text(
                    text = "“${tx.note}”",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val amountColor = when (tx.type) {
                TxType.INCOME, TxType.REFUND -> EmeraldDark
                TxType.TRANSFER -> Muted
                TxType.EXPENSE -> TextMain
            }
            val prefix = when (tx.type) {
                TxType.INCOME, TxType.REFUND -> "+"
                TxType.TRANSFER -> "⇄ "
                TxType.EXPENSE -> ""
            }
            Text(
                text = prefix + Money.format(tx.amountPaise),
                style = MaterialTheme.typography.titleLarge,
                color = amountColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = tx.source.labelEn,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = Muted,
            )
        }
    }
}
