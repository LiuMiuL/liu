# DATA_MODEL — Fold Deal Board

DB: Room `fold_deal_board.db`, **버전 1**. 스키마 JSON: `android/app/schemas/com.miu.fold.deals.data.DealDatabase/1.json`.
시각은 모두 UTC epoch ms로 저장하고 기기 시간대로 표시한다. 금액은 원화 정수(Long). `null` = 미확인.

## listings

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | TEXT PK | UUID |
| platform, originalUrl, platformListingId, sellerTitle | TEXT? | 원본 정보. URL은 http/https만 저장 |
| category | TEXT | PHONE / PC_PART / LAPTOP / TABLET / OTHER |
| brand, modelRaw, modelCanonical, variant | TEXT? | 원문과 정규화 키 분리 |
| ramRaw, ramGb, ramModuleGb, ramModuleCount | TEXT?/INT? | 총 RAM과 모듈 구성 분리(16×2 → 32/16/2) |
| storageRaw, storageGb | TEXT?/INT? | 저장공간(GB, 1TB=1024) |
| color, region | TEXT? | |
| publishedRaw, publishedValue, publishedPrecision | TEXT?/TEXT?/TEXT | 등록일 원문·값·정밀도(UNKNOWN/YEAR_MONTH/DAY) |
| tradeStatus | TEXT | ON_SALE / RESERVED / SOLD / UNVERIFIABLE |
| userRating | TEXT | NONE / INTERESTED / ON_HOLD / EXCLUDED / PURCHASED |
| innerScreen, outerScreen, hinge, repairHistory, battery, conditionNote | TEXT? | 판매자 설명 기준 상태(직접 확인 분리는 MKT-10) |
| description, memo | TEXT? | |
| specSource | TEXT | MANUAL / FROM_DESCRIPTION / ALLOWED_INTEGRATION / AI_SUGGESTION |
| savedAt, updatedAt | INT | 앱 저장 시각(등록일과 별개) |
| deletedAt | INT? | 소프트 삭제 |

## price_observations (추가 전용)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | TEXT PK | UUID, 같은 ID 재삽입은 ABORT |
| listingId | TEXT FK → listings.id (CASCADE), 인덱스 | |
| priceState | TEXT | KNOWN / UNKNOWN / NEGOTIABLE |
| amountKrw | INT? | KNOWN일 때만 |
| qualifier | TEXT | NONE / BUNDLE / PER_UNIT / DEPOSIT |
| rawText | TEXT? | 가격 원문 |
| shippingState, shippingKrw | TEXT, INT? | UNKNOWN / INCLUDED / SEPARATE / DIRECT_DEAL |
| priceKind | TEXT | POSTED / NEGOTIATION / CONFIRMED_TRADE |
| currency | TEXT | 현재 "KRW"만 생성. 다른 통화는 집계하지 않음 |
| observedAt | INT | 관측(확인) 시각 |
| sourceKind, note | TEXT, TEXT? | |

총비용 = amountKrw + (SEPARATE면 shippingKrw). 가격이 KNOWN·NONE이 아니거나, 배송 UNKNOWN, 또는 SEPARATE인데 금액 미상이면 **총비용 미확인**.

## comparisons

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | TEXT PK | |
| title | TEXT | |
| listingIds, observationIds | TEXT | 같은 순서의 줄바꿈 구분 목록. 비교 당시 사용한 관측 ID를 고정 |
| budgetKrw | INT? | |
| notes | TEXT? | |
| createdAt | INT | |

## 설정: 보드 조건 (DataStore Preferences, 0.3.0)

파일: `files/datastore/board_conditions.preferences_pb`. DB가 아니라 설정 파일이며 스키마 마이그레이션이 없다.

| 키 | 타입 | 설명 |
|---|---|---|
| saved | Boolean | 한 번이라도 저장했는지(없으면 기본 조건) |
| sort | String | SortOrder 이름. 모르는 값이면 최근 저장순 |
| onSaleOnly, hideExcluded | Boolean | |
| category | String? | Category 이름. 모르는 값이면 조건 없음 |
| models | Set<String> | 정규화 모델 키 |
| ramGb, storageGb | Set<String> | 정수 문자열 |
| publishedWithinDays, checkedWithinDays | Int? | 없으면 조건 없음 |
| includeUnknown | Boolean | 확인 필요 매물 표시 여부 |

검색어·비교 선택·예산은 저장하지 않는다(화면 재생성 동안만 SavedState로 유지).

## 집계 규칙(ComparisonCalculator)

- 대상: 삭제되지 않았고, 조건의 모델·RAM·저장공간과 정확히 같은 매물. 조건 필드가 미확인이면 "확인 필요"로 제외 목록에 표시.
- 판매 완료·확인 불가는 기본 제외. 매물당 **최신 POSTED 관측 1개**만 사용.
- 총비용 미확인·최신성 초과는 제외 사유와 함께 표시. N<3은 "표본 부족".

## 무결성·마이그레이션·백업

- 관측은 FK CASCADE이지만 매물은 소프트 삭제하므로 실제로 관측이 지워지는 경로는 현재 없음.
- 마이그레이션: 아직 v1뿐. 스키마 변경 시 `Migration`을 작성하며 `fallbackToDestructiveMigration`은 쓰지 않는다.
- 백업: OS 백업 제외. 사용자 실행 JSON 백업/복원은 MKT-12(미구현). 헤더 `appId, schemaVersion, appVersion, exportedAt, recordCounts` 예정.
- 명세 4.1 중 미구현 엔터티: Board, ProductIdentity(현재 Listing 컬럼으로 통합), SpecValue(현재 고정 컬럼), ConditionRecord, Attachment, WatchRule, WatchEvaluation, NegotiationNote(관측으로 대체, D-07), Reminder.
