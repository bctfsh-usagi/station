package com.station.nextstop

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 앱이 시작 직후 죽으면 화면에서 아무것도 확인할 수 없다.
 * 스택 트레이스를 파일로 남겨 두었다가 다음 실행 때 꺼내 보낼 수 있게 한다.
 */
object CrashReporter {

    private const val TAG = "NextStopCrash"
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stack = StringWriter().also { sw ->
                    PrintWriter(sw).use { throwable.printStackTrace(it) }
                }.toString()

                val report = buildString {
                    append("=== 이전 실행에서 앱이 종료됨 ===\n")
                    append("thread: ${thread.name}\n")
                    append("versionName: ${BuildConfig.VERSION_NAME}\n")
                    append("hiveAppId: ${BuildConfig.HIVE_APP_ID}\n")
                    append("hiveZone: ${BuildConfig.HIVE_ZONE}\n\n")
                    append(stack)
                }
                File(appContext.filesDir, FILE_NAME).writeText(report)
                Log.e(TAG, "crash captured", throwable)
            }
            // 기본 처리(프로세스 종료)는 그대로 넘긴다. 삼키면 앱이 좀비 상태가 된다.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 직전 실행에서 크래시가 있었으면 그 내용을 돌려주고 파일은 지운다. */
    fun consume(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()
        runCatching { file.delete() }
        return text
    }
}
