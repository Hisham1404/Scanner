package com.hisham.scanner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisham.scanner.R

/**
 * The same design the web build carries, held to the same four rules:
 *
 *   1. The interface is monochrome until it finds something. Colour is
 *      information, not decoration - Signal means a detection or a danger,
 *      Caution means uncertain, and nothing else anywhere is coloured.
 *   2. No cards. Hairline rules and space do the separating.
 *   3. Square geometry. This is an instrument, not a dashboard.
 *   4. Prose in Inter, every number and label in mono.
 *
 * Dark only, deliberately: this gets used in dim rooms at night, and a second
 * theme would be two half-committed designs instead of one.
 */
object Ink {
    val Bg = Color(0xFF08080A)
    val BgLift = Color(0xFF0D0D10)

    val Line = Color(0xFFF0EEE8).copy(alpha = 0.10f)
    val Line2 = Color(0xFFF0EEE8).copy(alpha = 0.18f)

    val Fg = Color(0xFFEDEBE6)
    val Fg2 = Color(0xFFEDEBE6).copy(alpha = 0.66f)
    val Fg3 = Color(0xFFEDEBE6).copy(alpha = 0.38f)

    val Signal = Color(0xFFFF4A38)
    val Caution = Color(0xFFD9A441)
}

object Metrics {
    val Gutter = 22.dp
    val SectionGap = 44.dp
    val Radius = 2.dp
}

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium)
)

/** Mono, uppercase, wide-tracked: every label, state word and reading. */
val LabelStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.7.sp,
    color = Ink.Fg3
)

val MonoValueStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.2.sp,
    color = Ink.Fg2
)

/** Display type is set light and tight. Weight is not emphasis. */
val DisplayStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 32.sp,
    lineHeight = 34.sp,
    letterSpacing = (-1.1).sp,
    color = Ink.Fg
)

val LeadStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 25.sp,
    letterSpacing = (-0.1).sp,
    color = Ink.Fg2
)

val BodyStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 21.sp,
    color = Ink.Fg2
)

val TitleStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    letterSpacing = (-0.2).sp,
    color = Ink.Fg
)

private val ScannerTypography = Typography(
    displayLarge = DisplayStyle,
    headlineSmall = DisplayStyle,
    titleMedium = TitleStyle,
    bodyLarge = LeadStyle,
    bodyMedium = BodyStyle,
    labelSmall = LabelStyle
)

private val ScannerColors = darkColorScheme(
    primary = Ink.Fg,
    onPrimary = Ink.Bg,
    background = Ink.Bg,
    onBackground = Ink.Fg,
    surface = Ink.Bg,
    onSurface = Ink.Fg,
    surfaceVariant = Ink.BgLift,
    onSurfaceVariant = Ink.Fg2,
    error = Ink.Signal,
    onError = Color.White,
    outline = Ink.Line2
)

@Composable
fun ScannerTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme is read so the call is not optimised away on a
    // light-mode device; the scheme stays dark either way, on purpose.
    @Suppress("UNUSED_VARIABLE")
    val ignored = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = ScannerColors,
        typography = ScannerTypography,
        content = content
    )
}

internal val CenteredLabel = LabelStyle.copy(textAlign = TextAlign.Center)
