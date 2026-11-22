# Knox Solar — Android app

Opens your live dashboard (https://v2.medicglobe.fun/index.html) in an app.
Nothing is copied into the app: when you update the website, the app updates too.

Extras: pull-to-refresh, CSV/JSON export saves to Downloads, offline screen,
grid-outage notifications, and every ~15 min it wakes the server's cron worker
(via cron_guard.php) if it has stopped.

## Build on GitHub
1. Create a PRIVATE repo, upload everything in this folder (keep the .github folder).
2. Open the Actions tab -> "Build Android APK" runs by itself (or press Run workflow).
3. When it is green, open Releases (right side of the repo) and download knox-solar.apk.

## Different website address?
Repo -> Settings -> Secrets and variables -> Actions -> Variables -> new variable
BASE_URL = https://your-domain/  (with the trailing slash). Or edit app/build.gradle.kts.

## Change the inverter login
Long-press the app icon -> "Inverter settings" (or open https://your-site/settings.php).
First visit: create an admin password (setup key = the cron secret in cron_worker.php).
Needs these server files uploaded: inverter_config.php, settings.php, plus the updated
cron_worker.php and proxy.php.

## Signing key
The signing key is NOT in this repo. It lives in the repo secret KEYSTORE_BASE64
(Settings -> Secrets and variables -> Actions). Without it, the build makes a
temporary key, so each new APK must be installed after uninstalling the old one.
