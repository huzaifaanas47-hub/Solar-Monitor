package com.medicglobe.knoxsolar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.Today
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f1
import com.medicglobe.knoxsolar.f2
import kotlinx.coroutines.delay

private class Tile(val label: String, val value: String, val unit: String, val bolt: Boolean = false)

/** Two-column grid of live inverter readings. Tiles slide in one after another. */
@Composable
fun ParamTiles(t: Today) {
    val tiles = listOf(
        Tile("Battery voltage", if (t.battV > 0.0) f2(t.battV) else "—", "V"),
        Tile("Grid voltage", if (t.gridV > 1.0) f2(t.gridV) else if (t.gridUp == 0) "0.00" else "—", "V"),
        Tile("Grid frequency", if (t.gridHz > 1.0) f2(t.gridHz) else "—", "Hz"),
        Tile("Inverter temperature", f1(t.temp), "°C"),
        Tile("Solar panel voltage", f1(t.pvV), "V"),
        Tile("Solar panel current", f1(t.pvA), "A"),
        Tile("Today generation", f0(t.gen * 1000.0), "Wh", bolt = true),
        Tile("Total generation", f2(t.total), "kWh", bolt = true),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, tile ->
                    TileCard(row * 2 + col, tile, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TileCard(index: Int, tile: Tile, modifier: Modifier) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        appear.animateTo(1f, tween(450))
    }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 36f }
            .clip(shape)
            .background(KC.glass)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .heightIn(min = 78.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(tile.bolt)
        Column(Modifier.padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    tile.value, color = KC.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Text(" " + tile.unit, color = KC.mute, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            Text(tile.label, color = KC.mute, fontSize = 12.sp, lineHeight = 15.sp)
        }
    }
}

private val BOLT = Path().apply {
    moveTo(0.58f, 0.10f); lineTo(0.28f, 0.54f); lineTo(0.47f, 0.54f)
    lineTo(0.41f, 0.90f); lineTo(0.72f, 0.44f); lineTo(0.53f, 0.44f); close()
}

/** Round green badge. The bolt ones breathe slowly. */
@Composable
private fun Badge(bolt: Boolean) {
    val inf = rememberInfiniteTransition(label = "badge")
    val glow by inf.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "glow",
    )
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(KC.ok.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val a = if (bolt) glow else 0.9f
            if (bolt) {
                withScaledPath(BOLT, size.width, size.height) { drawPath(it, KC.ok.copy(alpha = a)) }
            } else {
                // small battery-with-plug glyph, as on the inverter parameter tiles
                val w = size.width; val h = size.height
                drawRoundRect(
                    KC.ok.copy(alpha = a), androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.22f),
                    androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.56f),
                    androidx.compose.ui.geometry.CornerRadius(w * 0.12f, w * 0.12f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.10f),
                )
                drawRect(
                    KC.ok.copy(alpha = a), androidx.compose.ui.geometry.Offset(w * 0.84f, h * 0.42f),
                    androidx.compose.ui.geometry.Size(w * 0.10f, h * 0.16f),
                )
                withScaledPath(BOLT, w * 0.6f, h * 0.6f, w * 0.14f, h * 0.2f) { drawPath(it, KC.ok.copy(alpha = a)) }
            }
        }
    }
}

/** Draws a 0..1 path scaled to [w] x [h] and moved by ([dx], [dy]). */
private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.withScaledPath(
    unit: Path, w: Float, h: Float, dx: Float = 0f, dy: Float = 0f, draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Path) -> Unit,
) {
    val p = Path()
    p.addPath(unit)
    val m = androidx.compose.ui.graphics.Matrix()
    m.translate(dx, dy)
    m.scale(w, h)
    p.transform(m)
    this.draw(p)
}
