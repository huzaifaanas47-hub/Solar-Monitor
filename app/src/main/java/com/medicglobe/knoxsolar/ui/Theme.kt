package com.medicglobe.knoxsolar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object KC {
    val bg = Color(0xFF04080F)
    val s1 = Color(0xFF080D17)
    val s2 = Color(0xFF0D1520)
    val s3 = Color(0xFF131E2E)
    val glass = Color(0xA60D1520)
    val line = Color(0x14FFFFFF)
    val text = Color(0xFFEDF2FF)
    val mute = Color(0xFF7A90B0)
    val mute2 = Color(0xFF4A5D7A)
    val acc = Color(0xFF2DD4BF)
    val ok = Color(0xFF3DDB7F)
    val bad = Color(0xFFFF5C5C)
    val warn = Color(0xFFF59E0B)
    val solar = Color(0xFF2DD4BF)
    val amber = Color(0xFFFFB800)
    val load = Color(0xFF94A3B8)
    val grid = Color(0xFF38BDF8)
    val sell = Color(0xFFA78BFA)
}

@Composable
fun KnoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KC.acc, onPrimary = Color(0xFF04201C),
            background = KC.bg, onBackground = KC.text,
            surface = KC.s2, onSurface = KC.text,
            surfaceVariant = KC.s3, onSurfaceVariant = KC.mute,
            secondaryContainer = Color(0xFF123B38), onSecondaryContainer = KC.text,
            error = KC.bad, outline = KC.line,
        ),
        content = content,
    )
}
