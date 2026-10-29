# PROGRESS

## 현재 단계

**0.2.0 — 0~4단계 기능 대부분 구현, 실기기 미검증.** 남은 큰 빈칸: 실기기 검수(스크립트 준비됨), 힌지 대응, 배포용 서명키.

## 2026-10-29(2차) 개선 기록 — 0.2.0

- 고친 버그: 같은 메모 두 화면 동시 편집 시 덮어쓰기 → 조건부 저장+충돌 사본 / 필터 화면에서 할 일 이동이 숨은 항목과 바뀜 / 홈 키보드 가림 / 다른 탭에 있을 때 타이머 완료 미감지·완료 시각 부정확
- 추가: 타이머 종료 알림(AlarmManager), 드래그 이동, 같은 줄 카드 높이 맞춤, 앱 아이콘, 월 달력, 백업 파일명 날짜, 메모→할 일 중복 방지, '오늘' 기본값 자정 갱신
- 빌드: release R8 축소(30MB → 3.2MB), versionCode 2 / 0.2.0, 프로젝트 포함 개발용 디버그 키(어디서 빌드해도 같은 서명)
- 실기기: 테스트 전용 앱(`.devtest`)으로 도는 androidTest 스모크 5개 + Windows 스크립트 `tools/device-test.ps1`
- 실패와 원인: 드래그 손잡이가 '열기' 영역 안에 있어 접근성 노드가 합쳐짐(터치·TalkBack 모두 손잡이를 못 찾음) → 영역 분리 / 드래그 중 즉시 재배치하면 제스처가 다른 카드로 넘어감 → 놓을 때 한 번만 이동 / boundsInRoot가 스크롤 밖을 잘라 냄 → positionInRoot+size
- 이 클라우드 환경에서 못 한 것: 사용자 PC(D:\Miu\Projects\…)로 직접 복사·실기기 실행(Windows 폴더는 클라우드 세션에 연결 불가), PowerShell 문법 검사(pwsh 다운로드가 권한상 거부됨 → 수동 검토만)

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

Windows PC에서는 `tools/device-test.ps1`이 위 과정을 대신한다. 그다음 TASKS.md '바로 다음 작업'.
