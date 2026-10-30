# VideoGrab — 원탭 영상 저장 (Android)

다른 앱(유튜브, 브라우저 등)에서 **공유 → VideoGrab** 을 누르면 화면 전환 없이 바로 다운로드가 시작되고,
끝나면 갤러리(`Movies/VideoGrab`, 소리만은 `Music/VideoGrab`)에 저장되는 개인용 앱입니다.

- 엔진: [yt-dlp](https://github.com/yt-dlp/yt-dlp) (youtubedl-android 0.18.1 — Python·FFmpeg·QuickJS 포함). 유튜브 등 수천 개 사이트 지원.
- 패키지 `com.miu.videograb` · versionName `0.1.0` (versionCode 1) · minSdk 30 · targetSdk 35
- 중고거래 앱(`06-fold-deal-board`)과 코드·DB를 공유하지 않는 독립 프로젝트입니다.

> 본인이 권리를 가졌거나 다운로드가 허용된 영상만 저장하세요. 각 사이트의 이용약관과 저작권을 지켜야 합니다.
> 저작권 보호(DRM)가 걸린 영상은 받지 않으며, 로그인·우회 기능은 넣지 않았습니다.

## 기능 (0.1.0)

| 기능 | 상태 |
|---|---|
| 공유 받기 → 즉시 대기열 추가(화면 없음, 토스트로 안내) | 구현 |
| 링크 여러 개를 연달아 공유하거나 한 번에 붙여넣으면 순서대로 받기, 같은 링크 중복 방지 | 구현 |
| 화질: 1080p(기본) · 720p · 최고 화질 · 소리만(m4a). mp4(H.264)·m4a 우선 | 구현 |
| 백그라운드 다운로드(WorkManager 포그라운드 작업, 진행 알림), 취소·다시 시도 | 구현 |
| 앱이 강제 종료되면 다음 실행 때 이어 받기 | 구현 |
| 실패 이유를 한국어로 안내(비공개·로그인 필요·DRM·지역 제한·네트워크·공간 부족 등) | 구현 |
| 엔진 자동 업데이트(하루 1회, 안정판) + 사이트 변경 오류 시 업데이트 후 그 링크만 1회 자동 재시도 | 구현 |
| Wi-Fi에서만 받기, 엔진 수동 업데이트 | 구현 |
| 재생목록 링크: 최대 50개 | 구현(기본 수준) |
| 앱 내 브라우저·로그인 쿠키·자막·화질 직접 고르기·내장 플레이어 | 미구현 → `docs/TASKS.md` |

**실기기에서 아직 실행해 보지 않았습니다.** 검증 범위는 `docs/TEST_REPORT.md`.

## 빌드

필요: JDK 17+, Android SDK Platform 35 / Build-Tools 35.0.0.

```bash
cd video-downloader/android
./gradlew testDebugUnitTest lintDebug assembleDebug
# 폴드(arm64) 설치 파일: app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

기기 종류별 APK가 따로 나옵니다(Python 런타임 때문에 하나에 약 80MB). 갤럭시 폴드는 **arm64-v8a**.

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## 문서

`docs/PROGRESS.md` 진행 · `docs/TASKS.md` 할 일 · `docs/DECISIONS.md` 결정 · `docs/TEST_REPORT.md` 검증 ·
`docs/SECURITY.md` 권한·보안 · `docs/REFERENCE_NOTES.md` 참고 저장소 분석
