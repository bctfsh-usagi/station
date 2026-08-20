package com.station.nextstop

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Hive SDK 가 남긴 실제 통신 로그를 꺼내 공유한다.
 *
 * 왜 필요한가: AuthV4 의 setMetadataToConfiguration 은 서버 응답이 실패여도
 * primaryClientId 가 비었다는 이유로 항상 "unknown client" 를 돌려준다.
 * 즉 진짜 원인이 앱 화면에서는 보이지 않는다.
 * Hive SDK 는 useLog=true 일 때 요청/응답을 Logcat 에 남기므로 그걸 읽어온다.
 *
 * Android 4.1+ 에서 앱은 별도 권한 없이 "자기 프로세스의" 로그만 읽을 수 있다.
 */
object Diagnostics {

    private const val TAG = "NextStopDiag"
    private const val MAX_LINES = 120
    private const val MAX_LINE_CHARS = 500
    private const val KEY_WINDOW = 1400

    /** Hive 통신·초기화와 관련된 줄만 남긴다. 전체 로그는 길어서 붙여넣기가 어렵다. */
    private val INTERESTING = Regex(
        "HIVE|hive|AuthV4|provision|metadata-init|NextStop|Emulator|oauth",
        RegexOption.IGNORE_CASE
    )

    /** 내용이 없는 줄. */
    private val NOISE = Regex("""chatty|expire \d+ line""")

    /**
     * metadata-init 응답에는 Hive 전역 도메인 화이트리스트가 수만 자 실려 온다.
     * 줄을 통째로 버리면 같은 줄에 있는 oauth_clients_info 까지 날아가므로
     * 도메인 항목만 지워서 줄을 압축한다.
     */
    private val DOMAIN_ENTRY = Regex("""\{"protocol":"[^"]*","domain":"[^"]*"\},?""")

    /** 실패 원인을 가르는 값. 이 키가 있는 줄은 주변을 넉넉히 남긴다. */
    private const val KEY_OF_INTEREST = "oauth_clients_info"

    /** 최근 로그를 모아 문자열로 돌려준다. 비밀값은 반드시 마스킹한다. */
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

        val lines = raw.lineSequence()
            .filter { INTERESTING.containsMatchIn(it) }
            .filterNot { NOISE.containsMatchIn(it) }
            .map { condense(it) }
            .filter { it.isNotBlank() }
            .toList()

        // oauth_clients_info 가 담긴 줄은 잘려나가지 않도록 반드시 포함시킨다.
        val keyLines = lines.filter { it.contains(KEY_OF_INTEREST) }
        val body = (keyLines + lines.takeLast(MAX_LINES)).distinct().joinToString("\n")

        return redact(body.ifBlank { raw.takeLast(20_000) })
    }

    /** 긴 줄을 읽을 수 있는 크기로 줄인다. */
    private fun condense(line: String): String {
        val stripped = DOMAIN_ENTRY.replace(line, "")
        if (stripped.length <= MAX_LINE_CHARS) return stripped

        val idx = stripped.indexOf(KEY_OF_INTEREST)
        if (idx >= 0) {
            val from = (idx - 200).coerceAtLeast(0)
            val to = (idx + KEY_WINDOW).coerceAtMost(stripped.length)
            return "…" + stripped.substring(from, to) + "…"
        }
        return stripped.take(MAX_LINE_CHARS) + " …(잘림)"
    }

    /** 로그에 섞여 나올 수 있는 비밀값을 가린다. */
    private fun redact(text: String): String {
        var out = text
        BuildConfig.HIVE_CERTIFICATION_KEY
            .takeIf { it.isNotBlank() }
            ?.let { out = out.replace(it, "***REDACTED_HIVE_CERT_KEY***") }
        out = out.replace(
            Regex("""("(?:access_?token|player_?token|refresh_?token)"\s*:\s*")[^"]+""", RegexOption.IGNORE_CASE)
        ) { it.groupValues[1] + "***" }
        return out
    }

    /** 직전 실행의 크래시 리포트를 로그와 함께 내보낸다. */
    fun shareCrash(activity: Activity, crashReport: String) {
        share(activity, prefix = crashReport + "\n\n=== 이번 실행 로그 ===\n")
    }

    /** 콘솔에서 유저를 조회할 때 필요한 값이라 헤더에 같이 적는다. */
    private fun playerLine(): String = runCatching {
        val info = com.hive.AuthV4.getPlayerInfo() ?: return@runCatching "not signed in"
        "id=" + info.playerId + ", name=" + info.playerName
    }.getOrElse { "unavailable" }

    /** 공유 시트를 띄워 로그를 밖으로 보낼 수 있게 한다. */
    fun share(activity: Activity, prefix: String = "") {
        val playerLine = playerLine()
        val header = buildString {
            append("=== NextStop 진단 로그 ===\n")
            append("appId=${BuildConfig.HIVE_APP_ID}\n")
            append("zone=${BuildConfig.HIVE_ZONE}\n")
            append("certKey=${if (BuildConfig.HIVE_CERTIFICATION_KEY.isNotBlank()) "set" else "NOT set"}\n")
            append("package=${BuildConfig.APPLICATION_ID}\n")
            append("versionName=${BuildConfig.VERSION_NAME}\n")
            append("player=$playerLine\n\n")
        }
        val text = header + prefix + collect()

        // Hive SDK 가 에뮬레이터 탐지 결과를 클립보드에 써넣기 때문에(useLog=true),
        // 사용자가 "복사가 안 된다"고 느끼게 된다. 공유할 때 클립보드도 다시 채워준다.
        runCatching {
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("NextStop diagnostics", text))
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NextStop Hive 진단 로그")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, "진단 로그 보내기"))
    }
}
