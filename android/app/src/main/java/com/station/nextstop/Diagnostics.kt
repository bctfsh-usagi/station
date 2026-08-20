package com.station.nextstop

import android.app.Activity
import android.content.Intent
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Hive SDK 가 남긴 실제 통신 로그를 꺼내 공유한다.
 *
 * 왜 필요한가: AuthV4 의 setMetadataToConfiguration 은 서버 응답이 실패여도
 * primaryClientId 가 비었다는 이유로 항상 "unknown client" 를 돌려준다.
 * 즉 진짜 원인(HTTP 에러/서버 에러 코드)이 앱 화면에서는 보이지 않는다.
 * Hive SDK 는 useLog=true 일 때 요청/응답을 Logcat 에 남기므로 그걸 읽어온다.
 *
 * Android 4.1+ 에서 앱은 별도 권한 없이 "자기 프로세스의" 로그만 읽을 수 있다.
 */
object Diagnostics {

    private const val TAG = "NextStopDiag"
    private const val MAX_LINES = 120

    /** Hive 통신·초기화와 관련된 줄만 남긴다. 전체 로그는 길어서 붙여넣기가 어렵다. */
    private val INTERESTING = Regex(
        "HIVE|hive|AuthV4|provision|metadata-init|qpyou|withhive|NextStop|Emulator",
        RegexOption.IGNORE_CASE
    )

    /**
     * 최근 로그를 모아 문자열로 돌려준다.
     * Hive 인증키가 요청 본문 로그에 그대로 찍히므로 반드시 마스킹한다.
     */
    fun collect(): String {
        val raw = runCatching {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "time", "--pid", android.os.Process.myPid().toString())
            )
            BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        }.getOrElse { e ->
            Log.w(TAG, "logcat read failed", e)
            return "로그를 읽지 못했습니다: ${e.message}"
        }

        val filtered = raw.lineSequence()
            .filter { INTERESTING.containsMatchIn(it) }
            .toList()
            .takeLast(MAX_LINES)
            .joinToString("\n")

        val body = filtered.ifBlank { raw.lineSequence().toList().takeLast(MAX_LINES).joinToString("\n") }
        return redact(body)
    }

    /** 로그에 섞여 나올 수 있는 비밀값을 가린다. */
    private fun redact(text: String): String {
        var out = text
        BuildConfig.HIVE_CERTIFICATION_KEY
            .takeIf { it.isNotBlank() }
            ?.let { out = out.replace(it, "***REDACTED_HIVE_CERT_KEY***") }
        // 토큰류도 값 부분을 가린다.
        out = out.replace(Regex("""("(?:access_?token|player_?token|refresh_?token)"\s*:\s*")[^"]+"""", RegexOption.IGNORE_CASE)) {
            it.groupValues[1] + "***\""
        }
        return out
    }

    /** 공유 시트를 띄워 로그를 밖으로 보낼 수 있게 한다. */
    fun share(activity: Activity) {
        val header = buildString {
            append("=== NextStop 진단 로그 ===\n")
            append("appId=${BuildConfig.HIVE_APP_ID}\n")
            append("zone=${BuildConfig.HIVE_ZONE}\n")
            append("certKey=${if (BuildConfig.HIVE_CERTIFICATION_KEY.isNotBlank()) "set" else "NOT set"}\n")
            append("package=${BuildConfig.APPLICATION_ID}\n")
            append("versionName=${BuildConfig.VERSION_NAME}\n\n")
        }
        val text = header + collect()

        // Hive SDK 가 에뮬레이터 탐지 결과를 클립보드에 써넣기 때문에(useLog=true),
        // 사용자가 "복사가 안 된다"고 느끼게 된다. 공유할 때 클립보드도 다시 채워준다.
        runCatching {
            val cm = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("NextStop diagnostics", text))
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NextStop Hive 진단 로그")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, "진단 로그 보내기"))
    }
}
