# 「다음 정거장은」 안드로이드 출시 가이드

이 문서 하나만 따라가면 **APK 만들기 → 실제 광고 붙이기 → Hive 연동 → 구글 플레이 출시**까지 끝납니다.

---

## 0. 지금 리포지토리에 들어있는 것

| 경로 | 내용 |
|---|---|
| `android/` | 안드로이드 앱 프로젝트 (Kotlin, WebView 래퍼) |
| `android/gradle.properties` | **여러분이 값을 채워 넣을 유일한 파일** (광고 ID, Hive AppID 등) |
| `.github/workflows/android.yml` | GitHub Actions 에서 APK 를 자동으로 빌드 |
| `index.html` | 게임 본체. 웹과 APK 가 **같은 파일 하나**를 씁니다 |

게임 파일을 `android/` 아래에 복사해두지 않았습니다. 빌드할 때 Gradle 이 최상단의
`index.html` 과 `assets/` 를 APK 의 `assets/game/` 으로 복사합니다.
**게임을 고치면 웹과 APK 양쪽에 자동 반영**됩니다.

> **검증 상태 (실기기 확인 완료)**
>
> | 항목 | 상태 |
> |---|---|
> | APK 빌드 (GitHub Actions) | ✅ |
> | APK 안에 게임 파일 포함 | ✅ CI 가 매 빌드 확인 |
> | 실기기 설치·실행 | ✅ |
> | 게임 플레이 / 이미지 / 진행 저장 | ✅ |
> | **보상형 광고 재생 + 보상 지급** | ✅ 실기기 확인 |
> | **Hive AuthV4 게스트 로그인** | ✅ 실기기 확인 |
>
> Hive SDK 26.6.0 · AdMob 23.6.0 · UMP 3.1.0 이 정상 링크됩니다.

### 이미 동작하는 것 (설정 없이)

- APK 빌드 → 설치 → 게임 실행
- **광고**: 구글 공식 *테스트 광고*가 실제로 뜹니다. "광고 보고 이어하기"를 누르면
  진짜 보상형 광고가 재생되고, 끝까지 봐야 열차 1대를 받습니다.
- 2스테이지마다 전면 광고 1회

### 여러분이 채워야 하는 것 (계정이 필요한 부분)

- AdMob 광고 단위 ID → 실제 수익 발생
- Hive AppID → Hive 로그인
- 서명 키(keystore) → 플레이 스토어 업로드

---

## 1. 가장 빠른 길 — GitHub Actions 에서 APK 받기

Android Studio 설치 없이 APK 를 받는 방법입니다.

1. GitHub 리포지토리 → **Actions** 탭
2. 왼쪽에서 **Android APK** 워크플로 선택
3. 브랜치를 고르고 **Run workflow** 클릭 (푸시할 때마다 자동으로도 돕니다)
4. 초록불이 뜨면 실행 화면 맨 아래 **Artifacts** → `nextstop-debug-apk` 다운로드
5. 압축을 풀면 `app-debug.apk` — 안드로이드 폰에 옮겨 설치

> 폰에서 "출처를 알 수 없는 앱" 설치를 허용해야 합니다.
> 설정 → 앱 → 특별한 앱 액세스 → 알 수 없는 앱 설치 → (파일 관리자/크롬) 허용

이 디버그 APK 는 **테스트 광고**가 나오고 Hive 는 꺼져 있습니다. 게임 동작 확인용입니다.

### 설치한 뒤 확인할 것 (체크리스트)

| # | 확인 항목 | 정상이면 |
|---|---|---|
| 1 | 앱 아이콘이 지하철 로고로 보이고, 이름이 "다음 정거장은" | ✅ |
| 2 | 앱을 켰을 때 **흰 화면/검은 화면이 아니라** 타이틀 화면이 뜬다 | ✅ 게임 파일이 APK 에 잘 들어갔다는 뜻 |
| 3 | 화면을 탭하면 인트로 → 게임이 시작된다 | ✅ |
| 4 | 승객/열차 이미지가 깨지지 않고 다 보인다 | ✅ 이미지 에셋 정상 |
| 5 | 한 판 지고 **"광고 보고 이어하기"** 를 누른다 | 진짜 전면 광고가 뜨고 "Test Ad" 표시가 보인다 |
| 6 | 광고를 **끝까지** 본다 | 닫으면 열차 1대가 추가되고 게임이 이어진다 |
| 7 | 다시 지고, 광고를 **중간에 X 로 닫는다** | 보상 없이 게임오버 화면으로 돌아오고 "광고를 끝까지 봐야…" 토스트가 뜬다 |
| 8 | 스테이지를 2개 클리어한다 | 사이에 전면 광고가 한 번 뜬다 |
| 9 | 앱을 껐다 다시 켠다 | 진행한 레벨이 유지된다 (localStorage 저장) |
| 10 | 타이틀 화면 우상단 | **버튼이 없는 게 정상** (Hive AppID 미설정이라 숨김) |

5번에서 광고가 안 뜨고 "광고를 불러오는 중이에요" 만 나오면,
네트워크 확인 후 10~20초 뒤 다시 눌러 보세요. 그래도 안 되면
`adb logcat | grep NextStopAds` 로그를 알려주세요.

> 5·6·7번 광고는 **테스트 광고**입니다. 몇 번을 눌러도 계정에 아무 문제 없습니다.
> 자세한 이유는 아래 3-4 를 보세요.

---

## 2. 내 PC에서 빌드하기 (Android Studio)

1. [Android Studio](https://developer.android.com/studio) 설치
2. Android Studio → **Open** → 이 리포지토리의 **`android` 폴더**를 엽니다
   (리포지토리 최상단이 아니라 `android/` 입니다)
3. 첫 실행 시 Gradle 이 SDK 를 내려받습니다 (5~10분)
4. 상단 초록 ▶ 버튼으로 실기기/에뮬레이터 실행

터미널만 쓸 경우:

```bash
cd android
./gradlew assembleDebug
# 결과: android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 3. 실제 광고 붙이기 (AdMob)

가장 궁금해하신 부분입니다. **결론부터: 됩니다. 아래 3단계면 끝납니다.**

### 3-1. AdMob 계정과 앱 등록

1. <https://admob.google.com> 접속 → 갖고 계신 구글 계정으로 가입
   (AdSense 계정이 자동으로 함께 만들어집니다)
2. **앱 → 앱 추가**
   - "앱이 앱스토어에 등록되어 있나요?" → 아직이면 **아니요**
   - 플랫폼: **Android**, 앱 이름: `다음 정거장은`
3. 만들어진 앱의 **앱 ID** 를 복사 — `ca-app-pub-XXXXXXXX~YYYYYYYY` 형태 (`~` 물결표)

### 3-2. 광고 단위 2개 만들기

**광고 단위 → 광고 단위 추가**

| 형식 | 이름 예시 | 쓰이는 곳 |
|---|---|---|
| **보상형** | `continue_reward` | "광고 보고 이어하기" |
| **전면 광고** | `stage_interstitial` | 2스테이지마다 |

각각 `ca-app-pub-XXXXXXXX/ZZZZZZZZ` 형태의 ID (`/` 슬래시)를 복사합니다.

### 3-3. 값 채우기

`android/gradle.properties` 를 열어 세 줄을 채웁니다.

```properties
admob.appId=ca-app-pub-1234567890123456~1234567890
admob.rewardedUnitId=ca-app-pub-1234567890123456/1111111111
admob.interstitialUnitId=ca-app-pub-1234567890123456/2222222222
```

다시 빌드하면 끝입니다. 코드는 한 줄도 고칠 필요 없습니다.

> **GitHub Actions 로 빌드한다면** 이 값을 파일에 적는 대신 리포지토리
> Settings → Secrets and variables → Actions 에 `ADMOB_APP_ID`,
> `ADMOB_REWARDED_UNIT_ID`, `ADMOB_INTERSTITIAL_UNIT_ID` 로 넣어도 됩니다.

### 3-4. 광고 테스트, 안전하게 하는 법

**핵심: 정지당하는 건 "실제 광고"를 본인이 클릭했을 때뿐입니다.**
테스트 광고는 애초에 개발자가 마음껏 눌러 보라고 구글이 만들어 둔 것이라 아무 문제 없습니다.

| | 테스트 광고 | 실제 광고 |
|---|---|---|
| 언제 나오나 | `admob.*` 값이 **비어 있을 때** (지금 상태) | 내 광고 단위 ID 를 채웠을 때 |
| 내가 눌러도 되나 | ✅ **몇 번이든 OK** | ❌ 절대 금지 |
| 수익 | 없음 | 발생 |
| 광고 내용 | "Test Ad" 라고 크게 표시됨 | 진짜 광고 |

즉 **지금 Actions 에서 받는 APK 는 테스트 광고 빌드**라서,
"광고 보고 이어하기"를 몇 번을 눌러도 계정에 아무 영향이 없습니다.

#### 실제 ID 를 넣은 뒤에도 안전하게 테스트하려면

내 폰을 **테스트 기기**로 등록하면, 실제 광고 ID 를 쓰면서도 그 폰에서만 테스트 광고가 나옵니다.

1. 실제 ID 를 넣은 APK 를 폰에 설치하고 한 번 실행합니다
2. PC 에서 `adb logcat | grep "RequestConfiguration"` 실행
   → `Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("ABC123..."))`
   같은 줄이 나옵니다. 따옴표 안의 값이 이 폰의 **테스트 기기 ID** 입니다
3. AdMob 콘솔 → 설정 → **테스트 기기** → 기기 추가 → 그 ID 입력
4. 이제 그 폰에서는 실제 광고 단위를 써도 테스트 광고가 나옵니다 — 눌러도 안전

> 그냥 편하게 하시려면: **평소엔 `admob.*` 를 비워 둔 채로 개발**하고,
> 스토어에 올릴 때만 실제 ID 를 채워서 빌드하는 방법이 가장 안전합니다.

### 3-5. ⚠️ 실제 광고를 켠 뒤 지킬 것

- **본인 광고를 절대 클릭하지 마세요.** AdMob 계정이 영구 정지될 수 있습니다.
  (친구/가족에게 "눌러 달라"고 부탁하는 것도 같은 사유로 걸립니다)
- 실제 광고는 **앱이 스토어에 올라가고 AdMob 앱-스토어 연결이 승인된 뒤**부터
  제대로 채워집니다. 그 전에는 "광고 재고 없음(no fill)"이 자주 뜨는 게 정상입니다.
- **결제 정보 등록**: AdMob → 결제 → 주소/세금 정보 입력을 마쳐야 수익이 지급됩니다.
- **app-ads.txt**: AdMob 이 안내하는 한 줄을 게임 웹사이트
  (예: GitHub Pages 주소) 최상단에 `app-ads.txt` 로 올려두면 단가가 올라갑니다.
- **EEA 동의**: 이 앱은 이미 UMP(사용자 메시지 플랫폼) 동의 창을 광고 초기화 전에
  띄웁니다. AdMob → 개인정보 보호 및 메시지 에서 **GDPR 메시지**를 한 번 만들어
  게시해 주세요. 안 만들면 유럽 사용자에게 광고가 나가지 않습니다.

---

## 4. Hive 연동

### 4-1. 지금 코드가 하는 일

- Hive SDK v4 (`com.com2us.android.hive:hive-sdk:26.6.0`, Maven Central 공개 배포)
- `AuthV4.setup()` → 자동 로그인 여부 확인 → `signIn()` / `showSignIn()`
- JS 에서 `window.NS_NATIVE.hiveSignIn(cb)` 로 호출 가능
- **`hive.appId` 가 비어 있으면 Hive 기능만 꺼진 채로 앱이 정상 동작합니다.**
  콘솔 등록 전에도 APK 가 죽지 않게 하려는 의도입니다.

### 4-2. Hive 콘솔에서 할 일

> **먼저 읽으세요 — 실제로 로그인이 되기까지 필요했던 4가지**
>
> | # | 항목 | 안 하면 생기는 일 |
> |---|---|---|
> | 1 | App ID 등록 (패키지명과 동일하게) | 앱을 식별하지 못함 |
> | 2 | **보안 키 발급** (보안 키 설정) | `-13 unknown client` 로 setup 실패 |
> | 3 | 로그인 종류 선택 + 저장 (게스트만이면 게스트만) | IdP 목록이 비어 로그인 화면이 안 뜸 |
> | 4 | **약관 등록 → 그룹 설정 → 연결** | 로그인 시 약관 단계에서 진행 불가 |
>
> 앱 코드 쪽에서는 `Configuration.useHercules = false` 와
> `pauseTimers()` 미사용이 필수입니다 (4-5-1, 4-5-2 참고).


1. <https://console.hiveplatform.ai> 에 Hive 계정으로 로그인
2. **프로젝트 생성** → 게임 이름 입력
3. **앱 등록**: 플랫폼 `Android`, 패키지명은
   `android/gradle.properties` 의 `nextstop.applicationId` 와 **정확히 같아야** 합니다
   (기본값 `com.station.nextstop` — 바꾸고 싶으면 여기서 바꾸세요)
4. 발급된 **AppID** 를 복사

### 4-3. 값 채우기

```properties
# android/gradle.properties
nextstop.applicationId=com.station.nextstop
hive.appId=여기에_콘솔에서_받은_AppID
hive.zone=SANDBOX      # 개발 중에는 SANDBOX, 출시할 때 REAL
```

`res/raw/hive_config.xml` 은 빌드할 때 이 값으로 **자동 생성**됩니다. 직접 만들 필요 없습니다.
생성되는 내용:

```xml
<properties>
    <appId>...</appId>
    <useLog>true</useLog>
    <company>C2S</company>
    <channel>C2S</channel>
    <market>GO</market>          <!-- GO = Google Play -->
    <hiveOrientation>portrait</hiveOrientation>
    <providers>...</providers>
</properties>
```

### 4-7. Hive 없이 먼저 출시하기

Hive 프로비저닝이 풀리지 않으면 Hive 만 끄고 출시할 수 있습니다.

```properties
# android/gradle.properties
hive.appId=
```

이렇게 두고 빌드하면 Hive 초기화를 아예 시도하지 않고, 타이틀의 로그인 버튼도
숨겨집니다. 게임과 광고는 그대로 동작합니다. 나중에 값만 다시 채우면 됩니다.

### 4-5. 보안 키 발급 (필수 · 빠뜨리기 쉬움)

**하이브 콘솔 → 보안 키 설정 → 보안 키 발급**

Hive 공식 문서(IdP 콘솔 키 안내)의 경고:

> 서비스 간 인증 보안 강화를 위해 *하이브 콘솔 > 보안 키 설정*에서 보안 키를 반드시 발급받아야 합니다.
> **보안 키를 발급받지 않으면, 사용자는 게임 앱에서 로그인할 수 없습니다.**

이 단계를 빠뜨리면 `AuthV4.setup()` 이
`-13 / AuthV4InvalidParam (Client authentication failed: unknown client.)` 로 실패합니다.
`provision/metadata-init-interaction` 응답의 `oauth_clients_info` 가 비어서
`primaryClientId` 가 채워지지 않기 때문입니다.

게스트 로그인만 쓰더라도 필요합니다. 같은 문서 표에서 Guest / Android 는
"설정 제외"(콘솔·hive_config 양쪽 입력 불필요)지만, 보안 키는 IdP 와 무관한
별도의 필수 단계입니다.

### 4-5-1. 앱이 로그인 직전에 소리 없이 종료된다면 (Hercules)

`AuthV4.setup()` 이 성공한 직후 앱이 크래시 없이 사라지면 게임 보안(Hercules) 때문입니다.

```kotlin
// HiveSdkLifecycle.onSetupFinished()
if (ConfigurationImpl.getUseHercules()) {
    try { Class.forName("com.hive.hercules.HerculesInitializer") }
    catch (e: Exception) { Android.finish() }   // finishAffinity + exitProcess(0)
}
```

`Configuration.useHercules` 의 **기본값이 true** 라서, Hercules 모듈을 넣지 않은 앱은
setup 이 끝나는 순간 종료됩니다. 크래시가 아니라 `exitProcess` 라 스택 트레이스도 남지 않습니다.

이 프로젝트는 게임 보안을 쓰지 않으므로 `HiveManager.setup()` 에서 꺼 둡니다:

```kotlin
Configuration.useHercules = false
```

게임 보안을 쓰려면 대신 `com.com2us.android.hive:hive-hercules` 의존성을 추가해야 합니다.

### 4-5-2. Hive 로그인 화면이 로딩만 하고 버튼이 안 눌린다면

게임을 WebView 로 감싼 앱에서 흔히 밟는 함정입니다.

```kotlin
// 하면 안 되는 것
override fun onPause() {
    webView.pauseTimers()   // 프로세스 안의 "모든" WebView JS 타이머를 멈춘다
}
```

`WebView.pauseTimers()` 는 해당 인스턴스가 아니라 **프로세스 전체**에 적용됩니다.
Hive 의 로그인·약관 화면도 WebView 이므로, 그 화면이 게임 위에 떠서 게임 Activity 가
`onPause` 되는 순간 **Hive 페이지의 JS 까지 멈춥니다.**

증상: 로딩 스피너가 계속 돌고 → `[AuthV4WebView] Mirror Url` 로 폴백하고 →
화면은 그려지지만 버튼이 눌리지 않고 → 결국 `sign_in_failed: -6` (CANCELED).

`webView.onPause()` 는 해당 WebView 에만 적용되므로 그것만 쓰면 됩니다.

### 4-6. HIVE 인증키

Hive 콘솔 **프로젝트 정보 → 기본정보 → HIVE 인증키** 값입니다.
`provision/*` 요청 전부에 실려 나가므로(GCPSDK4-284) 설정하지 않으면
서버가 클라이언트를 식별하지 못할 수 있습니다.

**이 저장소는 공개이므로 `gradle.properties` 에 직접 적지 마세요.**

- CI: 저장소 Settings → Secrets and variables → Actions →
  `HIVE_CERTIFICATION_KEY` 로 등록 (워크플로가 주입합니다)
- 로컬: `~/.gradle/gradle.properties` 에 `hive.certificationKey=...`

값 자체는 APK 안에 들어가 추출 가능하므로 비밀번호가 아니라 앱 식별자에
가깝지만, 저장소에 평문으로 남기지는 않습니다.

### 4-4. Google 로그인까지 쓰려면

1. Hive 콘솔에서 Google 로그인 provider 를 켭니다
2. Google Cloud Console → 사용자 인증 정보 → **OAuth 웹 클라이언트 ID** 생성
3. `hive.googleServerClientId=...` 에 넣으면 `hive_config.xml` 의
   `<google serverClientId="..."/>` 로 들어갑니다
4. 앱 서명 키의 **SHA-1 지문**을 Google Cloud 와 Hive 콘솔 양쪽에 등록해야 합니다

```bash
keytool -list -v -keystore android/release.jks -alias nextstop
```

> Hive 푸시(FCM)까지 쓰려면 Firebase 프로젝트의 `google-services.json` 과
> `hive-push-google-fcm` 모듈이 추가로 필요합니다. 이번 구성에는 넣지 않았습니다.

### 4-5. 게임 안에서 로그인 호출하기

`index.html` 어디서든 이렇게 쓸 수 있습니다.

```js
const NS = window.NS_NATIVE;
if (NS && NS.available && NS.hiveEnabled()) {
  NS.hiveSignIn(res => {
    if (res.ok) console.log('로그인 성공', res.playerId, res.playerName);
    else console.log('로그인 실패', res.reason);
  });
}
```

**타이틀 화면 우상단에 로그인 버튼이 이미 붙어 있습니다.**
`hive.appId` 를 채운 APK 에서만 나타나고, 웹이나 미설정 빌드에서는 숨겨집니다.
누르면 로그인 → 플레이어 이름 표시, 다시 누르면 로그아웃입니다.
위치나 디자인을 바꾸고 싶으면 `index.html` 의 `#hiveBtn` / `.hive-btn` 을 고치면 됩니다.

---

## 5. 서명 키 만들기 (스토어 업로드 필수)

플레이 스토어는 서명된 APK/AAB 만 받습니다. 키는 **한 번 만들면 끝까지 써야 하고,
잃어버리면 같은 앱을 업데이트할 수 없습니다.** 반드시 백업하세요.

```bash
keytool -genkeypair -v \
  -keystore android/release.jks \
  -alias nextstop \
  -keyalg RSA -keysize 2048 -validity 10000
```

`android/keystore.properties` 를 만듭니다 (이 파일은 `.gitignore` 에 있어 커밋되지 않습니다):

```properties
storeFile=release.jks
storePassword=위에서_입력한_비밀번호
keyAlias=nextstop
keyPassword=위에서_입력한_비밀번호
```

이제 릴리스 빌드가 됩니다:

```bash
cd android
./gradlew bundleRelease     # AAB — 플레이 스토어 업로드용
./gradlew assembleRelease   # APK — 직접 배포/테스트용
```

### GitHub Actions 에서 서명하려면

리포지토리 Settings → Secrets and variables → Actions 에 등록:

| Secret 이름 | 값 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 android/release.jks` 출력 |
| `ANDROID_KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `ANDROID_KEY_ALIAS` | `nextstop` |
| `ANDROID_KEY_PASSWORD` | 키 비밀번호 |

넣어두면 워크플로가 `nextstop-release` 아티팩트로 서명된 APK 와 AAB 를 함께 올려줍니다.

---

## 6. 구글 플레이 출시

1. <https://play.google.com/console> → 개발자 등록 (**1회 $25**)
2. **앱 만들기** → 이름 `다음 정거장은`, 게임, 무료
3. 필수 입력 항목
   - 앱 아이콘 512×512 → `docs/play-store-icon-512.png` 사용 가능
   - 그래픽 이미지 1024×500
   - 스크린샷 최소 2장 (폰)
   - 짧은 설명 / 자세한 설명
   - **개인정보처리방침 URL** — 광고를 넣으면 **필수**입니다.
     GitHub Pages 에 `privacy.html` 하나 올려서 그 주소를 쓰면 됩니다.
   - 데이터 보안 설문: 광고 ID 수집 **예**, 대략적 위치/기기 ID 항목 체크
   - 광고 포함 여부: **예**
   - 콘텐츠 등급 설문
4. **프로덕션 → 새 버전 만들기** → `app-release.aab` 업로드
5. 처음에는 **내부 테스트** 트랙으로 올려 본인 계정에 설치해 보길 권합니다
6. 심사 통과 후, AdMob → 앱 → **앱 스토어에 연결**을 눌러 스토어 등록정보와 이어주면
   실제 광고 채움률이 올라갑니다

> 신규 개인 개발자 계정은 프로덕션 출시 전에
> **비공개 테스트(테스터 12명 · 14일)** 를 요구받을 수 있습니다.

---

## 7. 문제 해결

**앱을 켰는데 흰 화면 / 검은 화면만 나옵니다**
`adb logcat | grep NextStop` 으로 `[web]` 로그를 확인하세요.
게임 파일 복사가 안 됐다면 `./gradlew :app:copyGameAssets --rerun-tasks` 후 재빌드.

**"광고를 불러오는 중이에요" 만 계속 나옵니다**
광고가 아직 로드되지 않은 상태입니다. 네트워크를 확인하고 10초쯤 뒤 다시 눌러 보세요.
실제 광고 ID 로 바꾼 직후에는 채움률이 낮아 자주 발생합니다 —
테스트할 때는 광고 ID 를 비워 테스트 광고로 돌리는 편이 확실합니다.

**Hive 로그인이 `setup_not_done` 을 반환합니다**
`hive.appId` 가 비었거나 `AuthV4.setup()` 이 실패한 경우입니다.
`adb logcat | grep NextStopHive` 로 실패 사유를 확인하세요. 흔한 원인 순서:

1. **패키지명 불일치** — Hive 콘솔의 App ID 와 `nextstop.applicationId` 가 달라야 합니다.
   Hive 는 App ID 를 패키지명으로 쓰므로 둘이 **같은 값**이어야 합니다.
   현재 둘 다 `com.station.nextstop` 입니다.
2. **존(zone) 불일치** — 콘솔에 등록한 앱이 아직 sandbox 에만 있거나 real 에만 있을 수 있습니다.
   `hive.zone` 을 `SANDBOX` ↔ `REAL` 로 바꿔서 다시 빌드해 보세요.
   (Hive SDK 가 지원하는 값은 이 둘뿐입니다)
3. **콘솔 등록 미완료** — App ID 등록 후 저장 단계가 남아 있으면 서버가 앱을 모릅니다.

**`setup_failed: -13 / AuthV4InvalidParam` (Client authentication failed: unknown client.)**

Hive 서버가 응답은 했지만 이 앱에 대한 `primaryClientId` 를 내려주지 않은 경우입니다.
SDK 소스 기준으로 정확히 이 조건입니다 (`AuthV4Impl.setMetadataToConfiguration`):

```kotlin
return response.primaryClientId.isNotBlank().also {
    if (it.not()) onSetupFail(... "Client authentication failed: unknown client.")
}
```

`primaryClientId` 는 `provision/metadata-init-interaction` 응답의
`oauth_clients_info` 배열에서 `issue_order == "0"` 인 항목의 `client_id` 입니다:

```kotlin
oauthClientsInfo = data.optJSONArray("oauth_clients_info")?.also { clientsInfo ->
    ...
    if (client.optString("issue_order") == "0") {
        primaryClientId = client.optString("client_id")
    }
}
```

**즉 이 App ID 에 OAuth 클라이언트가 아직 발급되지 않았다는 뜻입니다.**
이건 앱 코드로는 해결할 수 없고, Hive 콘솔에서 처리해야 합니다. 확인 순서:

1. App ID 옆 ⚠️ 경고가 남아 있는지 — 남아 있으면 프로비저닝 미완료입니다
2. 콘솔에서 **OAuth 클라이언트 / 인증 정보 발급** 항목을 찾아 발급했는지
3. 로그인 종류를 `게스트`만 남기고 **저장**했는지 (인증 키가 필요한 항목이 켜져 있으면 저장이 완료되지 않을 수 있습니다)
4. `hive.zone` 이 앱을 등록한 콘솔과 맞는지 (일반 콘솔 = `REAL`, 샌드박스 콘솔 = `SANDBOX`)
5. 설치된 APK 의 실제 패키지명이 등록한 App ID 와 **완전히 동일**한지
   (`adb shell pm list packages | grep station`) — 접미사가 붙으면 다른 앱으로 취급됩니다

**실제로 시도해 배제된 원인들** (모두 동일한 오류가 재현됨):

| 가설 | 결과 |
|---|---|
| `hive.zone` = SANDBOX / REAL | 둘 다 동일 |
| 디버그 빌드의 `.debug` 패키지 접미사 | 제거해도 동일 |
| 로그인 종류를 게스트만 남기고 저장 | 동일 |
| 콘솔의 "HIVE 인증 상태" 활성화 | 동일 |
| `hive.certificationKey` 주입 | 동일 (빌드 로그에서 주입 확인됨) |
| company / channel / market 값 | 해당 요청에 실리지 않아 구조적으로 무관 |

따라서 이 오류는 **앱/SDK 설정이 아니라 콘솔 계정 측 프로비저닝** 문제입니다.
Hive 지원팀에 아래 내용으로 문의하는 것이 가장 빠릅니다:

> **App ID 에 OAuth 클라이언트가 발급되지 않습니다**
>
> - 프로젝트: [3694] station
> - App ID: `com.station.nextstop` (Android / Google Play)
> - SDK: Hive SDK v4 Android 26.6.0
>
> `AuthV4.setup()` 이 아래 오류로 실패합니다.
> `-13 / AuthV4InvalidParam / [AuthV4-Common] Invalid param : Client authentication failed: unknown client.`
>
> SDK 코드 확인 결과 `provision/metadata-init-interaction` 응답의
> `oauth_clients_info` 배열이 비어 있어 `primaryClientId` 가 채워지지 않는 것이 원인입니다.
>
> 아래는 모두 시도했으나 동일합니다:
> zone real/sandbox 양쪽, 로그인 종류 게스트만 지정 후 저장,
> HIVE 인증 상태 활성화, HIVE 인증키 설정, 패키지명과 App ID 완전 일치.
> 콘솔에는 경고 표시가 없는 상태입니다.
>
> 이 App ID 에 OAuth 클라이언트를 발급받으려면 어떤 절차가 필요한지 확인 부탁드립니다.

**게임 진행이 저장되지 않습니다**
게임은 `localStorage` 를 씁니다. 앱 데이터를 지우면 함께 지워집니다.
계정 기반 저장이 필요하면 Hive 로그인 + Hive DataStore 연동이 필요합니다.

**빌드가 `SDK location not found` 로 실패합니다**
Android Studio 로 `android/` 를 한 번 열면 `local.properties` 가 자동 생성됩니다.
또는 `export ANDROID_HOME=~/Android/Sdk`.

---

## 8. 참고

- Hive 개발자 문서: <https://developers.hiveplatform.ai/ko/latest/>
- Hive SDK Android (오픈소스): <https://github.com/COM2USPLATFORM/hive-sdk-v4-android>
- AdMob 안드로이드 가이드: <https://developers.google.com/admob/android/quick-start>
- Play Console 정책: <https://play.google.com/about/developer-content-policy/>
