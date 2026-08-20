# 다음정류장은 v5 Prototype

한국 지하철의 “먼저 내리고 타기”를 핵심 룰로 넣은 모바일 웹앱 프로토타입입니다.

## GitHub Pages 배포

1. 이 폴더의 모든 파일을 GitHub repository 최상단에 업로드합니다.
2. `index.html`, `manifest.json`, `service-worker.js`, `assets/`가 최상단에 있어야 합니다.
3. GitHub repository에서 `Settings → Pages`로 이동합니다.
4. `Deploy from a branch`를 선택합니다.
5. Branch: `main`, Folder: `/root`를 선택하고 저장합니다.
6. 생성된 `https://...github.io/...` 주소를 iPhone Safari에서 엽니다.
7. Safari 공유 버튼 → `홈 화면에 추가`를 누르면 주소창 없는 앱처럼 실행됩니다.

## v5 변경점

- 열차 안에 기존 승객이 먼저 탑승해 있습니다.
- 노란 `하차` 배지가 붙은 승객을 먼저 눌러 내려야 합니다.
- 하차 승객이 남아 있으면 플랫폼 승객을 태울 수 없습니다.
- 플랫폼 승객은 옷색과 같은 목적지 칸에 태워야 합니다.
- 플랫폼 승객 UI 겹침을 줄이기 위해 성별/탑승 태그를 제거했습니다.

## 안드로이드 APK / 하이브 / 광고

안드로이드 앱으로 빌드하고 실제 광고와 Hive 로그인을 붙이는 방법은
**[docs/ANDROID_HIVE_GUIDE.md](docs/ANDROID_HIVE_GUIDE.md)** 를 보세요.

- 앱 프로젝트: `android/` (Android Studio 로 이 폴더를 엽니다)
- APK 자동 빌드: GitHub **Actions → Android APK → Run workflow** → Artifacts 에서 다운로드
- 설정값은 전부 `android/gradle.properties` 한 파일에 모여 있습니다
