package com.medicglobe.knoxsolar.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.AppViewModel
import kotlinx.coroutines.delay

private class TabDef(val title: String, val icon: ImageVector)

private val TABS = listOf(
    TabDef("Live", Icons.Filled.Home),
    TabDef("History", Icons.Filled.DateRange),
    TabDef("Timeline", Icons.Filled.List),
    TabDef("Grid", Icons.Filled.Warning),
    TabDef("More", Icons.Filled.Menu),
    TabDef("Settings", Icons.Filled.Settings),
)

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
        Icon(Icons.Filled.Refresh, contentDescription = "Refreshing", tint = KC.acc, modifier = Modifier.rotate(angle))
    } else {
        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = KC.mute)
    }
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
    Text(
        if (tab == 0) agoText(vm.lastUpdated, nowMs) else TABS[tab].title,
        color = KC.mute, fontSize = 12.sp,
    )
}

@Composable
fun KnoxRoot(vm: AppViewModel, tab: Int, onTab: (Int) -> Unit) {
    Scaffold(
        containerColor = KC.bg,
        bottomBar = {
            NavigationBar(containerColor = KC.s1) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = KC.acc, selectedTextColor = KC.acc,
                            unselectedIconColor = KC.mute, unselectedTextColor = KC.mute,
                            indicatorColor = KC.s3,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(
            Modifier.fillMaxSize().background(KC.bg)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(KC.acc.copy(alpha = 0.09f), Color.Transparent),
                            center = Offset(0f, 0f), radius = size.width * 0.9f,
                        ),
                        radius = size.width * 0.9f, center = Offset(0f, 0f),
                    )
                    drawCircle(
                        Brush.radialGradient(
                            listOf(KC.grid.copy(alpha = 0.06f), Color.Transparent),
                            center = Offset(size.width, size.height), radius = size.width * 0.8f,
                        ),
                        radius = size.width * 0.8f, center = Offset(size.width, size.height),
                    )
                }
                .padding(pad)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Knox Solar", color = KC.acc, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        SubTitle(vm, tab)
                    }
                    IconButton(onClick = {
                        vm.refreshLive()
                        when (tab) {
                            1 -> vm.reloadHistory()
                            2 -> vm.loadTimeline()
                            3 -> vm.loadGrid()
                            4 -> { vm.loadMonthly(); vm.loadInsights(); vm.loadSysInfo(); vm.reloadHistory() }
                        }
                    }) { RefreshIcon(vm.refreshing) }
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
