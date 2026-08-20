package com.station.nextstop

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration as AndroidConfiguration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.hive.HiveActivity
import org.json.JSONObject

/**
 * 게임 전체를 담는 단일 Activity.
 *
 * 게임은 assets/game/index.html 에 그대로 들어가고,
 * WebViewAssetLoader 를 통해 https://appassets.androidplatform.net/ 오리진으로 서빙된다.
 * file:// 대신 https 오리진을 쓰는 이유:
 *   - localStorage(게임 진행 저장)가 안정적으로 유지된다
 *   - secure context 가 필요한 웹 API 가 정상 동작한다
 */
class MainActivity : AppCompatActivity(), NativeBridge.Host {

    companion object {
        private const val TAG = "NextStop"
        private const val ORIGIN = "https://appassets.androidplatform.net"
        private const val GAME_URL = "$ORIGIN/assets/game/index.html"
    }

    private lateinit var webView: WebView
    override lateinit var ads: AdsManager
    override lateinit var hive: HiveManager

    @SuppressLint("SetJavaScriptEnabled")
    /** 직전 실행이 Hive 때문에 죽었으면 이번 실행은 Hive 를 건너뛴다. */
    private var skipHiveDueToCrash = false
    private var lastCrashReport: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 스택 트레이스를 남겨야 시작 직후 죽는 문제를 볼 수 있다. Hive 초기화보다 먼저 건다.
        CrashReporter.install(this)
        lastCrashReport = CrashReporter.consume(this)
        // 같은 크래시로 계속 죽으면 앱을 아예 쓸 수 없다. 한 번 죽었으면 이번엔 Hive 를 끄고 뜬다.
        skipHiveDueToCrash = lastCrashReport?.contains("com.hive") == true
        if (skipHiveDueToCrash) {
            Log.w(TAG, "skipping Hive init: previous launch crashed inside the Hive SDK")
        }

        // Hive SDK 는 Activity 생명주기를 직접 전달받아야 한다.
        HiveActivity.onCreate(this, savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            setBackgroundColor(getColor(R.color.game_background))
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                // 게임 파일은 전부 앱 안에 들어있으므로 외부 파일 접근은 막아둔다.
                allowFileAccess = false
                allowContentAccess = false
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = false
                displayZoomControls = false
                textZoom = 100
            }
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url
                    // 게임 내부 링크는 WebView 가 처리하고, 외부 링크는 브라우저로 넘긴다.
                    if (url.toString().startsWith(ORIGIN)) return false
                    return runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
                        true
                    }.getOrElse { e ->
                        if (e is ActivityNotFoundException) true else throw e
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(TAG, "[web] ${message.message()} @${message.lineNumber()}")
                    return true
                }
            }
        }
        setContentView(webView)

        ads = AdsManager(this)
        hive = HiveManager(this)

        webView.addJavascriptInterface(NativeBridge(this), "NextStopNative")
        webView.loadUrl(GAME_URL)

        ads.start()

        lastCrashReport?.let { report ->
            // 직전 실행에서 죽었다면 그 내용을 바로 보낼 수 있게 공유 시트를 띄운다.
            webView.postDelayed({ Diagnostics.shareCrash(this, report) }, 1500)
        }

        if (skipHiveDueToCrash) {
            notifyHiveReady(false, "직전 실행이 Hive 에서 종료되어 이번에는 Hive 를 건너뛰었습니다")
        } else {
            hive.setup { success, message ->
                Log.i(TAG, "hive setup: success=$success ($message)")
                notifyHiveReady(success, message)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    // ------------------------------------------------- NativeBridge.Host

    private fun notifyHiveReady(success: Boolean, message: String) {
        runOnUi {
            webView.evaluateJavascript(
                "window.__nsHiveReady && window.__nsHiveReady($success, ${JSONObject.quote(message)});",
                null
            )
        }
    }

    override fun runOnUi(block: () -> Unit) = runOnUiThread(block)

    override fun shareDiagnostics() = Diagnostics.share(this)

    override fun resolve(requestId: String, result: JSONObject) {
        val payload = JSONObject.quote(result.toString())
        runOnUiThread {
            webView.evaluateJavascript(
                "window.__nsNativeResolve && window.__nsNativeResolve(" +
                    "${JSONObject.quote(requestId)}, $payload);",
                null
            )
        }
    }

    // ------------------------------------------- Activity / Hive lifecycle

    override fun onStart() {
        super.onStart()
        HiveActivity.onStart(this)
    }

    override fun onRestart() {
        super.onRestart()
        HiveActivity.onRestart(this)
    }

    override fun onResume() {
        super.onResume()
        HiveActivity.onResume(this)
        webView.onResume()
    }

    override fun onPause() {
        // pauseTimers()/resumeTimers() 는 쓰지 않는다.
        // 이 API 는 이 WebView 가 아니라 프로세스 안의 "모든" WebView 의 JS 타이머를 멈춘다.
        // Hive 로그인/약관 화면도 WebView 라서, 그 화면이 우리 위에 뜨면서 이 Activity 가
        // onPause 되는 순간 Hive 쪽 페이지의 JS 까지 얼어붙었다.
        // 그 결과 페이지 로드가 끝나지 않아 mirror 주소로 넘어가고, 버튼이 눌리지 않다가
        // 결국 -6(CANCELED) 로 실패했다. onPause() 는 이 WebView 에만 적용되므로 안전하다.
        webView.onPause()
        HiveActivity.onPause(this)
        super.onPause()
    }

    override fun onStop() {
        HiveActivity.onStop(this)
        super.onStop()
    }

    override fun onDestroy() {
        HiveActivity.onDestroy(this)
        webView.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        HiveActivity.onNewIntent(this, intent)
    }

    @Deprecated("Hive SDK v4 가 아직 onActivityResult 기반 로그인 흐름을 사용한다")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        HiveActivity.onActivityResult(this, requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        @Suppress("UNCHECKED_CAST")
        HiveActivity.onRequestPermissionsResult(
            this,
            requestCode,
            permissions as Array<String>,
            grantResults
        )
    }

    override fun onConfigurationChanged(newConfig: AndroidConfiguration) {
        super.onConfigurationChanged(newConfig)
        HiveActivity.onConfigurationChanged(this, newConfig)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        HiveActivity.onWindowFocusChanged(this, hasFocus)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        HiveActivity.onSaveInstanceState(this, outState)
    }
}
