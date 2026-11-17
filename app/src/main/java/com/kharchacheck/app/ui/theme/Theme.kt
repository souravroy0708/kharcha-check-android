package com.kharchacheck.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kharchacheck.app.R
import com.kharchacheck.app.core.model.Bucket

// Brand palette, shared with Bas 1 Minute so the two apps feel like one family.
val Ink = Color(0xFF0F1235)
val Ink2 = Color(0xFF181D4C)
val Ink3 = Color(0xFF232963)
val InkLine = Color(0xFF323A7C)
val Haldi = Color(0xFFFFC21A)
val Violet = Color(0xFF8F99FF)
val Coral = Color(0xFFFF6B4A)
val Mint = Color(0xFF2BB673)
val MintDark = Color(0xFF0B7A55)
val Amber = Color(0xFFFFB020)
val NeedBlue = Color(0xFF5B6CFF)
val MutedOnInk = Color(0xFFBCC1E0)
val Danger = Color(0xFFB42318)

fun Bucket.color(): Color = when (this) {
    Bucket.NEED -> NeedBlue
    Bucket.WANT -> Amber
    Bucket.LEAK -> Coral
    Bucket.INVEST -> Mint
    Bucket.INCOME -> MintDark
    Bucket.TRANSFER -> Color(0xFF8A90A8)
}

/** Surfaces that differ between light and dark. The ink hero, haldi and chart colours stay the same. */
@Immutable
data class KcColors(
    val paper: Color, val card: Color, val line: Color, val text: Color, val muted: Color,
    val chip: Color, val good: Color, val goodBg: Color, val warnBg: Color, val dark: Boolean,
)

private val LightKc = KcColors(
    paper = Color(0xFFF4F5FA), card = Color.White, line = Color(0xFFDCDFEC), text = Ink, muted = Color(0xFF4A4F6A),
    chip = Color(0xFFECEEF7), good = MintDark, goodBg = Color(0xFFE3F7EE), warnBg = Color(0xFFFFF1EC), dark = false,
)
private val DarkKc = KcColors(
    paper = Color(0xFF0A0C22), card = Color(0xFF161A42), line = Color(0xFF2B3063), text = Color(0xFFEEF0FF), muted = Color(0xFFA9AECB),
    chip = Color(0xFF20255A), good = Color(0xFF5ED6A0), goodBg = Color(0xFF123A2C), warnBg = Color(0xFF3A1F1A), dark = true,
)

val LocalKc = staticCompositionLocalOf { LightKc }

object Kc { val colors: KcColors @Composable get() = LocalKc.current }

val Display = FontFamily(Font(R.font.anek_bold, FontWeight.Bold), Font(R.font.anek_extrabold, FontWeight.ExtraBold))
val Body = FontFamily(Font(R.font.mukta_regular, FontWeight.Normal), Font(R.font.mukta_medium, FontWeight.Medium), Font(R.font.mukta_bold, FontWeight.Bold))

private val AppType = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, lineHeight = 52.sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 42.sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 27.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 23.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun KharchaTheme(dark: Boolean, content: @Composable () -> Unit) {
    val kc = if (dark) DarkKc else LightKc
    val scheme = if (dark) darkColorScheme(
        primary = Haldi, onPrimary = Ink, secondary = Violet, onSecondary = Ink,
        background = kc.paper, onBackground = kc.text, surface = kc.card, onSurface = kc.text,
        surfaceVariant = kc.chip, onSurfaceVariant = kc.muted, outline = kc.line, error = Coral,
        surfaceContainer = kc.card, surfaceContainerHigh = kc.card, surfaceContainerLow = kc.paper,
    ) else lightColorScheme(
        primary = Ink, onPrimary = Color.White, secondary = Haldi, onSecondary = Ink,
        background = kc.paper, onBackground = kc.text, surface = kc.card, onSurface = kc.text,
        surfaceVariant = kc.chip, onSurfaceVariant = kc.muted, outline = kc.line, error = Danger,
        surfaceContainer = kc.card, surfaceContainerHigh = kc.card, surfaceContainerLow = kc.paper,
    )
    CompositionLocalProvider(LocalKc provides kc) {
        MaterialTheme(colorScheme = scheme, typography = AppType, content = content)
    }
}
