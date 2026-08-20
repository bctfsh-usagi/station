package com.station.nextstop

import android.app.Activity
import android.util.Log
import com.hive.AuthV4
import com.hive.Configuration
import com.hive.ResultAPI
import org.json.JSONObject

/**
 * Hive SDK v4 (AuthV4) 연동.
 *
 * gradle.properties 의 hive.appId 가 비어 있으면 Hive 기능 전체가 꺼진 상태로 동작한다.
 * (콘솔 등록 전에도 APK 가 정상 실행되어야 하므로 초기화 실패로 앱이 죽지 않게 한다.)
 *
 * 설정값은 res/raw/hive_config.xml 로 주입되며, 이 파일은 빌드 시 생성된다.
 */
class HiveManager(private val activity: Activity) {

    companion object {
        private const val TAG = "NextStopHive"
    }

    val isEnabled: Boolean = BuildConfig.HIVE_APP_ID.isNotBlank()

    /** AuthV4.setup 이 끝났는지. 끝나기 전에 signIn 을 호출하면 실패한다. */
    @Volatile
    var isSetupDone: Boolean = false
        private set

    /** 마지막 setup 실패 사유. UI 에 그대로 보여줘서 adb 없이도 원인을 알 수 있게 한다. */
    @Volatile
    var lastSetupError: String = ""
        private set

    @Volatile
    private var setupInProgress: Boolean = false

    @Volatile
    private var autoSignInAvailable: Boolean = false

    /**
     * Activity.onCreate 에서 HiveActivity.onCreate 다음에 호출한다.
     * @param onReady setup 완료 콜백 (성공 여부, 메시지)
     */
    fun setup(onReady: (success: Boolean, message: String) -> Unit) {
        if (!isEnabled) {
            onReady(false, "hive_not_configured")
            return
        }
        if (isSetupDone) {
            onReady(true, "already_setup")
            return
        }
        if (setupInProgress) {
            onReady(false, "setup_in_progress")
            return
        }
        setupInProgress = true

        try {
            Configuration.context = activity
            Configuration.appId = BuildConfig.HIVE_APP_ID
            Configuration.zone = runCatching {
                Configuration.ZoneType.valueOf(BuildConfig.HIVE_ZONE)
            }.getOrDefault(Configuration.ZoneType.SANDBOX)
            Configuration.useLog = BuildConfig.DEBUG

            // Hive 게임 보안(Hercules) 모듈을 넣지 않았으므로 반드시 꺼야 한다.
            // 기본값이 true 라서, 켜진 채로 두면 HiveSdkLifecycle.onSetupFinished 가
            //   Class.forName("com.hive.hercules.HerculesInitializer")
            // 에 실패하고 Android.finish() 로 앱 프로세스를 그대로 종료시킨다.
            // (크래시가 아니라 exitProcess 라서 스택 트레이스조차 남지 않는다)
            Configuration.useHercules = false
            // Hive 콘솔 > 프로젝트 정보 > 기본정보 > "HIVE 인증키".
            // provision/metadata-init-interaction 을 포함한 모든 프로비저닝 요청에 실려 나간다.
            // 비어 있으면 서버가 클라이언트를 식별하지 못해 "unknown client" 로 실패한다.
            if (BuildConfig.HIVE_CERTIFICATION_KEY.isNotBlank()) {
                Configuration.hiveCertificationKey = BuildConfig.HIVE_CERTIFICATION_KEY
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Hive configuration failed", t)
            setupInProgress = false
            lastSetupError = "configuration_failed: ${t.message}"
            onReady(false, lastSetupError)
            return
        }

        AuthV4.setup(object : AuthV4.AuthV4SetupListener {
            override fun onAuthV4Setup(
                result: ResultAPI,
                isAutoSignIn: Boolean,
                did: String?,
                providerTypeList: ArrayList<AuthV4.ProviderType>?
            ) {
                setupInProgress = false
                if (result.isSuccess) {
                    isSetupDone = true
                    lastSetupError = ""
                    autoSignInAvailable = isAutoSignIn
                    Log.i(TAG, "AuthV4.setup ok (autoSignIn=$isAutoSignIn, did=$did)")
                    onReady(true, if (isAutoSignIn) "auto_sign_in_available" else "setup_done")
                } else {
                    // errorCode 만으로는 원인을 알기 어려워 서버가 준 메시지까지 함께 남긴다.
                    lastSetupError = "setup_failed: ${result.errorCode} / ${result.code} / ${result.message}"
                    Log.w(TAG, "AuthV4.setup failed: $result")
                    onReady(false, lastSetupError)
                }
            }
        })
    }

    /**
     * 로그인.
     * 이전에 로그인한 기록이 있으면 자동 로그인(AUTO), 없으면 Hive 로그인 UI 를 띄운다.
     */
    fun signIn(onResult: (json: JSONObject) -> Unit) {
        if (!isEnabled) {
            onResult(fail("hive_not_configured"))
            return
        }
        if (!isSetupDone) {
            // setup 이 아직 안 끝났거나 실패한 상태. 버튼을 누른 김에 한 번 더 시도한다.
            setup { success, message ->
                if (success) signIn(onResult)
                else onResult(fail("setup_not_done: $message"))
            }
            return
        }

        val listener = object : AuthV4.AuthV4SignInListener {
            override fun onAuthV4SignIn(result: ResultAPI, playerInfo: AuthV4.PlayerInfo?) {
                if (result.isSuccess && playerInfo != null) {
                    onResult(playerJson(playerInfo))
                } else {
                    Log.w(TAG, "signIn failed: $result")
                    onResult(fail("sign_in_failed: ${result.errorCode}"))
                }
            }
        }

        if (autoSignInAvailable) {
            AuthV4.signIn(AuthV4.ProviderType.AUTO, listener)
        } else {
            AuthV4.showSignIn(listener)
        }
    }

    /** 게스트 로그인. 계정 연동 없이 바로 플레이하게 할 때 쓴다. */
    fun signInGuest(onResult: (json: JSONObject) -> Unit) {
        if (!isEnabled) {
            onResult(fail("hive_not_configured"))
            return
        }
        if (!isSetupDone) {
            setup { success, message ->
                if (success) signInGuest(onResult)
                else onResult(fail("setup_not_done: $message"))
            }
            return
        }
        AuthV4.signIn(AuthV4.ProviderType.GUEST, object : AuthV4.AuthV4SignInListener {
            override fun onAuthV4SignIn(result: ResultAPI, playerInfo: AuthV4.PlayerInfo?) {
                if (result.isSuccess && playerInfo != null) onResult(playerJson(playerInfo))
                else onResult(fail("guest_sign_in_failed: ${result.errorCode}"))
            }
        })
    }

    fun signOut(onResult: (json: JSONObject) -> Unit) {
        if (!isEnabled || !isSetupDone) {
            onResult(fail("hive_not_ready"))
            return
        }
        AuthV4.signOut(object : AuthV4.AuthV4SignOutListener {
            override fun onAuthV4SignOut(result: ResultAPI) {
                autoSignInAvailable = false
                onResult(JSONObject().put("ok", result.isSuccess))
            }
        })
    }

    /** 현재 로그인 상태를 동기적으로 반환. 로그인 전이면 ok=false. */
    fun currentPlayer(): JSONObject {
        if (!isEnabled) return fail("hive_not_configured")
        val info = runCatching { AuthV4.getPlayerInfo() }.getOrNull()
            ?: return fail("not_signed_in")
        return playerJson(info)
    }

    /** UI 가 버튼 상태를 정할 수 있도록 현재 Hive 상태를 노출한다. */
    fun statusJson(): JSONObject = JSONObject()
        .put("enabled", isEnabled)
        .put("setupDone", isSetupDone)
        .put("setupInProgress", setupInProgress)
        .put("lastError", lastSetupError)
        .put("appId", BuildConfig.HIVE_APP_ID)
        .put("zone", BuildConfig.HIVE_ZONE)
        .put("hasCertificationKey", BuildConfig.HIVE_CERTIFICATION_KEY.isNotBlank())

    private fun playerJson(info: AuthV4.PlayerInfo): JSONObject = JSONObject()
        .put("ok", true)
        .put("playerId", info.playerId.toString())
        .put("playerName", info.playerName)
        .put("playerImageUrl", info.playerImageUrl)
        .put("isGuest", info.isGuest())
        .put("isNewUser", info.isNewUser)
        .put("did", info.did)

    private fun fail(reason: String): JSONObject =
        JSONObject().put("ok", false).put("reason", reason)
}
