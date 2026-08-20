package com.station.nextstop

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * 실제 광고(Google AdMob) 관리.
 *
 * - 보상형 광고: 게임 오버 후 "광고 보고 이어하기"
 * - 전면 광고: 스테이지 클리어 사이
 *
 * 광고 단위 ID 를 gradle.properties 에 채우기 전까지는 구글 공식 테스트 ID 로 동작한다.
 * 테스트 ID 는 실제 수익이 발생하지 않지만, 자기 광고 클릭으로 계정이 정지되는 일도 없다.
 */
class AdsManager(private val activity: Activity) {

    companion object {
        private const val TAG = "NextStopAds"
    }

    private lateinit var consentInformation: ConsentInformation

    private var mobileAdsInitialized = false
    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedLoading = false
    private var interstitialLoading = false

    /**
     * EEA/UK 사용자 동의(UMP)를 먼저 수집한 뒤 광고 SDK 를 초기화한다.
     * 동의를 건너뛰고 광고를 요청하면 유럽 트래픽에서 광고가 안 나오거나 정책 위반이 된다.
     */
    fun start() {
        consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "consent form error: ${formError.errorCode} ${formError.message}")
                    }
                    initializeMobileAdsIfAllowed()
                }
            },
            { requestError ->
                Log.w(TAG, "consent info update failed: ${requestError.errorCode} ${requestError.message}")
                // 동의 정보를 못 가져와도 비유럽 사용자는 광고를 받을 수 있으므로 초기화는 시도한다.
                initializeMobileAdsIfAllowed()
            }
        )
    }

    private fun initializeMobileAdsIfAllowed() {
        if (mobileAdsInitialized) return
        if (::consentInformation.isInitialized && !consentInformation.canRequestAds()) {
            Log.i(TAG, "ads not allowed by consent state yet")
            return
        }
        mobileAdsInitialized = true
        MobileAds.initialize(activity) {
            Log.i(TAG, "MobileAds initialized (testAds=${BuildConfig.USING_TEST_ADS})")
            preloadRewarded()
            preloadInterstitial()
        }
    }

    // ---------------------------------------------------------------- rewarded

    fun preloadRewarded() {
        if (!mobileAdsInitialized || rewardedLoading || rewardedAd != null) return
        rewardedLoading = true
        RewardedAd.load(
            activity,
            BuildConfig.ADMOB_REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                    rewardedAd = null
                    Log.w(TAG, "rewarded load failed: ${error.code} ${error.message}")
                }
            }
        )
    }

    fun isRewardedReady(): Boolean = rewardedAd != null

    /**
     * @param onResult 보상 지급 여부(true = 끝까지 시청). 항상 정확히 한 번 호출된다.
     */
    fun showRewarded(onResult: (earned: Boolean, reason: String) -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            preloadRewarded()
            onResult(false, "not_loaded")
            return
        }

        var earned = false
        var delivered = false
        fun deliver(reason: String) {
            if (delivered) return
            delivered = true
            rewardedAd = null
            preloadRewarded()
            onResult(earned, reason)
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = deliver("dismissed")
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "rewarded show failed: ${error.code} ${error.message}")
                deliver("show_failed")
            }
        }
        ad.show(activity) { earned = true }
    }

    // ------------------------------------------------------------ interstitial

    fun preloadInterstitial() {
        if (!mobileAdsInitialized || interstitialLoading || interstitialAd != null) return
        interstitialLoading = true
        InterstitialAd.load(
            activity,
            BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    interstitialAd = null
                    Log.w(TAG, "interstitial load failed: ${error.code} ${error.message}")
                }
            }
        )
    }

    fun isInterstitialReady(): Boolean = interstitialAd != null

    fun showInterstitial(onClosed: (shown: Boolean) -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            preloadInterstitial()
            onClosed(false)
            return
        }

        var delivered = false
        fun deliver(shown: Boolean) {
            if (delivered) return
            delivered = true
            interstitialAd = null
            preloadInterstitial()
            onClosed(shown)
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = deliver(true)
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "interstitial show failed: ${error.code} ${error.message}")
                deliver(false)
            }
        }
        ad.show(activity)
    }
}
