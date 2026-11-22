# Knox Solar — native Android app (v2)

A real Android app (Kotlin + Jetpack Compose), not a WebView. It talks to your existing
PHP server (`proxy.php`, `cron_guard.php`) and draws everything natively.

## Screens
- **Live** – battery ring, solar / load / grid / battery power, today's energy, money saved, details, grid and logging status. Refreshes every 30 s while the app is open.
- **History** – year + month picker, totals, bar chart, per-day list (`action=history`, `years`).
- **Timeline** – 5-minute power and battery charts for any day, grid-down periods shaded red (`action=cron_log`).
- **Grid** – outage list and summary for today / 7 days / 30 days / this year (`action=grid_events`).
- **Settings** – server address, alert switches, buy/sell price, logging status + "Restart logger now", and a button that opens your server's `settings.php` (inverter login) in the browser.

Background (every ~15 min, even when closed): wakes the server cron worker via `cron_guard.php`,
notifies on grid down/back and "logging stuck".

## Build
Needs JDK 17, Android SDK 34 and Gradle 8.9 (no wrapper is included).

GitHub: push this folder to your repo, open Actions -> "Build Android APK" -> Run workflow.
The APK appears under the run's Artifacts. Optional repo variable `BASE_URL`.

Locally: `gradle assembleRelease`

## Signing (you sign it)
Without signing secrets the release APK is **unsigned**: `app/build/outputs/apk/release/app-release-unsigned.apk`.

    zipalign -p -f 4 app-release-unsigned.apk knox-aligned.apk
    apksigner sign --ks your-key.jks --out knox-solar.apk knox-aligned.apk
    apksigner verify knox-solar.apk

Use the same key as the old WebView app (same package `com.medicglobe.knoxsolar`) and this
installs over it. Optionally add secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD` and CI signs it for you. No passwords are stored in the repo.

## Notes
- Server addresses must be `https://`.
- Prices are saved through `proxy.php?action=save_prices` (the same call the old dashboard used).
