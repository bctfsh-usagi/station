import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/** gradle.properties 값을 읽되, 비어 있으면 기본값으로 대체한다. */
fun prop(name: String, fallback: String = ""): String =
    (project.findProperty(name) as String?)?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback

// 구글이 공개한 공식 테스트 광고 ID.
// 실제 ID 를 넣기 전까지는 이 값으로 빌드되어, 정책 위반 없이 광고 동작을 확인할 수 있다.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testRewardedUnitId = "ca-app-pub-3940256099942544/5224354917"
val testInterstitialUnitId = "ca-app-pub-3940256099942544/1033173712"

val admobAppId = prop("admob.appId", testAdmobAppId)
val admobRewardedUnitId = prop("admob.rewardedUnitId", testRewardedUnitId)
val admobInterstitialUnitId = prop("admob.interstitialUnitId", testInterstitialUnitId)
val usingTestAds = admobAppId == testAdmobAppId

val hiveAppId = prop("hive.appId")
val hiveZone = prop("hive.zone", "SANDBOX").uppercase()

/**
 * 게임 원본(리포지토리 최상단의 index.html, assets/)을 APK 의 assets/game/ 으로 복사한다.
 * 웹(GitHub Pages)과 APK 가 같은 파일 하나를 공유하도록 하기 위한 것으로,
 * 게임 파일을 android/ 아래에 중복 보관하지 않는다.
 */
val copyGameAssets = tasks.register<Sync>("copyGameAssets") {
    from(rootProject.file("..")) {
        include("index.html")
        include("assets/**")
    }
    into(layout.buildDirectory.dir("generated/gameAssets/game"))
}

/**
 * hive_config.xml 을 gradle.properties 값으로부터 생성한다.
 * Hive SDK 는 res/raw/hive_config.xml 을 읽으므로(Resource.openRawResource(..., defType="raw")),
 * AppID 같은 값을 소스에 하드코딩하지 않고 빌드 설정에서 주입하기 위한 것이다.
 */
val generateHiveConfig = tasks.register("generateHiveConfig") {
    val outputDir = layout.buildDirectory.dir("generated/hiveRes")
    val appId = hiveAppId
    val googleServerClientId = prop("hive.googleServerClientId")
    inputs.property("appId", appId)
    inputs.property("googleServerClientId", googleServerClientId)
    outputs.dir(outputDir)
    doLast {
        val rawDir = outputDir.get().asFile.resolve("raw").apply { mkdirs() }
        val googleBlock = if (googleServerClientId.isNotEmpty())
            "\n        <google serverClientId=\"$googleServerClientId\" />"
        else
            "\n        <!-- Google 로그인을 쓰려면 gradle.properties 의 hive.googleServerClientId 를 채우세요. -->"
        rawDir.resolve("hive_config.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<!-- 이 파일은 android/app/build.gradle.kts 가 생성합니다. 직접 수정하지 마세요. -->
<!-- 값은 android/gradle.properties 에서 바꿉니다. -->
<properties>
    <appId>$appId</appId>
    <useLog>true</useLog>
    <company>C2S</company>
    <channel>C2S</channel>
    <market>GO</market>
    <hiveOrientation>portrait</hiveOrientation>
    <providers>$googleBlock
    </providers>
</properties>
""",
            Charsets.UTF_8
        )
    }
}

android {
    namespace = "com.station.nextstop"
    compileSdk = 35

    defaultConfig {
        applicationId = prop("nextstop.applicationId", "com.station.nextstop")
        minSdk = 24
        targetSdk = 35
        versionCode = prop("nextstop.versionCode", "1").toInt()
        versionName = prop("nextstop.versionName", "1.0.0")

        // AdMob 앱 ID 는 매니페스트 meta-data 로 넣어야 한다. 값이 없으면 SDK 초기화 시 앱이 죽는다.
        manifestPlaceholders["admobAppId"] = admobAppId

        buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$admobRewardedUnitId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"$admobInterstitialUnitId\"")
        buildConfigField("boolean", "USING_TEST_ADS", "$usingTestAds")
        buildConfigField("String", "HIVE_APP_ID", "\"$hiveAppId\"")
        buildConfigField("String", "HIVE_ZONE", "\"$hiveZone\"")
    }

    // 주의: assets srcDir 은 복사 대상의 *부모* 여야 APK 안에서 assets/game/... 경로가 된다.
    //       (gameAssets/game 을 직접 가리키면 assets/index.html 로 들어가 버린다)
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/gameAssets"))
    sourceSets["main"].res.srcDir(layout.buildDirectory.dir("generated/hiveRes"))

    signingConfigs {
        // 릴리스 서명 정보는 저장소에 커밋하지 않는다.
        // 로컬:  android/keystore.properties
        // CI:    ANDROID_KEYSTORE_* 환경변수 (workflow 가 keystore.properties 를 만들어 준다)
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        if (keystorePropertiesFile.exists()) {
            val keystoreProperties = Properties().apply {
                keystorePropertiesFile.inputStream().use { load(it) }
            }
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES")
    }
}

tasks.named("preBuild") {
    dependsOn(copyGameAssets, generateHiveConfig)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)

    // 실제 광고
    implementation(libs.play.services.ads)
    // GDPR/EEA 동의 수집 (실제 광고 송출에 필요)
    implementation(libs.user.messaging.platform)

    // Hive SDK v4
    implementation(libs.hive.sdk)
    implementation(libs.hive.authv4.google.signin)
}
