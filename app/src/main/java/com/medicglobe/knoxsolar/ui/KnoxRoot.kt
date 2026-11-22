package com.medicglobe.knoxsolar.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.AppViewModel

private class TabDef(val title: String, val icon: ImageVector)

private val TABS = listOf(
    TabDef("Live", Icons.Filled.Home),
    TabDef("History", Icons.Filled.DateRange),
    TabDef("Timeline", Icons.Filled.List),
    TabDef("Grid", Icons.Filled.Warning),
    TabDef("Settings", Icons.Filled.Settings),
)

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
                            indicatorColor = KC.s2,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().background(KC.bg).padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Knox Solar · " + TABS[tab].title, color = KC.acc,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        vm.refreshLive()
                        when (tab) {
                            1 -> vm.loadHistory()
                            2 -> vm.loadTimeline()
                            3 -> vm.loadGrid()
                        }
                    }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = KC.mute) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> LiveScreen(vm)
                        1 -> HistoryScreen(vm)
                        2 -> TimelineScreen(vm)
                        3 -> GridScreen(vm)
                        else -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
}
