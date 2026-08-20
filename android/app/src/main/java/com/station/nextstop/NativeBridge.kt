package com.station.nextstop

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * 게임(WebView 안의 JavaScript) <-> 네이티브 브리지.
 *
 * JS 쪽 사용법은 index.html 의 NextStop native bridge 블록을 보세요.
 * 비동기 결과는 window.__nsNativeResolve(requestId, resultJson) 으로 돌려준다.
 *
 * 주의: @JavascriptInterface 가 붙은 메서드는 WebView 의 임의 스레드에서 호출된다.
 * Ads/Hive SDK 는 UI 스레드를 요구하므로 host 를 통해 반드시 메인 스레드로 넘긴다.
 */
class NativeBridge(private val host: Host) {

    interface Host {
        fun runOnUi(block: () -> Unit)
        fun resolve(requestId: String, result: JSONObject)
        val ads: AdsManager
        val hive: HiveManager
    }

    /** 네이티브 환경인지 확인용. 웹에서는 window.NextStopNative 자체가 없다. */
    @JavascriptInterface
    fun platform(): String = "android"

    @JavascriptInterface
    fun capabilities(): String = JSONObject()
        .put("ads", true)
        .put("hive", host.hive.isEnabled)
        .put("testAds", BuildConfig.USING_TEST_ADS)
        .toString()

    // ------------------------------------------------------------------- ads

    @JavascriptInterface
    fun isRewardedReady(): Boolean = host.ads.isRewardedReady()

    @JavascriptInterface
    fun showRewardedAd(requestId: String) {
        host.runOnUi {
            host.ads.showRewarded { earned, reason ->
                host.resolve(
                    requestId,
                    JSONObject().put("ok", earned).put("reason", reason)
                )
            }
        }
    }

    @JavascriptInterface
    fun isInterstitialReady(): Boolean = host.ads.isInterstitialReady()

    @JavascriptInterface
    fun showInterstitialAd(requestId: String) {
        host.runOnUi {
            host.ads.showInterstitial { shown ->
                host.resolve(requestId, JSONObject().put("ok", shown))
            }
        }
    }

    // ------------------------------------------------------------------ hive

    @JavascriptInterface
    fun hiveEnabled(): Boolean = host.hive.isEnabled

    /** setup 완료 여부와 마지막 실패 사유. UI 가 버튼 상태를 정하는 데 쓴다. */
    @JavascriptInterface
    fun hiveStatus(): String = host.hive.statusJson().toString()

    @JavascriptInterface
    fun hiveCurrentPlayer(): String = host.hive.currentPlayer().toString()

    @JavascriptInterface
    fun hiveSignIn(requestId: String) {
        host.runOnUi { host.hive.signIn { json -> host.resolve(requestId, json) } }
    }

    @JavascriptInterface
    fun hiveSignInGuest(requestId: String) {
        host.runOnUi { host.hive.signInGuest { json -> host.resolve(requestId, json) } }
    }

    @JavascriptInterface
    fun hiveSignOut(requestId: String) {
        host.runOnUi { host.hive.signOut { json -> host.resolve(requestId, json) } }
    }
}
