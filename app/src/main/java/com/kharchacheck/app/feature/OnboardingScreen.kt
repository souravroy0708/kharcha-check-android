@file:OptIn(ExperimentalFoundationApi::class)

package com.kharchacheck.app.feature

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.theme.Amber
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Ink2
import com.kharchacheck.app.ui.theme.InkLine
import com.kharchacheck.app.ui.theme.Mint
import com.kharchacheck.app.ui.theme.MutedOnInk
import com.kharchacheck.app.ui.theme.NeedBlue
import kotlinx.coroutines.launch

private data class Slide(val emoji: String, val title: String, val body: String)

private val slides = listOf(
    Slide("₹", "Paisa kahan gaya?\nAb pata chalega.", "Log any spend in about 5 seconds with a calculator keypad and one-tap categories. Or share a bank SMS straight into the app."),
    Slide("🕳️", "Find the leaks", "Every rupee lands in Needs, Wants or Leaks. Leaks are the food deliveries, impulse buys and late fees that are easiest to cut."),
    Slide("🔥", "Make it a game", "Streaks, a 21-day grid, challenges like “No delivery week”, badges and levels. Plus golden rules to turn cuts into investments."),
    Slide("🔒", "Private. Really.", "No account, no ads, no tracking. Your entries are encrypted on your phone. The internet is only used to fetch public fund prices, and for backups to a folder you choose."),
)

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val pager = rememberPagerState { slides.size + 1 }
    val scope = rememberCoroutineScope()
    val platform = LocalPlatform.current
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kharcha Check", style = MaterialTheme.typography.titleMedium, color = Haldi, modifier = Modifier.weight(1f).padding(start = 8.dp))
            if (pager.currentPage < slides.size) TextButton(onClick = { scope.launch { pager.animateScrollToPage(slides.size) } }) { Text("Skip", color = MutedOnInk) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            if (page < slides.size) SlidePage(slides[page], page) else SetupPage(vm, platform.requestNotifications)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(slides.size + 1) { i ->
                Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                    .background(if (i == pager.currentPage) Haldi else InkLine))
            }
        }
        if (pager.currentPage < slides.size) Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            BigButton("Next", onDark = true) { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
        }
    }
}

@Composable
private fun SlidePage(s: Slide, index: Int) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(140.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) {
            Text(s.emoji, fontSize = 64.sp, color = Ink)
        }
        Spacer(Modifier.height(32.dp))
        Text(s.title, style = MaterialTheme.typography.displaySmall, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(s.body, style = MaterialTheme.typography.bodyLarge, color = MutedOnInk, textAlign = TextAlign.Center)
        if (index == 1) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Needs", NeedBlue, Color.White); Pill("Wants", Amber, Ink); Pill("Leaks", Coral, Color.White); Pill("Invest", Mint, Color.White)
            }
        }
    }
}

@Composable
private fun SetupPage(vm: AppViewModel, askNotifications: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var income by rememberSaveable { mutableStateOf("") }
    var budget by rememberSaveable { mutableStateOf("") }
    val incomePaise = Money.parse(income) ?: 0
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Ink2, unfocusedContainerColor = Ink2, focusedBorderColor = Haldi, unfocusedBorderColor = InkLine,
        focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Haldi,
        focusedLabelColor = Haldi, unfocusedLabelColor = MutedOnInk, focusedPrefixColor = Color.White, unfocusedPrefixColor = Color.White,
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Quick setup", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Text("All optional. You can change these any time in Settings.", style = MaterialTheme.typography.bodyMedium, color = MutedOnInk)
        OutlinedTextField(name, { name = it.take(24) }, label = { Text("What should we call you?") }, singleLine = true, colors = fieldColors,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(income, { v -> income = v.filter(Char::isDigit).take(9) }, label = { Text("Monthly take-home income") }, prefix = { Text("₹ ") },
            singleLine = true, colors = fieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(budget, { v -> budget = v.filter(Char::isDigit).take(9) }, label = { Text("Monthly spending budget") }, prefix = { Text("₹ ") },
            singleLine = true, colors = fieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        if (incomePaise > 0) {
            val suggested = incomePaise * 80 / 100
            Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Ink2).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🥇", fontSize = 22.sp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("50/30/20 suggests spending up to ${Money.format(suggested)}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("and saving or investing at least ${Money.format(incomePaise - suggested)} a month.", style = MaterialTheme.typography.bodySmall, color = MutedOnInk)
                }
                Pill("Use", Haldi, Ink) { budget = (suggested / 100).toString() }
            }
        }
        Spacer(Modifier.height(8.dp))
        BigButton("Start tracking", onDark = true) {
            askNotifications()
            vm.finishOnboarding(name, incomePaise, Money.parse(budget) ?: 0)
        }
        Text("🔒 Stays on this phone. No sign-up.", style = MaterialTheme.typography.bodySmall, color = MutedOnInk, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
fun LockScreen(vm: AppViewModel) {
    val p = LocalPlatform.current
    LaunchedEffect(Unit) { p.authenticate("Unlock Kharcha Check") { vm.unlock() } }
    Column(Modifier.fillMaxSize().background(Ink).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) { Text("🔒", fontSize = 52.sp) }
        Spacer(Modifier.height(24.dp))
        Text("Kharcha Check is locked", style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
        Text("Your money stays private, even if someone else picks up your phone.", style = MaterialTheme.typography.bodyMedium, color = MutedOnInk, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        BigButton("Unlock", onDark = true) { p.authenticate("Unlock Kharcha Check") { vm.unlock() } }
    }
}
