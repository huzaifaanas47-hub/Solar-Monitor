package com.medicglobe.knoxsolar.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.R

object KC {
    // Surfaces: deep ink with a blue undertone, layered instead of boxed in.
    val bg = Color(0xFF05070D)
    val s1 = Color(0xFF090E18)
    val s2 = Color(0xFF0E1625)
    val s3 = Color(0xFF16213A)
    val glass = Color(0xE60C1322)
    val card = Color(0xFF0C1322)
    val cardTop = Color(0xF2141E32)
    val cardBot = Color(0xF20B111F)
    val cardBrush = Brush.verticalGradient(listOf(cardTop, cardBot))
    val track = Color(0x14FFFFFF)
    val line = Color(0x14FFFFFF)

    // Text
    val text = Color(0xFFF1F5FF)
    val mute = Color(0xFF8397B8)
    val mute2 = Color(0xFF55678A)

    // Accents
    val acc = Color(0xFF34E0C4)
    val ok = Color(0xFF3DDB7F)
    val bad = Color(0xFFFF5C6C)
    val warn = Color(0xFFFFB020)
    val solar = Color(0xFF34E0C4)
    val amber = Color(0xFFFFC233)
    val load = Color(0xFF9FB0CC)
    val grid = Color(0xFF45B6FF)
    val sell = Color(0xFFB496FF)
}

/** Plus Jakarta Sans, one variable font file, five weights. */
@OptIn(ExperimentalTextApi::class)
val KFont: FontFamily = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(
            R.font.jakarta_sans, FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    }
)

/** Material's default letter spacing and 24 sp line height make small text look loose; tighten both. */
private fun TextStyle.k() = copy(fontFamily = KFont, letterSpacing = 0.sp, lineHeight = TextUnit.Unspecified)

private fun knoxTypography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.k(), displayMedium = d.displayMedium.k(), displaySmall = d.displaySmall.k(),
        headlineLarge = d.headlineLarge.k(), headlineMedium = d.headlineMedium.k(), headlineSmall = d.headlineSmall.k(),
        titleLarge = d.titleLarge.k(), titleMedium = d.titleMedium.k(), titleSmall = d.titleSmall.k(),
        bodyLarge = d.bodyLarge.k(), bodyMedium = d.bodyMedium.k(), bodySmall = d.bodySmall.k(),
        labelLarge = d.labelLarge.k(), labelMedium = d.labelMedium.k(), labelSmall = d.labelSmall.k(),
    )
}

// Chips, fields and menus pick these up, so every screen gets pill chips and softer corners.
private val KShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(50),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun KnoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KC.acc, onPrimary = Color(0xFF032A24),
            background = KC.bg, onBackground = KC.text,
            surface = KC.s2, onSurface = KC.text,
            surfaceVariant = KC.s3, onSurfaceVariant = KC.mute,
            secondaryContainer = Color(0xFF123F3B), onSecondaryContainer = KC.text,
            surfaceContainerLowest = KC.bg, surfaceContainerLow = KC.s1, surfaceContainer = KC.s2,
            surfaceContainerHigh = KC.s3, surfaceContainerHighest = Color(0xFF1B2846),
            error = KC.bad, outline = KC.line, outlineVariant = KC.line,
        ),
        typography = knoxTypography(),
        shapes = KShapes,
        content = content,
    )
}
