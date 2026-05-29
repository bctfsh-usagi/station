# 다음정류장은 - Web Prototype

GitHub Pages에 바로 올릴 수 있는 정적 웹앱 패키지입니다.

## 파일 구조

```txt
index.html
manifest.json
service-worker.js
assets/
  icons/
  passengers/
```

## GitHub Pages 배포 방법

1. 새 GitHub repository를 만듭니다.
2. 이 폴더 안의 파일을 전부 업로드합니다. `index.html`이 repository 최상단에 있어야 합니다.
3. Repository의 **Settings → Pages**로 이동합니다.
4. **Build and deployment → Source**를 `Deploy from a branch`로 설정합니다.
5. Branch를 `main`, folder를 `/root`로 선택하고 Save합니다.
6. 잠시 후 생성되는 `https://...github.io/...` 주소를 iPhone Safari에서 엽니다.
7. Safari 공유 버튼 → **홈 화면에 추가**를 누르면 주소창 없는 앱 모드로 테스트할 수 있습니다.

## 주의

iPhone의 파일 앱/미리보기 화면에서는 JavaScript 실행이 제한될 수 있습니다.
반드시 GitHub Pages 같은 HTTPS 웹주소에서 확인해주세요.
