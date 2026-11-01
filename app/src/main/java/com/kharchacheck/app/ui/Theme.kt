package com.kharchacheck.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kharchacheck.app.R

// Kharcha Check Financial Trust Palette: Deep Slate Navy + Emerald Mint + Warm Amber
val Navy = Color(0xFF0D1B2A)
val Navy2 = Color(0xFF1B263B)
val Navy3 = Color(0xFF283852)
val NavyLine = Color(0xFF334766)
val Emerald = Color(0xFF10B981)
val EmeraldDark = Color(0xFF059669)
val EmeraldSoft = Color(0xFFD1FAE5)
val Amber = Color(0xFFF59E0B)
val AmberSoft = Color(0xFFFEF3C7)
val AmberInk = Color(0xFF78350F)
val Coral = Color(0xFFF43F5E)
val CoralSoft = Color(0xFFFFE4E6)
val IndigoAccent = Color(0xFF6366F1)

object ThemeState { var dark by mutableStateOf(false) }

val Paper: Color get() = if (ThemeState.dark) Color(0xFF09111B) else Color(0xFFF5F7FB)
val Paper2: Color get() = if (ThemeState.dark) Color(0xFF152232) else Color(0xFFE9EEF6)
val CardBg: Color get() = if (ThemeState.dark) Color(0xFF111C2B) else Color.White
val PaperLine: Color get() = if (ThemeState.dark) Color(0xFF26354A) else Color(0xFFDCE3EE)
val TextMain: Color get() = if (ThemeState.dark) Color(0xFFF1F5F9) else Color(0xFF0D1B2A)
val Muted: Color get() = if (ThemeState.dark) Color(0xFF94A3B8) else Color(0xFF52637A)
val MutedOnDark = Color(0xFFCBD5E1)
val PrimaryAccent: Color get() = if (ThemeState.dark) Emerald else Color(0xFF0F766E)

val Display = FontFamily(
    Font(R.font.anek_bold, FontWeight.Bold),
    Font(R.font.anek_extrabold, FontWeight.ExtraBold),
)

val Body = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_bold, FontWeight.Bold),
)

val Devanagari = FontFamily(
    Font(R.font.anek_devanagari, FontWeight.SemiBold),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp),
)

@Composable
fun KharchaCheckTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    ThemeState.dark = dark
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = Emerald,
            onPrimary = Navy,
            secondary = Amber,
            onSecondary = Navy,
            background = Color(0xFF09111B),
            onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF111C2B),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = Color(0xFF152232),
            outline = Color(0xFF26354A),
        ) else lightColorScheme(
            primary = Navy,
            onPrimary = Color.White,
            secondary = Emerald,
            onSecondary = Navy,
            background = Color(0xFFF5F7FB),
            onBackground = Color(0xFF0D1B2A),
            surface = Color.White,
            onSurface = Color(0xFF0D1B2A),
            surfaceVariant = Color(0xFFE9EEF6),
            outline = Color(0xFFDCE3EE),
        ),
        typography = AppTypography,
    ) {
        CompositionLocalProvider(LocalContentColor provides TextMain, content = content)
    }
}
