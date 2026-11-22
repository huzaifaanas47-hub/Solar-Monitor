package com.medicglobe.knoxsolar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object KC {
    val bg = Color(0xFF04080F)
    val s1 = Color(0xFF0D1520)
    val s2 = Color(0xFF131E2E)
    val line = Color(0xFF1F2D44)
    val text = Color(0xFFE6EDF7)
    val mute = Color(0xFF8EA0BC)
    val acc = Color(0xFF2DD4BF)
    val ok = Color(0xFF34D399)
    val bad = Color(0xFFF87171)
    val warn = Color(0xFFFBBF24)
    val solar = Color(0xFFFBBF24)
    val load = Color(0xFF60A5FA)
    val grid = Color(0xFFA78BFA)
}

@Composable
fun KnoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KC.acc, onPrimary = Color(0xFF04201C),
            background = KC.bg, onBackground = KC.text,
            surface = KC.s1, onSurface = KC.text,
            surfaceVariant = KC.s2, onSurfaceVariant = KC.mute,
            secondaryContainer = Color(0xFF123B38), onSecondaryContainer = KC.text,
            error = KC.bad, outline = KC.line,
        ),
        content = content,
    )
}
