# DATA_MODEL — Room `fold_smart_desk.db` (schemaVersion 1)

스키마 JSON: `android/app/schemas/com.miu.fold.dashboard.data.AppDatabase/1.json` (exportSchema=true).
시각은 UTC epoch ms 저장, 표시만 기기 시간대. ID는 UUID 문자열.

| 테이블 | 주요 필드 | 무결성·규칙 |
|---|---|---|
| `dashboard_card` | id, type(typeId), title, configJson, enabled | 종류와 인스턴스 분리. 알 수 없는 type은 '지원하지 않는 카드'로 표시(크래시 없음). 첫 버전은 종류당 1개 |
| `layout_placement` | **(cardId, profile)** PK, orderKey, span, visible | profile ∈ `narrow`/`wide`. FK cardId → card (CASCADE). 프로필 저장은 트랜잭션으로 해당 프로필만 교체 |
| `note` | id, title, body, pinned, tags, deletedAt, createdAt, updatedAt, revision | deletedAt≠null = 휴지통. 저장마다 revision+1. 본문 최대 200,000자 |
| `todo` | id, title, description, **dueDate(yyyy-MM-dd)**, dueInstant, priority 0~2, doneAt, orderKey, sourceNoteId | 날짜만 마감은 dueDate로만 비교(UTC 자정 치환 없음). sourceNoteId는 FK 없음(메모 영구 삭제 시 참조만 끊김) |
| `local_event` | id, title, allDay, localDateStart, localDateEnd, startInstant, endInstant, zoneId, memo | 종일=LocalDate 범위, 시간 지정=Instant 범위 + 입력 당시 zoneId |
| `shortcut` | id, type(WEB/APP/INTERNAL), label, target, iconRef, fallbackUrl, orderKey, updatedAt | 저장 전 검증: WEB=https/http만·토큰 파라미터·계정정보 거절, APP=패키지명 형식, INTERNAL=고정 목록 |
| `timer_session` | id, label, linkedTodoId, plannedDurationMs, remainingMs, state, monotonicAnchor, wallClockDeadline, bootMarker, startedAt, focusElapsedMs, updatedAt | 활성(RUNNING/PAUSED/INTERRUPTED) 최대 1개 — Mutex로 직렬화, 복원 시 여분은 취소 처리 |
| `focus_record` | **sessionId PK**, startedAt, endedAt, elapsedMs, outcome(COMPLETED/CANCELLED) | 세션당 1건. 취소는 실제 집중 시간만 |
| `recent_item` | **(type, localId)** PK, openedAt | 최대 100건 유지. 대상이 사라지면 '삭제된 항목'+제거 버튼 |

명세 4.1 대비 변경: `LayoutPlacement.minSize` → 카드 종류 레지스트리(`CardType.supportedSpans`)로 이동, `visible` 추가. `ImportedSnapshot`, `Workspace`는 미구현(후속).

설정(테마, 최근 숨김, 타이머 분)은 DataStore `settings`.

## 마이그레이션

- 현재 version 1. 스키마를 바꾸면 version을 올리고 `Migration`을 추가하며 `fallbackToDestructiveMigration`은 쓰지 않는다.
- 초기 배치(DefaultLayouts)가 새 버전에서 바뀌어도 `ensureDefaults()`는 기존 사용자 배치를 덮어쓰지 않고 새 카드만 끝에 붙인다(테스트 `ensureDefaultsDoesNotOverwriteUserLayout`).

## 백업 JSON

```json
{ "header": { "appId": "com.miu.fold.dashboard", "schemaVersion": 1, "appVersion": "0.1.0",
              "exportedAt": 0, "recordCounts": { "notes": 1, ... } },
  "cards": [], "placements": [], "notes": [], "todos": [], "events": [], "shortcuts": [], "focusRecords": [], "settings": {} }
```

복원 검사: 10MB 상한 → JSON 형식 → appId 일치 → schemaVersion ≤ 1 → recordCounts 일치 → ID 중복 → 배치가 존재 카드 참조 → 날짜 형식 → 바로가기 목적지 재검증(위험한 것은 제외+경고) → 끊긴 sourceNoteId는 null+경고. 미리보기·모드 선택 후 하나의 트랜잭션으로 저장.
예시 파일: `tests/fixtures/backup-unknown-card.json`.
