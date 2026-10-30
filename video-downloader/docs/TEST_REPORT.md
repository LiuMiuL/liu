# TEST_REPORT — VideoGrab 0.1.0

환경: 2026-10-30, 클라우드 Linux 컨테이너, OpenJDK 21, Gradle 8.14.3, Android SDK 35. KVM 없음 → **에뮬레이터·실기기 실행 불가**.

## 실행 결과

```text
./gradlew assembleDebug lintDebug testDebugUnitTest → BUILD SUCCESSFUL
테스트: 32 tests / 0 failures / 0 errors
lint: 0 errors, 40 warnings (GradleDependency 36, AGP 버전 3, DataExtractionRules 1)
APK (debug, versionCode 1, 0.1.0)
  arm64-v8a   84,550,649 B  SHA-256 a1638ba726a7986887d3e4b49d70bd9410425f87b8244bce984f2852dab61680
  armeabi-v7a 77,878,793 B  SHA-256 24a42652752748e0637e5d39e71221581bc0a14ff05ca4ca0f342d5df43394fc
  x86_64      87,553,857 B  SHA-256 666e5f0d54b7bbda91b3f75e955d7588f8a4ec01439241df9131abcbf87f29bf
매니페스트: extractNativeLibs=true, SystemForegroundService foregroundServiceType=dataSync
```

## yt-dlp 옵션 실측 (데스크톱 yt-dlp 2026.10.26 + ffmpeg, 로컬 서버)

앱이 넘기는 옵션 목록 그대로 실행. 테스트 스트림: HLS 마스터(1080p·720p 영상 전용 + 별도 AAC 소리), 단일 mp4(한글 파일명).

| 화질 | 결과 |
|---|---|
| 1080p | 1920×1080 영상 + 소리 합쳐진 mp4 |
| 720p | 1280×720 + 소리 mp4 |
| 최고 화질 | 1920×1080 + 소리 mp4 |
| 소리만 | AAC m4a |
| 단일 mp4 | 그대로 저장, 한글 제목 유지 |

`--newline -N 4` 추가 후 다시 실행해 동일 결과 확인. 출력 로그·실제 오류 문구를 `tests/fixtures/`에 저장해 단위 테스트에서 사용.

**한계:** 데스크톱 yt-dlp라 앱 안의 Python/QuickJS 실행, 실제 유튜브 접속(봇 확인·JS 확인)은 확인하지 못함.

## 테스트 목록

| 클래스 | 수 | 내용 |
|---|---|---|
| UrlExtractorTest | 4 | 공유 문구 링크 추출, 중복·괄호·문장부호, 스킴 거절, 정규화 |
| DownloadCommandTest | 5 | 화질별 옵션(실측과 같은 목록), 알 수 없는 설정 기본값 |
| OutputFilesTest | 4 | 실제 출력 줄에서 제목, 조각·임시 파일 판별, 저장 대상 선택, 표시 이름 |
| ErrorClassifierTest | 4 | 실제 오류 fixture, 사이트별 흔한 문구, 업데이트 권유, 요약 |
| QueueProcessorTest | 10 | 순차 처리·저장·정리, 중복, 실패 후 계속, 업데이트 후 1회 재시도, 재시도 1회 제한, 취소, 강제 종료 후 재대기, 파일 없음, 저장 실패 되돌리기, 여러 파일 |
| AutoEngineUpdaterTest | 3 | 하루 1회, 실패 직후 1시간, 최신·꺼짐·오류 |
| ShareReceiverTest | 2 | 공유 → 대기열 + WorkManager 예약 + 화면 닫힘, 링크 없음 안내 |

## 미검증 (기기 필요)
- 실제 유튜브·쇼츠·인스타 등 다운로드, 1080p 합치기(기기 FFmpeg), 엔진 초기화 시간
- MediaStore 저장 후 갤러리 표시, "열기"
- 포그라운드 알림·백그라운드 지속, Android 15 dataSync 6시간 제한
- 엔진 GitHub 업데이트 실제 동작
- 폴드 접기·펼치기 화면
