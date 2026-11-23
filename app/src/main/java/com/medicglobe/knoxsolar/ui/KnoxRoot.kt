package com.medicglobe.knoxsolar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.AppViewModel
import kotlinx.coroutines.delay

private class TabDef(val title: String, val glyph: Glyph)

// Settings (index 5) is reached from the top bar, so the bottom bar stays at five roomy items.
private val TABS = listOf(
    TabDef("Live", Glyph.Live),
    TabDef("History", Glyph.History),
    TabDef("Timeline", Glyph.Timeline),
    TabDef("Grid", Glyph.Grid),
    TabDef("More", Glyph.More),
    TabDef("Settings", Glyph.Settings),
)
private const val SETTINGS_TAB = 5

/** "Updated just now", "Updated 40 s ago", "Updated 3 min ago". */
private fun agoText(lastMs: Long, nowMs: Long): String {
    if (lastMs <= 0L) return "Waiting for the first reading"
    val s = ((nowMs - lastMs) / 1000L).coerceAtLeast(0L)
    return when {
        s < 5 -> "Updated just now"
        s < 90 -> "Updated $s s ago"
        s < 3600 -> "Updated ${s / 60} min ago"
        else -> "Updated over an hour ago"
    }
}

@Composable
private fun RefreshIcon(spinning: Boolean) {
    if (spinning) {
        val transition = rememberInfiniteTransition(label = "refresh")
        val angle by transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "angle",
        )
        Icon(Icons.Filled.Refresh, contentDescription = "Refreshing", tint = KC.acc, modifier = Modifier.size(22.dp).rotate(angle))
    } else {
        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = KC.mute, modifier = Modifier.size(22.dp))
    }
}

/** Soft round button for the top bar. */
@Composable
private fun GlassButton(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape)
            .background(if (selected) KC.acc.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.14f else 0.08f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Green dot that breathes while the screen is live. */
@Composable
private fun LiveDot() {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "dotA",
    )
    Box(Modifier.size(7.dp).alpha(a).background(KC.ok, CircleShape))
}

/** Owns the one-second clock so only this label recomposes each second. */
@Composable
private fun SubTitle(vm: AppViewModel, tab: Int) {
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (tab == 0) {
            LiveDot()
            Spacer(Modifier.width(7.dp))
        }
        Text(
            if (tab == 0) agoText(vm.lastUpdated, nowMs) else TABS[tab].title,
            color = KC.mute, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        )
    }
}

/** Floating glass pill. The selected item grows and shows its label; the rest are icons only. */
@Composable
private fun FloatingNav(tab: Int, onTab: (Int) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF2182339), Color(0xF20C1322))))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))), shape)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0..4) {
                val t = TABS[i]
                val on = tab == i
                val grow by animateFloatAsState(if (on) 1f else 0f, tween(280), label = "navGrow")
                val tint = if (on) KC.acc else KC.mute
                Box(
                    Modifier.weight(1f + 0.95f * grow).height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(KC.acc.copy(alpha = 0.15f * grow))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onTab(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NavGlyph(t.glyph, tint, size = 24.dp)
                        AnimatedVisibility(
                            visible = on,
                            enter = fadeIn(tween(220)) + expandHorizontally(tween(260)),
                            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(200)),
                        ) {
                            Text(
                                t.title, color = KC.acc, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 7.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KnoxRoot(vm: AppViewModel, tab: Int, onTab: (Int) -> Unit) {
    BackHandler(enabled = tab != 0) { onTab(0) }

    Scaffold(
        containerColor = KC.bg,
        bottomBar = { FloatingNav(tab, onTab) },
    ) { pad ->
        Box(
            Modifier.fillMaxSize().background(KC.bg)
                .drawBehind {
                    // soft aurora: teal glow top-left, blue top-right, violet bottom-right
                    drawCircle(
                        Brush.radialGradient(
                            listOf(KC.acc.copy(alpha = 0.11f), Color.Transparent),
                            center = Offset(0f, 0f), radius = size.width * 0.95f,
                        ),
                        radius = size.width * 0.95f, center = Offset(0f, 0f),
                    )
                    drawCircle(
                        Brush.radialGradient(
                            listOf(KC.grid.copy(alpha = 0.07f), Color.Transparent),
                            center = Offset(size.width, size.height * 0.18f), radius = size.width * 0.7f,
                        ),
                        radius = size.width * 0.7f, center = Offset(size.width, size.height * 0.18f),
                    )
                    drawCircle(
                        Brush.radialGradient(
                            listOf(KC.sell.copy(alpha = 0.07f), Color.Transparent),
                            center = Offset(size.width, size.height), radius = size.width * 0.9f,
                        ),
                        radius = size.width * 0.9f, center = Offset(size.width, size.height),
                    )
                }
                .padding(pad)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Knox Solar", color = KC.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        SubTitle(vm, tab)
                    }
                    GlassButton(selected = false, onClick = {
                        vm.refreshLive()
                        when (tab) {
                            1 -> vm.reloadHistory()
                            2 -> vm.loadTimeline()
                            3 -> vm.loadGrid()
                            4 -> { vm.loadMonthly(); vm.loadInsights(); vm.loadSysInfo(); vm.reloadHistory() }
                        }
                    }) { RefreshIcon(vm.refreshing) }
                    Spacer(Modifier.width(8.dp))
                    GlassButton(selected = tab == SETTINGS_TAB, onClick = { onTab(SETTINGS_TAB) }) {
                        NavGlyph(Glyph.Settings, if (tab == SETTINGS_TAB) KC.acc else KC.mute, size = 22.dp)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Crossfade(targetState = tab, label = "tabs") { current ->
                        when (current) {
                            0 -> LiveScreen(vm)
                            1 -> HistoryScreen(vm)
                            2 -> TimelineScreen(vm)
                            3 -> GridScreen(vm)
                            4 -> MoreScreen(vm)
                            else -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
