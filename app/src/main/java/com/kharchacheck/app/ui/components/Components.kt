package com.kharchacheck.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.ReviewState
import com.kharchacheck.app.core.model.Source
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.DayCell
import com.kharchacheck.app.domain.Split
import com.kharchacheck.app.ui.theme.Amber
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Ink2
import com.kharchacheck.app.ui.theme.Ink3
import com.kharchacheck.app.ui.theme.InkLine
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import com.kharchacheck.app.ui.theme.MutedOnInk
import com.kharchacheck.app.ui.theme.NeedBlue
import com.kharchacheck.app.ui.theme.Violet
import com.kharchacheck.app.ui.theme.color
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** Dark "ink" header that draws under the status bar, as in Bas 1 Minute. */
@Composable
fun Hero(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Ink)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun HeroTile(label: String, value: String, hint: String?, dot: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Ink2).border(1.dp, InkLine, RoundedCornerShape(18.dp)).padding(12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(label, color = MutedOnInk, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        if (hint != null) Text(hint, color = MutedOnInk, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun KcCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Kc.colors.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Kc.colors.text, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
        }
        content()
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Text(
        text, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
fun SectionHeader(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Kc.colors.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, onDark: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (onDark) Haldi else if (Kc.colors.dark) Haldi else Ink,
            contentColor = if (onDark || Kc.colors.dark) Ink else Color.White,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Emoji in a tinted circle: the visual identity of a category everywhere in the app. */
@Composable
fun CategoryIcon(category: Category?, size: Dp = 42.dp) {
    val c = category?.let { Color(it.colorArgb) } ?: Kc.colors.muted
    Box(Modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(category?.emoji ?: "❓", fontSize = (size.value * 0.48f).sp)
    }
}

private val rowTime = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TxnRow(txn: Txn, category: Category?, attached: Boolean = false, selected: Boolean = false, hidden: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val sign = when (txn.type) { TxType.INCOME -> "+"; TxType.EXPENSE -> "−"; else -> "" }
    val amountColor = when (txn.type) { TxType.INCOME -> Kc.colors.good; TxType.INVEST -> Mint; TxType.TRANSFER -> Kc.colors.muted; else -> Kc.colors.text }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Haldi.copy(alpha = 0.3f) else Color.Transparent)
            .then(if (onLongClick != null) Modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick) else Modifier.clickable(onClick = onClick))
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Box(Modifier.size(42.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) { Text("✓", color = Ink, style = MaterialTheme.typography.titleLarge) }
        else CategoryIcon(category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(txn.payee.ifBlank { category?.name ?: "Entry" }, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildList {
                if (txn.payee.isNotBlank()) add(category?.name ?: "")
                add(java.time.Instant.ofEpochMilli(txn.epochMillis).atZone(txn.zone).format(rowTime))
                add(txn.mode.label)
                if (txn.worth.emoji.isNotEmpty()) add(txn.worth.emoji)
                if (attached) add("📎")
                if (hidden) add("🙈 hidden")
            }.filter { it.isNotBlank() }.joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(sign + Money.format(txn.amountPaise, withPaise = true), style = MaterialTheme.typography.titleMedium, color = amountColor)
            when {
                txn.review == ReviewState.NEEDS_REVIEW -> Pill("Review", Haldi, Ink, Modifier.padding(top = 2.dp))
                txn.source == Source.SMS -> Text("SMS", style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
                category?.bucket == com.kharchacheck.app.core.model.Bucket.LEAK ->
                    Text("Leak", style = MaterialTheme.typography.labelSmall, color = Coral)
            }
        }
    }
}

/** Budget ring: outer arc is spend vs budget, inner arc is the part that was leaks. */
@Composable
fun SpendRing(fraction: Float, leakFraction: Float, over: Boolean, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    val lf by animateFloatAsState(leakFraction.coerceIn(0f, 1f), tween(900), label = "leak")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension
            val outer = d * 0.085f; val inner = d * 0.055f; val gap = d * 0.035f
            fun ring(inset: Float, w: Float, frac: Float, color: Color) {
                val tl = Offset(inset, inset); val s = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(Ink3, -90f, 360f, false, tl, s, style = Stroke(w, cap = StrokeCap.Round))
                if (frac > 0.002f) drawArc(color, -90f, 360f * frac, false, tl, s, style = Stroke(w, cap = StrokeCap.Round))
            }
            ring(outer / 2, outer, f, if (over) Coral else Haldi)
            ring(outer + gap + inner / 2, inner, lf, Coral.copy(alpha = 0.85f))
        }
        center()
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = Kc.colors.chip, height: Dp = 10.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

/** Needs / Wants / Save bar with the 50/30/20 ideal shown underneath. */
@Composable
fun SplitBar(split: Split) {
    val parts = listOf(split.needsPct to NeedBlue, split.wantsPct to Amber, split.savePct to Mint)
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(50)).background(Kc.colors.chip)) {
            parts.forEach { (pct, col) -> if (pct > 0) Box(Modifier.weight(pct.toFloat() / total).fillMaxHeight().background(col)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(NeedBlue, "Needs ${split.needsPct}%", "goal ≤50")
            Legend(Amber, "Wants ${split.wantsPct}%", "goal ≤30")
            Legend(Mint, "Saved ${split.savePct}%", "goal ≥20")
        }
    }
}

@Composable
private fun RowScope.Legend(c: Color, label: String, hint: String) {
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Kc.colors.text)
        }
        Text(hint, style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
    }
}

/** Compact daily bars for a month. */
@Composable
fun MiniBars(values: List<Long>, color: Color, modifier: Modifier = Modifier, highlightIndex: Int = -1) {
    val peak = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    Row(modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val frac = (v / peak).coerceIn(0f, 1f)
            Box(
                Modifier.weight(1f).fillMaxHeight(if (v == 0L) 0.03f else frac.coerceAtLeast(0.05f))
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(if (i == highlightIndex) Haldi else if (v == 0L) Kc.colors.line else color),
            )
        }
    }
}

/** 21-day habit grid: haldi = logged & leak-free, coral dot = had a leak, mint = no-spend day. */
@Composable
fun HabitGrid(cells: List<DayCell>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(7).forEachIndexed { w, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { i, d ->
                    val isToday = w == 2 && i == 6
                    val bg = when {
                        d.noSpend -> Mint
                        d.active && !d.leak -> Haldi
                        d.active -> Violet.copy(alpha = 0.55f)
                        else -> Kc.colors.chip
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(bg)
                            .then(if (isToday) Modifier.border(2.dp, Kc.colors.text, CircleShape) else Modifier)
                            .semantics { contentDescription = "${d.date}: " + if (d.noSpend) "no-spend day" else if (d.active) (if (d.leak) "logged, had leaks" else "logged, leak-free") else "nothing logged" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.labelMedium,
                            color = if (d.active || d.noSpend) Ink else Kc.colors.muted)
                        if (d.leak) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(7.dp).clip(CircleShape).background(Coral))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 2.dp)) {
            Dot(Haldi, "Leak-free"); Dot(Violet.copy(alpha = 0.55f), "Logged"); Dot(Mint, "No-spend"); Dot(Coral, "Leak")
        }
    }
}

@Composable
private fun Dot(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
    }
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(emoji, fontSize = 44.sp)
        Text(title, style = MaterialTheme.typography.titleLarge, color = Kc.colors.text, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted, textAlign = TextAlign.Center)
        if (action != null && onAction != null) { Spacer(Modifier.height(4.dp)); BigButton(action, onClick = onAction) }
    }
}

/** Segmented pill switch. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onDark: Boolean = false, onSelect: (Int) -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (onDark) Ink2 else Kc.colors.chip).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label, maxLines = 1, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                color = when { on -> Ink; onDark -> MutedOnInk; else -> Kc.colors.muted },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) Haldi else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp, horizontal = 4.dp),
            )
        }
    }
}

private data class Piece(val x: Float, val delay: Float, val speed: Float, val drift: Float, val spin: Float, val color: Color, val w: Float)

/** Confetti + haptic for wins: badges, challenges, goals. */
@Composable
fun Celebration(emoji: String, title: String, body: String, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val t = remember { Animatable(0f) }
    val pieces = remember {
        val colors = listOf(Haldi, Violet, Mint, Coral, Color.White)
        List(90) { Piece(Random.nextFloat(), Random.nextFloat() * 0.35f, 0.6f + Random.nextFloat() * 0.8f, (Random.nextFloat() - 0.5f) * 0.25f, Random.nextFloat() * 720f, colors.random(), 6f + Random.nextFloat() * 8f) }
    }
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); t.animateTo(1f, tween(2800, easing = LinearEasing)) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                pieces.forEach { p ->
                    val k = ((t.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f) * p.speed
                    if (k <= 0f) return@forEach
                    val x = (p.x + p.drift * k) * size.width
                    val y = -20f + k * size.height * 1.1f
                    rotate(p.spin * k, Offset(x, y)) { drawRect(p.color, Offset(x, y), Size(p.w, p.w * 1.8f), alpha = (1.4f - k).coerceIn(0f, 1f)) }
                }
            }
            Column(
                Modifier.padding(28.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(emoji, fontSize = 56.sp)
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Haldi, textAlign = TextAlign.Center)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MutedOnInk, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                BigButton("Shabash! 🎉", onDark = true, onClick = onClose)
            }
        }
    }
}

@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Kc.colors.text) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Kc.colors.chip).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1, fontWeight = FontWeight.Bold)
    }
}
