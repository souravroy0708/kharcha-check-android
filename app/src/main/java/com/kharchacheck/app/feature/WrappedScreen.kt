@file:OptIn(ExperimentalFoundationApi::class)

package com.kharchacheck.app.feature

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.InvestMath
import com.kharchacheck.app.domain.Wrapped
import com.kharchacheck.app.domain.WrappedStory
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.nav.WrappedRoute
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Ink2
import com.kharchacheck.app.ui.theme.InkLine
import com.kharchacheck.app.ui.theme.Mint
import com.kharchacheck.app.ui.theme.MutedOnInk
import com.kharchacheck.app.ui.theme.Violet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Months covered by a Wrapped period: "2026-10" → October; "2026" → January to now (or December). */
fun wrappedMonths(period: String): Pair<String, List<YearMonth>> {
    val now = YearMonth.now()
    return if (period.length == 4) {
        val y = period.toInt()
        val last = if (y == now.year) now.monthValue else 12
        "$y" to (1..last).map { YearMonth.of(y, it) }
    } else {
        val m = YearMonth.parse(period)
        m.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) to listOf(m)
    }
}

@Composable
fun WrappedScreen(vm: AppViewModel, nav: NavHostController, route: WrappedRoute) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val (title, months) = wrappedMonths(route.period)
    val story by produceState<WrappedStory?>(null, data.txns, route.period) {
        value = withContext(Dispatchers.Default) { Wrapped.build(title, months, data.txns, data.catMap, data.settings.noSpendDays) }
    }
    val s = story
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
            Text("Kharcha Wrapped · $title", style = MaterialTheme.typography.titleMedium, color = Haldi)
        }
        when {
            s == null -> Unit
            s.isEmpty -> EmptyState("🎁", "Nothing to wrap yet", "Log a few entries for $title and your story appears here.")
            else -> Story(s, data.settings.expectedReturnPct)
        }
    }
}

private data class CardSpec(val emoji: String, val accent: Color, val headline: String, val body: String, val big: Boolean = false)

private fun cards(s: WrappedStory, ret: Int): List<CardSpec> = buildList {
    add(CardSpec("🎁", Haldi, "Your ${s.title} in money", "${s.entries} entries over ${s.activeDays} days. Let's see where it all went."))
    add(CardSpec("💸", Coral, "You spent ${Money.format(s.spendPaise)}",
        if (s.incomePaise > 0) "From ${Money.format(s.incomePaise)} coming in, you kept ${Money.format(s.savedPaise)}" + (s.savingsRatePct?.let { " ($it%)." } ?: ".")
        else "Add income next time and we'll show how much you kept."))
    s.topCategory?.let { c -> add(CardSpec(c.emoji, Color(c.colorArgb), "Your #1: ${c.name}", "${Money.format(s.topCategoryPaise)}, which is ${s.topCategorySharePct}% of everything you spent.")) }
    if (s.topPayee != null) add(CardSpec("📍", Violet, "Your go-to: ${s.topPayee}", "You paid them ${s.topPayeeCount} times." +
        (s.biggestDay?.let { " Your biggest day was ${it.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))} at ${Money.format(s.biggestDayPaise)}." } ?: "")))
    if (s.leaksPaise > 0) {
        val monthly = s.leaksPaise / (if (s.title.length == 4) 12 else 1)
        add(CardSpec("🕳️", Coral, "${Money.format(s.leaksPaise)} on leaks",
            "${s.leakCount} small spends. Invested every month instead, that's about ${Money.compact(InvestMath.sipFutureValue(monthly, ret.toDouble(), 10))} in 10 years."))
    }
    add(CardSpec("🌿", Mint, "${s.noSpendDays} no-spend days",
        s.favouriteDay?.let { "Your biggest spending day of the week: ${it.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}." } ?: "Every zero day counts."))
    add(CardSpec(s.persona.emoji, Haldi, "You're ${s.persona.title}", s.persona.line, big = true))
}

@Composable
private fun Story(s: WrappedStory, ret: Int) {
    val specs = cards(s, ret)
    val pager = rememberPagerState { specs.size }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(specs.size) { i -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(if (i <= pager.currentPage) Haldi else InkLine)) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i -> Card(specs[i]) }
        Text("Swipe for more →", style = MaterialTheme.typography.labelMedium, color = MutedOnInk, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(8.dp))
    }
}

/** One story card. The card itself (not the buttons) is captured as an image for sharing. */
@Composable
private fun Card(spec: CardSpec) {
    val (emoji, accent, headline, body, big) = spec
    val layer = rememberGraphicsLayer()
    val p = LocalPlatform.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.8f)
                .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }
                .clip(RoundedCornerShape(32.dp)).background(Ink2).padding(28.dp),
        ) {
            Box(Modifier.size(220.dp).align(Alignment.TopEnd).padding(start = 60.dp, bottom = 60.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)))
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(emoji, fontSize = if (big) 88.sp else 64.sp)
                Spacer(Modifier.height(20.dp))
                Text(headline, style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = MutedOnInk)
                Spacer(Modifier.weight(1f))
                Text("Kharcha Check · private, on my phone", style = MaterialTheme.typography.labelMedium, color = accent)
            }
        }
        Spacer(Modifier.height(16.dp))
        Pill("Share this card", Haldi, Ink, Modifier.align(Alignment.CenterHorizontally)) {
            scope.launch {
                val bmp: Bitmap = layer.toImageBitmap().asAndroidBitmap()
                p.shareImage(bmp, "$headline: my Kharcha Wrapped ✨")
            }
        }
    }
}
