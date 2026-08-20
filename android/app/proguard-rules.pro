# --- WebView JavaScript bridge ---------------------------------------------
# @JavascriptInterface 로 노출한 메서드는 이름이 바뀌면 JS 에서 호출할 수 없다.
-keepclassmembers class com.station.nextstop.NativeBridge {
    public *;
}
-keepattributes JavascriptInterface
-keepattributes *Annotation*

# --- Hive SDK ---------------------------------------------------------------
# Hive SDK 는 리플렉션으로 provider 모듈을 찾으므로 난독화에서 제외한다.
-keep class com.hive.** { *; }
-keep class com.com2us.** { *; }
-dontwarn com.hive.**
-dontwarn com.com2us.**

# --- Google Mobile Ads ------------------------------------------------------
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**
