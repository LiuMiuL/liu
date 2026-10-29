# TASKS — Fold Deal Board

상태: `TODO → IN_PROGRESS → IMPLEMENTED_UNVERIFIED → DONE`, 외부 조건으로 막힘 `BLOCKED_EXTERNAL`, 범위상 미룸 `DEFERRED`.
`DONE`은 실기기(폴드) 검증까지 끝난 경우에만 쓴다. 현재는 실기기·에뮬레이터가 없어 JVM/Robolectric 검증까지만 했다.

구분: **필수**(1~4단계) / **확장**(후속 A·B) / **외부 인증 필요**(허용된 API·AI·OCR 서비스).

| ID | 작업 | 구분 | 상태 | 선행조건 | 검수 연결 | 비고 |
|---|---|---|---|---|---|---|
| MKT-01 | 독립 앱·DB | 필수 | IMPLEMENTED_UNVERIFIED | — | DatabasePersistenceTest, AppFlow*UiTest | Room v1, 다른 앱 의존 없음. 실기기 설치 미검증 |
| MKT-02 | 링크 공유·직접 입력 | 필수 | IN_PROGRESS | — | ListingDraftTest(공유 텍스트), AppFlow*UiTest | 직접 입력·`ACTION_SEND text/plain` 수신 구현. **스크린샷 첨부 미구현(MKT-02b)** |
| MKT-02b | 스크린샷 첨부(시스템 선택기, 앱 내부 복사) | 필수 | TODO | MKT-01 | — | Attachment 엔터티·해시·참조 무결성 필요 |
| MKT-03 | 모델·RAM·용량·상태 | 필수 | IMPLEMENTED_UNVERIFIED | — | MKT-T01, T02, T03 | 원문 보존, 정규화 키 수정 가능. 판매자 주장/직접 확인 분리는 MKT-10 |
| MKT-04 | 보드·필터·정렬 | 필수 | IN_PROGRESS | — | ListingFilterTest, AppFlow*UiTest | 검색·정렬 3종·판매 중만·제외 숨김, **조건 필터(분류·기종·RAM·저장공간·등록일 N일·가격 확인일 N일, 미확인=확인 필요) 구현(0.2.0)**. 남은 것: Board 엔터티(예산·필수/선호/제외 조건 저장, 여러 보드), 조건을 앱 재시작 후에도 유지 |
| MKT-05 | 2~4개 비교 | 필수 | IMPLEMENTED_UNVERIFIED | MKT-03 | MKT-T02, AppFlow*UiTest | 고정 항목 열, 차이(≠)·미확인 표시, 다른 항목만, 가격/총비용 기준, 카드 대체, 예산 대비 |
| MKT-06 | 가격 관측·협상 | 필수 | IMPLEMENTED_UNVERIFIED | MKT-01 | ListingDraftTest, DatabasePersistenceTest | 관측 추가 전용(ABORT). 협상은 `PriceKind.NEGOTIATION` 관측으로 기록(DECISIONS D-07) |
| MKT-07 | 표본 집계 | 필수 | IMPLEMENTED_UNVERIFIED | MKT-06 | MKT-T05, T06, T07, T11, T13 | 매물당 최신 게시가 1개, 총비용 기준. 최신성 기준은 로직만 있고 UI 설정 없음 |
| MKT-08 | 조건 평가 MATCH/NO_MATCH/UNKNOWN | 필수 | TODO | MKT-04, MKT-07 | MKT-T09, T10, T11 | WatchRule·WatchEvaluation 엔터티 필요 |
| MKT-09 | 알림·중복 방지 | 필수 | TODO | MKT-08 | MKT-T09, T10, T14 | POST_NOTIFICATIONS 런타임 권한, 앱 내 알림 기록 |
| MKT-10 | 구매 체크리스트 | 필수 | TODO | MKT-03 | — | ConditionRecord(sellerClaim/userCheck), 질문 복사 |
| MKT-11 | MD/CSV/JSON 내보내기 | 필수 | TODO | MKT-05 | MKT-T16 | CSV 수식 주입 방지 |
| MKT-12 | 복원·삭제·권한 | 필수 | IN_PROGRESS | MKT-11 | — | 소프트 삭제·복원 구현. JSON 백업/복원(트랜잭션, 새 데이터로 가져오기) 미구현 |
| MKT-13 | 폴드·성능·실기기 | 필수 | TODO | 실기기 | 공통 기기·환경 검수 | Robolectric 360dp/900dp만 확인. 힌지 회피(WindowManager)·200% 글꼴 실측 미실시 |
| MKT-14 | 테마 선택(시스템/밝게/어둡게) | 필수 | TODO | — | — | 현재 시스템 테마만 따름 |
| MKT-X1 | 허용 플랫폼 자동 어댑터 | 확장·외부 | DEFERRED | 공식 API/피드·이용 권한 확인 | MKT-T12, T15 | 허용 경로 확인 전 착수하지 않음. 현재 "자동 추적" 문구 없음 |
| MKT-X2 | OCR·AI 필드 제안 | 확장·외부 | DEFERRED | 사용자 동의·비용·외부 서비스 | — | 원문 근거·수동 확인 필수 |

## 검수 시나리오 연결 현황

| 시나리오 | 상태 | 근거 |
|---|---|---|
| MKT-T01 세대·파생 모델 분리 | 통과(JVM) | SpecNormalizerTest.mkt_T01_generationsAndVariantsNotMerged |
| MKT-T02 RAM 차이 표시 | 통과(JVM·Robolectric UI) | CompareTableTest.mkt_T02_ramDifferenceIsMarked, AppFlow*UiTest |
| MKT-T03 RAM16×2 분리 | 통과(JVM) | SpecNormalizerTest.mkt_T03_ramModulesSplit |
| MKT-T04 미확인·협의·0원 | 통과(JVM) | PriceParserTest.mkt_T04_unknownNegotiableZeroAreDistinct |
| MKT-T05 배송비 미상 → 총비용 미확인 | 통과(JVM) | ComparisonCalculatorTest.mkt_T05_unknownShippingMeansUnknownTotal |
| MKT-T06 605000/620000/625000 | 통과(JVM) | ComparisonCalculatorTest.mkt_T06_specExampleAggregate (fixture: tests/fixtures/aggregate_example.csv) |
| MKT-T07 이력 10개 → 표본 1 | 통과(JVM) | ComparisonCalculatorTest.mkt_T07_tenObservationsCountAsOneSample |
| MKT-T08 등록일 미상 분리 | 통과(JVM·Robolectric UI) | ListingFilterTest.mkt_T08_publishedWithinWeekSeparatesUnknown, AppFlow*UiTest(일주일 조건 → 확인 필요 2개) |
| MKT-T09·T10 알림 1회·중복 없음 | 미구현 | MKT-08/09 |
| MKT-T11 필수 RAM 미확인 → 확인 필요 | 집계 부분 통과 | ComparisonCalculatorTest.mkt_T11_…; 조건 평가는 MKT-08 |
| MKT-T12 가격 갱신 실패 | N/A | 자동 갱신 없음(MKT-X1) |
| MKT-T13 판매 완료 기본 제외 | 통과(JVM) | ComparisonCalculatorTest.mkt_T13_soldExcludedByDefault |
| MKT-T14 알림 권한 거절 | 미구현 | MKT-09 |
| MKT-T15 자동 어댑터 없음 → 자동 추적 문구 없음 | 통과(Robolectric UI) | 보드 상단 "수동 기록 모드 · 자동 가격 추적 없음" 확인 |
| MKT-T16 CSV 수식 방지 | 미구현 | MKT-11 |
| MKT-T17 접기·펼치기·재실행 유지 | 부분 | 액티비티 재생성 후 비교 선택 유지(Robolectric). 실제 접기·프로세스 종료 미검증 |
| MKT-T18 비교 후 원본 가격 변경 | 부분 | 비교 스냅샷이 당시 관측 ID 유지(DatabasePersistenceTest), "새 관측값 있음" 표시 구현. UI 테스트 없음 |
