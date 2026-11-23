package com.medicglobe.knoxsolar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private class Tile(val label: String, val value: String, val unit: String, val accent: Color)

/** Two-column grid of live inverter readings. Tiles slide in one after another. */
@Composable
fun ParamTiles(t: Today) {
    val tiles = listOf(
        Tile("Battery voltage", if (t.battV > 0.0) f2(t.battV) else "—", "V", KC.sell),
        Tile("Grid voltage", if (t.gridV > 1.0) f2(t.gridV) else if (t.gridUp == 0) "0.00" else "—", "V", KC.grid),
        Tile("Grid frequency", if (t.gridHz > 1.0) f2(t.gridHz) else "—", "Hz", KC.grid),
        Tile("Inverter temp", f1(t.temp), "°C", KC.warn),
        Tile("Panel voltage", f1(t.pvV), "V", KC.amber),
        Tile("Panel current", f1(t.pvA), "A", KC.amber),
        Tile("Generated today", f0(t.gen * 1000.0), "Wh", KC.solar),
        Tile("Lifetime total", f2(t.total), "kWh", KC.solar),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        delay(index * 60L)
        appear.animateTo(1f, tween(420))
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 28f }
            .clip(shape)
            .background(KC.cardBrush)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))), shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(tile.accent, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(tile.label, color = KC.mute, fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
            Text(
                tile.value, color = KC.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                style = TextStyle(fontFamily = KFont, fontFeatureSettings = "tnum"),
            )
            Text(" " + tile.unit, color = KC.mute, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}
