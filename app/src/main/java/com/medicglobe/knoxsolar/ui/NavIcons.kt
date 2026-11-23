package com.medicglobe.knoxsolar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Thin line icons drawn on a 24 x 24 grid, so the nav needs no icon library. */
enum class Glyph { Live, History, Timeline, Grid, More, Settings }

@Composable
fun NavGlyph(glyph: Glyph, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val st = Stroke(1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        when (glyph) {
            Glyph.Live -> {
                // lightning bolt
                val bolt = Path().apply {
                    moveTo(13.5f * s, 2.5f * s); lineTo(5f * s, 13.5f * s); lineTo(11.5f * s, 13.5f * s)
                    lineTo(10.5f * s, 21.5f * s); lineTo(19f * s, 10.5f * s); lineTo(12.5f * s, 10.5f * s); close()
                }
                drawPath(bolt, color, style = st)
            }
            Glyph.History -> {
                drawLine(color, p(5f, 20f), p(5f, 12f), st.width, StrokeCap.Round)
                drawLine(color, p(12f, 20f), p(12f, 5f), st.width, StrokeCap.Round)
                drawLine(color, p(19f, 20f), p(19f, 9f), st.width, StrokeCap.Round)
            }
            Glyph.Timeline -> {
                val line = Path().apply {
                    moveTo(3f * s, 16f * s); lineTo(8f * s, 10f * s); lineTo(12.5f * s, 14f * s); lineTo(20f * s, 5f * s)
                }
                drawPath(line, color, style = st)
                drawCircle(color, 1.7f * s, p(20f, 5f))
                drawLine(color.copy(alpha = 0.55f), p(3f, 21f), p(21f, 21f), st.width, StrokeCap.Round)
            }
            Glyph.Grid -> {
                // transmission pylon
                drawLine(color, p(12f, 3f), p(6.5f, 21f), st.width, StrokeCap.Round)
                drawLine(color, p(12f, 3f), p(17.5f, 21f), st.width, StrokeCap.Round)
                drawLine(color, p(8.5f, 9f), p(15.5f, 9f), st.width, StrokeCap.Round)
                drawLine(color, p(7.3f, 15f), p(16.7f, 15f), st.width, StrokeCap.Round)
                drawLine(color, p(8.5f, 9f), p(16.7f, 15f), st.width * 0.7f, StrokeCap.Round)
                drawLine(color, p(15.5f, 9f), p(7.3f, 15f), st.width * 0.7f, StrokeCap.Round)
            }
            Glyph.More -> {
                val r = CornerRadius(2.4f * s, 2.4f * s)
                val sz = Size(6.5f * s, 6.5f * s)
                for (cx in listOf(3.8f, 13.7f)) for (cy in listOf(3.8f, 13.7f)) {
                    drawRoundRect(color, Offset(cx * s, cy * s), sz, r, style = st)
                }
            }
            Glyph.Settings -> {
                // two sliders
                drawLine(color, p(3.5f, 8f), p(20.5f, 8f), st.width, StrokeCap.Round)
                drawLine(color, p(3.5f, 16f), p(20.5f, 16f), st.width, StrokeCap.Round)
                knob(color, s, 9f, 8f)
                knob(color, s, 15f, 16f)
            }
        }
    }
}

private fun DrawScope.knob(color: Color, s: Float, cx: Float, cy: Float) {
    drawCircle(Color(0xFF0B111F), 3.1f * s, Offset(cx * s, cy * s))
    drawCircle(color, 3.1f * s, Offset(cx * s, cy * s), style = Stroke(1.9f * s))
}
