# PROGRESS — Fold Deal Board

## 현재 단계

- **0단계(모델·출처·가격 의미·환경)**: 완료. 계산·스키마 테스트 기반 마련.
- **1단계(수동 저장·공유 링크·상세·2~4개 비교)**: 핵심 흐름 구현, JVM/Robolectric 검증 통과. 스크린샷 첨부(MKT-02b)와 실기기 검증 남음.
- 2단계 일부(관측 이력·표본 집계)를 1단계 비교 화면에 필요해서 함께 구현했다. 보드 엔터티·필터는 미구현.

마지막 갱신: 2026-10-30 (KST)

## 이번 작업에서 만든 것

- Gradle 프로젝트 `06-fold-deal-board/android` (단일 `:app` 모듈, 버전 카탈로그 + `app/gradle.lockfile` 의존성 잠금)
- 도메인: `listings/` (Listing, SpecNormalizer, PublishedDateParser, ListingDraft), `observations/` (PriceObservation, PriceParser), `comparison/` (ComparisonCalculator, CompareTable, Comparison)
- 데이터: `data/` (DealDao, DealDatabase v1 + 스키마 JSON `app/schemas/`, ListingRepository)
- UI: `ui/` (DealRoot 적응형 2패널, BoardScreen, EditorScreen, DetailScreen, CompareScreen, AppViewModel)
- 테스트 34개: `app/src/test/...` + 고정 데이터 `tests/fixtures/aggregate_example.csv`
- 문서: README, docs/*

## 실제 실행 결과

| 명령 | 결과 |
|---|---|
| `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, 34 tests, 0 failures |
| `./gradlew lintDebug` | 0 errors, 31 warnings (새 버전 안내 30, DataExtractionRules 1 — DECISIONS D-10) |
| `./gradlew assembleDebug` | `android/app/build/outputs/apk/debug/app-debug.apk` 생성 (25,184,079 bytes, SHA-256 `c81183fcc0eba600443af247348f46ff9ead2aa75d5f367f3bd52412a7ead209`) |

APK는 빌드 산출물이라 Git에 넣지 않는다. 위 명령으로 다시 만든다.

## 진행 중 겪은 문제와 해결

1. 컨테이너에 Android SDK 없음 → commandline-tools로 `platforms;android-35`, `build-tools;35.0.0`, `platform-tools` 설치(`/opt/android-sdk`, 컨테이너 한정).
2. Maven Central `429 Too Many Requests` 반복 → 재시도. Robolectric 런타임 jar(`android-all-instrumented 14-robolectric-10818077-i7`)는 `~/.m2`에 직접 받아 둠.
3. 한글 백틱 테스트 이름이 이 환경 파일 인코딩에서 클래스 파일 경로 오류 → ASCII 이름 + 한글 주석으로 변경.
4. UI 테스트에서 발견: 하단 스낵바가 "매물 추가" 버튼을 가림 → 스낵바를 머리줄 아래(위쪽)로 이동. 위쪽 0dp 배치는 뒤로 버튼을 가려 64dp 아래로 조정.
5. Robolectric에서 ExtendedFAB 텍스트가 의미 트리에 나타나지 않음 → 테스트는 `testTag("add-listing")`로 찾음.

## 미구현·미검증·외부 설정 필요

- 미구현(필수): 스크린샷 첨부, Board(예산·조건), 사양/날짜 필터, 조건 평가·알림, 체크리스트, 내보내기·백업, 테마 선택 → TASKS.md
- 미검증: 실기기·에뮬레이터 설치/실행, 실제 접기·펼치기·회전·분할 화면, 프로세스 종료 복원, 200% 글꼴, 다른 앱에서의 실제 공유
- 외부 설정 필요(확장): 허용된 플랫폼 API/피드(MKT-X1), OCR·AI 서비스(MKT-X2)

## 다음 단계와 바로 실행할 작업

1. 실기기(폴드) 또는 에뮬레이터에 설치해 1단계 흐름(매물 5개 입력 → 비교 → 재실행) 확인 후 TEST_REPORT 갱신.
2. MKT-02b 스크린샷 첨부: `ActivityResultContracts.PickVisualMedia` → 앱 내부 파일 복사 + Attachment 엔터티(DB v2 마이그레이션 작성).
3. MKT-04: Board 엔터티와 모델/RAM/용량·등록일·확인일 필터.
4. MKT-08/09: WatchRule·WatchEvaluation과 3값 평가, 중복 알림 방지 원장.

```bash
cd 06-fold-deal-board/android
./gradlew testDebugUnitTest lintDebug assembleDebug
```
