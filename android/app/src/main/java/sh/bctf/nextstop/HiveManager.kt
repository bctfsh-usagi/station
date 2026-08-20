package sh.bctf.nextstop

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

        try {
            Configuration.context = activity
            Configuration.appId = BuildConfig.HIVE_APP_ID
            Configuration.zone = runCatching {
                Configuration.ZoneType.valueOf(BuildConfig.HIVE_ZONE)
            }.getOrDefault(Configuration.ZoneType.SANDBOX)
            Configuration.useLog = BuildConfig.DEBUG
        } catch (t: Throwable) {
            Log.e(TAG, "Hive configuration failed", t)
            onReady(false, "configuration_failed: ${t.message}")
            return
        }

        AuthV4.setup(object : AuthV4.AuthV4SetupListener {
            override fun onAuthV4Setup(
                result: ResultAPI,
                isAutoSignIn: Boolean,
                did: String?,
                providerTypeList: ArrayList<AuthV4.ProviderType>?
            ) {
                if (result.isSuccess) {
                    isSetupDone = true
                    autoSignInAvailable = isAutoSignIn
                    Log.i(TAG, "AuthV4.setup ok (autoSignIn=$isAutoSignIn, did=$did)")
                    onReady(true, if (isAutoSignIn) "auto_sign_in_available" else "setup_done")
                } else {
                    Log.w(TAG, "AuthV4.setup failed: $result")
                    onReady(false, "setup_failed: ${result.errorCode}")
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
            onResult(fail("setup_not_done"))
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
            onResult(fail("setup_not_done"))
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
