package com.medicglobe.knoxsolar.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.medicglobe.knoxsolar.AppViewModel
import com.medicglobe.knoxsolar.BuildConfig
import com.medicglobe.knoxsolar.Prefs
import com.medicglobe.knoxsolar.fmtDur

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(Prefs.baseUrl(ctx)) }
    var urlMsg by remember { mutableStateOf<String?>(null) }
    var nOutage by remember { mutableStateOf(Prefs.notifyOutage(ctx)) }
    var nStuck by remember { mutableStateOf(Prefs.notifyStuck(ctx)) }
    var buy by remember { mutableStateOf("") }
    var sell by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadPrices(); vm.loadGuard(false) }
    LaunchedEffect(vm.priceBuy, vm.priceSell) { buy = vm.priceBuy; sell = vm.priceSell }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KCard("SERVER") {
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                label = { Text("Server address (https://…)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    if (url.trim().startsWith("https://")) {
                        Prefs.setBaseUrl(ctx, url)
                        url = Prefs.baseUrl(ctx)
                        urlMsg = "Saved."
                        vm.refreshLive()
                    } else urlMsg = "The address must start with https://"
                }) { Text("Save") }
                urlMsg?.let { Note(it) }
            }
        }

        KCard("INVERTER LOGIN") {
            Note("The DessMonitor login, device and admin password are managed on your server's settings page (protected by your admin password). It opens in your browser.")
            OutlinedButton(onClick = {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.baseUrl(ctx) + "settings.php")))
                } catch (e: ActivityNotFoundException) { }
            }) { Text("Open inverter settings") }
        }

        KCard("ALERTS") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Grid down / back", color = KC.text)
                Switch(checked = nOutage, onCheckedChange = { nOutage = it; Prefs.setNotifyOutage(ctx, it) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Data logging stuck", color = KC.text)
                Switch(checked = nStuck, onCheckedChange = { nStuck = it; Prefs.setNotifyStuck(ctx, it) })
            }
            Note("Checked about every 15 minutes in the background, even when the app is closed.")
        }

        KCard("ELECTRICITY PRICE (Rs per kWh)") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = buy, onValueChange = { buy = it }, singleLine = true, label = { Text("Buy") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = sell, onValueChange = { sell = it }, singleLine = true, label = { Text("Sell") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.savePrices(buy, sell) }) { Text("Save prices") }
                vm.priceMsg?.let { Note(it) }
            }
        }

        KCard("DATA LOGGING") {
            val g = vm.guard
            if (g == null) Note("Checking…") else {
                KV("Status", if (g.ok) "Working" else "Stuck")
                g.ageSec?.let { KV("Last reading", fmtDur(it) + " ago") }
                g.lastRunAt?.let { KV("Last run", it) }
                if (!g.ok) g.lastMessage?.let { Note(it, KC.warn) }
            }
            OutlinedButton(onClick = { vm.loadGuard(true) }, modifier = Modifier.padding(top = 8.dp)) { Text("Restart logger now") }
        }

        Note("Knox Solar ${BuildConfig.VERSION_NAME}")
    }
}
