package com.kharchacheck.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.DayPart
import com.kharchacheck.app.domain.MonthPoint
import com.kharchacheck.app.ui.theme.Amber
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import com.kharchacheck.app.ui.theme.NeedBlue
import com.kharchacheck.app.ui.theme.Violet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private fun YearMonth.short(): String = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3)

@Composable
fun ChartLegend(items: List<Pair<Color, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (c, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(5.dp))
                Text(l, style = MaterialTheme.typography.labelMedium, color = Kc.colors.muted)
            }
        }
    }
}

/** Income / spend / invest side by side per month. Tap a month to see its numbers. */
@Composable
fun IncomeSpendChart(months: List<MonthPoint>, selected: Int, onSelect: (Int) -> Unit) {
    val peak = months.maxOfOrNull { maxOf(it.incomePaise, it.spendPaise, it.investPaise) }?.coerceAtLeast(1) ?: 1
    val grow by animateFloatAsState(1f, tween(700), label = "grow")
    Row(
        Modifier.fillMaxWidth().height(190.dp).semantics {
            contentDescription = "Income versus spending by month. " + months.joinToString("; ") {
                "${it.month.short()}: income ${Money.format(it.incomePaise)}, spent ${Money.format(it.spendPaise)}"
            }
        },
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom,
    ) {
        months.forEachIndexed { i, m ->
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .background(if (i == selected) Kc.colors.chip else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.weight(1f).fillMaxWidth()) {
                    val values = listOf(m.incomePaise to Mint, m.spendPaise to Haldi, m.investPaise to Violet)
                    val gap = size.width * 0.06f
                    val bw = ((size.width * 0.8f) - gap * 2) / 3
                    var x = (size.width - (bw * 3 + gap * 2)) / 2
                    values.forEach { (v, c) ->
                        val h = size.height * (v.toFloat() / peak) * grow
                        if (h > 0f) drawRoundRect(c, Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 3))
                        x += bw + gap
                    }
                }
                Text(m.month.short(), style = MaterialTheme.typography.labelSmall, color = if (i == selected) Kc.colors.text else Kc.colors.muted)
            }
        }
    }
}

/** Savings-rate line with a dashed 20% goal line. Months without income leave a gap. */
@Composable
fun SavingsRateChart(months: List<MonthPoint>, goalPct: Int = 20) {
    val rates = months.map { it.savingsRatePct }
    val known = rates.filterNotNull()
    val top = maxOf(known.maxOrNull() ?: 0, goalPct + 10).toFloat()
    val bottom = minOf(known.minOrNull() ?: 0, 0).toFloat()
    val good = Kc.colors.good
    val line = Kc.colors.line
    val muted = Kc.colors.muted
    Column(Modifier.semantics { contentDescription = "Savings rate by month: " + months.joinToString("; ") { "${it.month.short()} ${it.savingsRatePct?.let { r -> "$r%" } ?: "no income"}" } }) {
        Canvas(Modifier.fillMaxWidth().height(130.dp).padding(vertical = 6.dp)) {
            fun y(v: Float) = size.height - (v - bottom) / (top - bottom) * size.height
            val step = if (rates.size > 1) size.width / (rates.size - 1) else 0f
            val zeroY = y(0f)
            drawLine(line, Offset(0f, zeroY), Offset(size.width, zeroY), 2f)
            drawLine(good.copy(alpha = 0.7f), Offset(0f, y(goalPct.toFloat())), Offset(size.width, y(goalPct.toFloat())), 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            var path: Path? = null
            rates.forEachIndexed { i, r ->
                if (r == null) { path?.let { drawPath(it, Haldi, style = Stroke(6f, cap = StrokeCap.Round)) }; path = null; return@forEachIndexed }
                val p = Offset(i * step, y(r.toFloat()))
                path = (path ?: Path().apply { moveTo(p.x, p.y) }).apply { lineTo(p.x, p.y) }
            }
            path?.let { drawPath(it, Haldi, style = Stroke(6f, cap = StrokeCap.Round)) }
            rates.forEachIndexed { i, r ->
                if (r != null) drawCircle(if (r >= goalPct) good else Coral, 9f, Offset(i * step, y(r.toFloat())))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            months.forEach { Text(it.month.short().take(1), style = MaterialTheme.typography.labelSmall, color = muted) }
        }
    }
}

data class Slice(val color: Color, val value: Float, val label: String)

/** Donut with a gap between slices; centre content is up to the caller. */
@Composable
fun Donut(slices: List<Slice>, size: Dp, description: String, center: @Composable () -> Unit) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val sweep by animateFloatAsState(1f, tween(800), label = "donut")
    val track = Kc.colors.chip
    Box(Modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.16f
            val inset = stroke / 2
            val tl = Offset(inset, inset); val s = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, tl, s, style = Stroke(stroke))
            var start = -90f
            slices.forEach { sl ->
                val angle = 360f * sl.value / total * sweep
                if (angle > 1.5f) drawArc(sl.color, start + 0.75f, angle - 1.5f, false, tl, s, style = Stroke(stroke))
                start += angle
            }
        }
        center()
    }
}

/** Needs / Wants / Leaks stacked per month. */
@Composable
fun BucketStackChart(months: List<MonthPoint>) {
    val peak = months.maxOfOrNull { it.needsPaise + it.wantsPaise + it.leaksPaise }?.coerceAtLeast(1) ?: 1
    Row(
        Modifier.fillMaxWidth().height(150.dp).semantics {
            contentDescription = "Needs, wants and leaks by month. " + months.joinToString("; ") {
                "${it.month.short()}: needs ${Money.format(it.needsPaise)}, wants ${Money.format(it.wantsPaise)}, leaks ${Money.format(it.leaksPaise)}"
            }
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { m ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.weight(1f).fillMaxWidth(0.7f)) {
                    var y = size.height
                    listOf(m.needsPaise to NeedBlue, m.wantsPaise to Amber, m.leaksPaise to Coral).forEach { (v, c) ->
                        val h = size.height * v / peak
                        if (h > 0f) { drawRect(c, Offset(0f, y - h), Size(size.width, h)); y -= h }
                    }
                }
                Text(m.month.short().take(1), style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
            }
        }
    }
}

/** Weekday × time-of-day intensity: "you spend most on Friday nights". */
@Composable
fun SpendHeatmap(heat: List<List<Long>>, peak: Long) {
    val parts = DayPart.entries
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics { contentDescription = "Spending by weekday and time of day" }) {
        Row {
            Spacer(Modifier.width(38.dp))
            parts.forEach { p -> Text(p.label.take(4), style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        heat.forEachIndexed { d, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(DayOfWeek.of(d + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH), style = MaterialTheme.typography.labelSmall,
                    color = Kc.colors.muted, modifier = Modifier.width(38.dp))
                row.forEach { v ->
                    val a = if (peak <= 0) 0f else (v.toFloat() / peak)
                    Box(Modifier.weight(1f).height(24.dp).padding(horizontal = 2.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (v == 0L) Kc.colors.chip else Coral.copy(alpha = 0.15f + 0.85f * a)))
                }
            }
        }
    }
}

/** Calendar of a month, each day shaded by how much was spent. Tap a day to see its total. */
@Composable
fun CalendarHeat(month: YearMonth, daily: List<Long>, selected: Int?, onSelect: (Int) -> Unit) {
    val peak = daily.maxOrNull()?.coerceAtLeast(1) ?: 1
    val offset = month.atDay(1).dayOfWeek.value - 1
    val today = LocalDate.now()
    val cells = List(offset) { null } + daily.indices.map { it }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { i ->
                    if (i == null) Spacer(Modifier.weight(1f)) else {
                        val v = daily[i]
                        val date = month.atDay(i + 1)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (v == 0L) Kc.colors.chip else Haldi.copy(alpha = 0.2f + 0.8f * v / peak))
                                .then(if (selected == i || date == today) Modifier.border(2.dp, Kc.colors.text, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { onSelect(i) }
                                .semantics { contentDescription = "${date.dayOfMonth} ${month.short()}: ${Money.format(v)} spent" },
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (date.isAfter(today)) Kc.colors.muted else Kc.colors.text) }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Simple monthly bars for one series (category drill-down). */
@Composable
fun MonthBars(months: List<YearMonth>, values: List<Long>, color: Color, height: Dp = 140.dp) {
    val peak = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val avg = if (values.isEmpty()) 0L else values.sum() / values.size
    val muted = Kc.colors.muted
    Column {
        Box(Modifier.fillMaxWidth().height(height)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                values.forEach { v ->
                    Box(Modifier.weight(1f).fillMaxHeight((v.toFloat() / peak).coerceAtLeast(0.02f)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (v == 0L) Kc.colors.line else color))
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height - size.height * avg / peak
                drawLine(muted, Offset(0f, y), Offset(size.width, y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            months.forEach { Text(it.short().take(if (months.size > 6) 1 else 3), style = MaterialTheme.typography.labelSmall, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
    }
}
