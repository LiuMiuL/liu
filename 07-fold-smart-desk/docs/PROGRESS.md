# PROGRESS

## 현재 단계

**0단계 완료, 1단계 구현 완료(기기 미검증).** 2단계(배치 편집·테마·검색·최근)와 3·4단계 일부(타이머 로직·기록, 로컬 일정, 백업)도 선구현.
가장 큰 빈칸: 앱 밖 타이머 종료 알림(HOME-09), 드래그 이동, 월 달력, 힌지 대응, 실기기 검수.

## 2026-10-29 작업 기록

- 환경 확인: 저장소에 기존 프로젝트·미커밋 변경 없음(README만). JDK 21·Gradle 8.14.3 있음, Android SDK 없음 → cmdline-tools로 Platform 36·Build-Tools 36.0.0 설치(`/opt/android-sdk`, 컨테이너 안이라 새 세션에선 다시 설치 필요). KVM 없음 → 에뮬레이터 불가.
- 실패와 원인:
  1. 의존성 다운로드 실패 — Maven Central 429(요청 과다). → GCS 공식 미러를 저장소 앞에 추가.
  2. 자동 저장 테스트 실패 — Room이 자체 executor에서 돌아 테스트 디스패처만으론 완료를 못 기다림. → 대기 헬퍼.
  3. 닫힌 DB로 저장 실패를 흉내 내면 '저장 중'에서 멈춤. → `NoteWriter` 인터페이스로 SQLiteFullException을 던지는 가짜 저장소 사용.
  4. Lint NewApi 3건(API 33 전용 호출) → 수정.
  5. 첫 실행 '카드 없음' 오표시(경쟁 상태) → 기본 카드 생성 후 구독.
  6. Robolectric `captureToImage` 타임아웃 → 뷰를 직접 Canvas에 그려 캡처.

## 주요 파일

- `android/app/src/main/java/com/miu/fold/dashboard/`
  - `dashboard/` CardRegistry(카드 종류·GridPolicy), LayoutEditor(순수 배치 로직), DashboardRepository, HomeViewModel, HomeScreen, Cards
  - `timer/` TimerEngine(순수 상태 전이), TimerController(단일 세션·기록), TimerViewModel, TimerScreen
  - `notes/` NoteRules, NoteDraft(자동 저장), NoteRepository, NotesViewModel, NotesScreen
  - `todos/`, `calendar/`, `shortcuts/`(UrlValidator·ShortcutLauncher), `search/`, `recent/`, `settings/`, `backup/`(BackupCodec·BackupService)
  - `data/` 엔터티·DAO·AppDatabase, `core/` AppClock·FakeClock
  - `MainActivity`(공유 수신), `ui/AppRoot`(하단 메뉴/레일, 수명 주기 저장)
- `android/app/src/test/` 테스트 7개 클래스 48개
- `docs/` 이 문서들, `tests/fixtures/` 수동 확인용 백업 예시

## 다음에 바로 실행할 명령

```bash
# 새 컨테이너라면 SDK부터
mkdir -p /opt/android-sdk && cd /opt/android-sdk
curl -sSO https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q commandlinetools-linux-13114758_latest.zip -d tmp && mkdir -p cmdline-tools && mv tmp/cmdline-tools cmdline-tools/latest
yes | cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null
cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"

cd <repo>/07-fold-smart-desk/android
echo "sdk.dir=/opt/android-sdk" > local.properties
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

그다음 TASKS.md '바로 다음 작업' 1번(HOME-09 종료 알림)부터.
