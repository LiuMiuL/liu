# DECISIONS — 기술 선택·가정·변경 기록

## 버전(2026-10-29 작업 환경에서 실제로 받아 빌드 성공한 조합)

| 항목 | 값 | 이유 |
|---|---|---|
| Gradle | 8.14.3 (wrapper) | 환경에 설치된 버전, AGP 8.11 요구(8.13+) 충족 |
| AGP | 8.11.1 | compileSdk 36 지원 안정판 |
| Kotlin / Compose 컴파일러 플러그인 / Serialization 플러그인 | 2.2.0 | KSP 2.2.0-2.0.2와 짝 |
| Compose BOM | 2025.06.01 | Material3 1.3.x 안정 |
| Room / DataStore | 2.7.2 / 1.1.7 | 안정판 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 30 | minSdk는 명세 제안값 API 30 |
| JDK | 21 툴체인, 바이트코드 17 | 환경 JDK 21 |

모든 버전은 `gradle/libs.versions.toml`에 고정. `+`/`latest` 없음. preview/alpha 없음.
Lint가 더 새 버전(AGP 9.5, Kotlin 2.4, BOM 2026.09 등)이 있다고 경고하지만, 이번 단계는 검증된 조합 유지가 우선이라 올리지 않았다. 올릴 때는 한 번에 하나씩 올리고 전체 테스트를 다시 돌린다.

## 구조

- 단일 `:app` 모듈, 책임 단위 패키지: `dashboard`, `notes`, `todos`, `calendar`, `timer`, `shortcuts`, `search`, `recent`, `settings`, `backup`, `data`, `core`, `ui`. (명세 5.2의 폴더를 Kotlin 패키지로 대응)
- DI 프레임워크 없이 `AppContainer` 수동 구성(작은 앱에 불필요한 계층을 늘리지 않음).
- 네비게이션 라이브러리 없이 `rememberSaveable` 탭/오버레이 상태. 화면 수가 적고 딥링크가 없어서. 화면이 늘면 Navigation Compose로 교체 검토.
- ViewModel은 단일 Activity 범위에서 공유.

## 레이아웃 정책

- 열 수는 **창 가용 너비**(탐색 레일 제외)로 계산: 창 600dp 미만 1열, 이상이면 카드 최소 260dp와 여백 16dp를 만족할 때만 2열, 창 1200dp 이상이면 3열까지.
  - 초기 설계값 280dp → **260dp로 변경**: 폴드 내부 화면(약 680dp 창 − 레일 80dp = 600dp)에서 280dp면 2열이 겨우 안 나와 1열이 되어 '펼치면 함께 보기' 목표를 못 채움. 260dp에서도 카드 내용(입력칸+버튼)이 잘리지 않음을 미리보기로 확인. 실기기 확인 필요.
- 프로필은 2개: 1열이면 `좁은 화면`, 2열 이상이면 `넓은 화면`. 넓은 화면 배치의 3열 크기는 2열 창에서 2열로 줄여 표시하되 저장값은 유지.
- **숨김은 프로필별**(`LayoutPlacement.visible`). 명세 4.1의 `DashboardCard.enabled`는 카드 자체 사용 여부로 남겼다(한 프로필에서 숨긴 카드가 다른 프로필에서 사라지지 않도록).
- 편집 중 접기/펼치기: 편집 프로필을 시작 시점에 고정. 창이 바뀌면 안내 문구를 띄우고, 저장은 원래 프로필에만, 취소하면 DB는 손대지 않은 상태.
- 드래그는 이번 단계에서 미구현. 명세가 요구하는 버튼 이동(앞/뒤/위/아래)만 제공.
- 노트 화면 2패널: 가용 너비 ≥ 240 + 320 + 48dp일 때만.

## 타이머

- 남은 시간 = `remainingMs − (elapsedRealtime − monotonicAnchor)` (같은 부팅일 때만). wall clock은 표시용.
- 부팅 구분: `Settings.Global.BOOT_COUNT`, 못 읽으면 대략 부팅 시각(분 단위)으로 대체.
- 앱이 백그라운드로 갈 때(ON_STOP) 기준값 체크포인트 → 재부팅 시 잃는 시간을 마지막 체크포인트 이후로 한정. 재부팅 후에는 완료로 처리하지 않고 INTERRUPTED.
- 완료 기록은 `focus_record.sessionId` 기본키 + INSERT IGNORE로 중복 제거.
- 화면 갱신은 화면 구독 중에만 250ms 틱(WhileSubscribed). 백그라운드 폴링·포그라운드 서비스 없음.
- 알림은 미구현(HOME-09). WorkManager는 쓰지 않을 것(명세 A7).

## 데이터·저장

- 메모 자동 저장 디바운스 500ms(초기값). 실패 시 초안을 메모리에 유지하고 '다시 저장' 제공. 프로세스가 죽기 전 ON_STOP에서 즉시 저장 시도. **프로세스가 저장 전에 강제 종료되면 마지막 500ms 이내 입력은 잃을 수 있음**(초안을 Bundle에 넣지 않는다는 명세 원칙을 따름).
- SavedState에는 ID·필터·검색어 같은 작은 값만.
- 첫 실행 기본 카드 생성이 끝난 뒤에만 배치를 구독(테스트 중 발견한 '카드 없음' 오표시 버그 수정).
- 백업에서 진행 중 타이머 세션 제외(monotonic 값은 다른 부팅·기기에서 의미 없음). 집중 기록은 포함.
- 백업 '새 데이터로 추가'는 카드/배치를 가져오지 않고, 현재 없는 카드 종류만 추가(기존 홈 배치 보존). '전체 교체'는 앱 내부 `files/backups/pre-restore-*.json`을 먼저 만든다.

## 빌드 환경 메모

- Maven Central 원본이 429를 돌려줘서 `settings.gradle.kts`에 공식 GCS 미러를 앞에 추가했다. Robolectric 런타임 jar도 같은 미러에서 받는다(`app/build.gradle.kts`의 systemProperty).
- 에뮬레이터를 쓸 수 없는 환경(KVM 없음)이라 UI는 Robolectric + Compose UI Test로 JVM에서 검증했다.
