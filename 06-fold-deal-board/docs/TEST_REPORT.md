# TEST_REPORT — Fold Deal Board

## 실행 환경

- 2026-10-29(UTC) 클라우드 Linux 컨테이너, OpenJDK 21.0.12, Gradle 8.14.3(wrapper), Android SDK Platform 35 / Build-Tools 35.0.0
- KVM 없음 → **에뮬레이터·실기기 실행 불가**. UI는 Robolectric(SDK 34 런타임) + Compose UI Test로만 확인.
- 빌드 종류: debug

## 0.2.0 (2026-10-29 UTC, 조건 필터 추가)

```text
./gradlew assembleDebug lintDebug testDebugUnitTest → BUILD SUCCESSFUL
테스트: 42 tests / 0 failures (신규 ListingFilterTest 7개, ListingDraftTest +1, UI 흐름에 조건 필터 단계 추가)
lint: 0 errors, 31 warnings (구성 동일)
aapt2 dump badging → versionCode 2, versionName 0.2.0
app-debug.apk 25,266,003 bytes, SHA-256 482c5866b77de86f9de411d83418a1f8c3b5b94f2f9c8c685faf0c8875036b57
```

추가 확인 내용:
- ListingFilterTest: 일주일 경계(오늘−7일 통과, −8일 제외), 등록일 미상 → 확인 필요(MKT-T08), 연월 정밀도, 휴대폰 기종+저장공간 정확 일치(Fold SE·256GB·PC 부품 제외, 용량 미상 → 확인 필요), 확실한 불일치 우선, 가격 확인일 독립, 선택지 생성, 상대 날짜 해석
- AppFlow*UiTest(360dp·900dp): 조건 열기 → RAM 16GB 선택 → "조건 충족 1개", 12GB 카드 숨김 → 일주일 조건 → "조건 충족 0개 · 확인 필요 2개(숨김)"

## 0.1.0 실행 기록

```text
./gradlew testDebugUnitTest    → BUILD SUCCESSFUL, 34 tests / 0 failures / 0 skipped
./gradlew lintDebug            → 0 errors, 31 warnings (GradleDependency 27, AndroidGradlePluginVersion 3, DataExtractionRules 1)
./gradlew assembleDebug        → app/build/outputs/apk/debug/app-debug.apk
aapt2 dump badging             → package com.miu.fold.deals, versionCode 1, versionName 0.1.0, minSdk 30, targetSdk 35
sha256sum app-debug.apk        → c81183fcc0eba600443af247348f46ff9ead2aa75d5f367f3bd52412a7ead209 (25,184,079 bytes)
```

## 테스트 목록

| 클래스 | 수 | 종류 | 내용 |
|---|---|---|---|
| PriceParserTest | 4 | JVM | 70만·700,000원·700000·70만 5천 정규화, MKT-T04, 일괄/개당/보증금, 잘못된 쉼표 |
| SpecNormalizerTest | 5 | JVM | MKT-T03 RAM 모듈 분리, 저장공간/TB, 같은 모델 표기 통일, MKT-T01 세대·파생 분리, 등록일 정밀도 |
| ComparisonCalculatorTest | 10 | JVM | MKT-T05, T06(명세 4.3 예시, fixture CSV), T07, T11, T13, 예산 잔액 45,000원, 협상가 비대체, 삭제·최신성 제외, 중앙값, 일괄/협의 총비용 |
| CompareTableTest | 3 | JVM | MKT-T02 RAM 차이, 미확인 셀·차이 판정·예산 초과 표시, PC RAM 모듈 표기 |
| ListingDraftTest | 8 | JVM | 미확인 저장, 가격 없이 저장, 원문 보존, 가격 변경 시 관측 추가, 정규화 키 왕복, URL 스킴 거절, 공유 텍스트 분리, MKT-T08(등록일 미상) |
| DatabasePersistenceTest | 2 | Robolectric + Room 파일 DB | 매물 5개 저장·수정·삭제 → DB 닫고 다시 열어 데이터·가격 이력·비교 스냅샷 보존 확인, 관측 ID 중복 거절 |
| AppFlowNarrowUiTest | 1 | Robolectric UI, 360×780dp | 빈 상태 → 매물 2개 입력·저장 → 상세 → 비교 선택 → 비교표(사양 다름·≠ RAM) → 액티비티 재생성 후 비교 유지, "자동 추적 없음" 문구 |
| AppFlowWideUiTest | 1 | Robolectric UI, 900×700dp | 2패널 빈 상세 안내 확인 + 같은 흐름 |
| ListingFilterTest (0.2.0) | 7 | JVM | 조건 필터·상대 등록일 |

## 검증하지 않은 항목 (N/A 또는 미실시)

| 항목 | 상태 | 이유 |
|---|---|---|
| 실기기·폴드 설치/실행 | 미실시 | 기기 없음 |
| 에뮬레이터 | 미실시 | KVM 없음 |
| 실제 접기·펼치기·회전·분할 화면 | 미실시 | Robolectric은 고정 qualifiers와 recreate만 확인 |
| 프로세스 종료 후 SavedState 복원 | 미실시 | 설계상 SavedStateHandle 사용, 실측 없음 |
| 글자 200%·키보드 겹침 | 미실시 | 카드 대체 로직은 fontScale 기반으로 구현했으나 실측 없음 |
| 다른 앱(번개장터 등)에서 실제 공유 | 미실시 | 공유 텍스트 파싱만 JVM 테스트 |
| 오프라인·네트워크 | N/A | 네트워크 기능 없음 |
| 알림·카메라·파일 권한 거절 | N/A | 해당 기능 미구현 |
| DB 마이그레이션 | N/A | 스키마 v1만 존재 |
| 성능(대량 데이터) | 미실시 | 측정값 없음 |

Robolectric 화면 측정값(글자 폭 등)은 실제 렌더링과 다르므로 레이아웃 품질 판단에 쓰지 않는다.
