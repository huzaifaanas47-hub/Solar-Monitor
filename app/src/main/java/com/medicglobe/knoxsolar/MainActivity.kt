package com.medicglobe.knoxsolar

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var offline: View

    private var leftAt = 0L
    private var lastPing = 0L
    private val siteHost: String? = Uri.parse(BuildConfig.BASE_URL).host

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        swipe = findViewById(R.id.swipe)
        progress = findViewById(R.id.progress)
        offline = findViewById(R.id.offline)
        findViewById<Button>(R.id.retry).setOnClickListener { loadSite() }

        swipe.setColorSchemeColors(Color.parseColor("#2DD4BF"))
        swipe.setProgressBackgroundColorSchemeColor(Color.parseColor("#0D1520"))
        // Only pull-to-refresh when the page is scrolled to the very top.
        swipe.setOnChildScrollUpCallback { _, _ -> web.scrollY > 0 }
        swipe.setOnRefreshListener {
            web.clearCache(false)
            if (offline.visibility == View.VISIBLE) loadSite() else web.reload()
        }

        web.setBackgroundColor(Color.parseColor("#04080F"))
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
        }
        web.addJavascriptInterface(FileBridge(applicationContext), "Knox")

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == siteHost) return false
                if (u.scheme == "http" || u.scheme == "https") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, u))
                    } catch (e: ActivityNotFoundException) {
                    }
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                swipe.isRefreshing = false
                progress.visibility = View.GONE
                view.evaluateJavascript(DOWNLOAD_HOOK, null)
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    swipe.isRefreshing = false
                    offline.visibility = View.VISIBLE
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        loadSite(pageFor(intent))
    }

    private fun loadSite(page: String = "index.html") {
        offline.visibility = View.GONE
        web.loadUrl(BuildConfig.BASE_URL + page)
    }

    private fun pageFor(i: Intent?): String =
        if (i?.action == ACTION_OPEN_SETTINGS) "settings.php" else "index.html"

    // Long-press the app icon -> "Inverter settings" opens the settings page.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadSite(pageFor(intent))
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        val now = System.currentTimeMillis()

        // Dashboard timers are paused while the app is in the background: refresh after a long break.
        if (leftAt > 0 && now - leftAt > 10 * 60_000L) web.reload()

        // Make sure the server's cron worker is alive (at most once every 2 minutes).
        if (now - lastPing > 2 * 60_000L) {
            lastPing = now
            Thread { Net.pingGuard() }.start()
        }
    }

    override fun onPause() {
        leftAt = System.currentTimeMillis()
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "com.medicglobe.knoxsolar.OPEN_SETTINGS"

        // The dashboard's CSV/JSON export creates a blob: link and clicks it, which a WebView
        // cannot download by itself. This hook hands the file to the app so it can save it.
        private const val DOWNLOAD_HOOK = """
(function(){
  if (window.__knoxHook) return; window.__knoxHook = true;
  var blobs = {};
  var origCreate = URL.createObjectURL;
  URL.createObjectURL = function(b){ var u = origCreate.call(URL, b); blobs[u] = b; return u; };
  var origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function(){
    var href = this.href || '';
    if (href.indexOf('blob:') === 0 && blobs[href]) {
      var b = blobs[href], name = this.download || 'knox_export';
      var r = new FileReader();
      r.onloadend = function(){
        var s = String(r.result);
        Knox.saveFile(name, b.type || 'application/octet-stream', s.substring(s.indexOf(',') + 1));
      };
      r.readAsDataURL(b);
      return;
    }
    return origClick.apply(this, arguments);
  };
})();
"""
    }
}

/** Saves files exported from the dashboard into the phone's Downloads folder. */
class FileBridge(private val ctx: Context) {

    @JavascriptInterface
    fun saveFile(name: String, mime: String, base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "knox_export" }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe)
                put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("could not create file")
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            toast("Saved to Downloads: $safe")
        } catch (e: Exception) {
            toast("Save failed: ${e.message}")
        }
    }

    private fun toast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
}
