# TASKS — Fold Smart Desk

상태: `TODO → IN_PROGRESS → IMPLEMENTED_UNVERIFIED → DONE`, 외부 조건으로 막힘 `BLOCKED_EXTERNAL`, 범위상 미룸 `DEFERRED`.
**DONE은 실기기(또는 에뮬레이터) 검증까지 끝난 것만** 쓴다. 이번 작업 환경에는 에뮬레이터(KVM)·실기기가 없어 DONE 항목이 없다.
`IMPLEMENTED_UNVERIFIED` = 코드 + JVM/Robolectric 테스트 통과, 기기 미검증.

구분: **[필수]** 명세 필수 범위 · **[확장]** 확장 기능 · **[승인]** 별도 승인·외부 권한 필요

| ID | 작업 | 구분 | 상태 | 남은 것 / 선행조건 | 검수 연결 |
|---|---|---|---|---|---|
| HOME-01 | 카드 홈·레지스트리 | 필수 | IMPLEMENTED_UNVERIFIED | 실기기 확인 | T01, T17 (UI 테스트 통과) |
| HOME-02 | 메모·초안·저장 | 필수 | IMPLEMENTED_UNVERIFIED | 회전 중 입력 유지 실기기 확인, 실제 저장 공간 부족 재현 | T02, T03 |
| HOME-03 | 할 일·완료·마감 | 필수 | IMPLEMENTED_UNVERIFIED | 정확 시각(dueInstant) 입력 UI 없음(필드만 존재), 반복은 후속 | T16, T19 |
| HOME-04 | 앱/웹 바로가기 | 필수 | IMPLEMENTED_UNVERIFIED | 앱 아이콘 표시 미구현(iconRef 미사용), 앱 삭제 후 실행 실기기 확인 | T14, T15 |
| HOME-05 | 좁은/넓은 배치 | 필수 | IMPLEMENTED_UNVERIFIED | 힌지 회피(FoldingFeature) 미적용 | T05 |
| HOME-06 | 편집·취소·초기화 | 필수 | IMPLEMENTED_UNVERIFIED | **드래그 이동 미구현**(버튼 이동만), 실기기 접기/펼치기 확인 | T04, T06, T07 |
| HOME-07 | 검색·최근 | 필수 | IMPLEMENTED_UNVERIFIED | 최근 '할 일/일정' 항목은 해당 탭으로만 이동(개별 항목 강조 없음) | 단위 테스트 |
| HOME-08 | 타이머·Clock | 필수 | IMPLEMENTED_UNVERIFIED | 실기기 백그라운드·재부팅 확인 | T08~T12 |
| HOME-09 | 종료 알림·완료 이력 | 필수 | IN_PROGRESS | 이력·중복 제거 완료. **앱 밖 종료 알림 미구현**(AlarmManager·POST_NOTIFICATIONS 권한 흐름 필요) | T11, T13 |
| HOME-10 | 로컬 일정 | 필수 | IMPLEMENTED_UNVERIFIED | **월 달력 보기 미구현**(일 단위+30일 목록만), 시각 입력은 HH:mm 텍스트 | T16 |
| HOME-11 | 공유·내보내기 | 필수 | IN_PROGRESS | 텍스트 공유 수신/발신 완료. 공유받은 '카드 JSON'·ImportedSnapshot 미구현 | T15, T18 |
| HOME-12 | 백업·복원·테마 | 필수 | IMPLEMENTED_UNVERIFIED | SAF 파일 선택 실기기 확인, 첨부 ZIP 없음(첨부 기능 자체가 없음) | T17, T20 |
| HOME-13 | 폴드·접근성·성능 | 필수 | BLOCKED_EXTERNAL | 실제 폴드 기기·에뮬레이터 필요. 글자 200%, TalkBack, 분할 화면, 대량 데이터 성능 | 공통 기기 검수 |
| HOME-X1 | 홈 위젯 | 확장 | DEFERRED | 후속 별도 구현 | — |
| HOME-X2 | 외부 연동 카드 | 확장 | DEFERRED | 상대 앱 공유 계약 확보 후 | — |
| HOME-X3 | 워크스페이스 | 확장 | DEFERRED | — | — |
| HOME-X4 | 반복 할 일 | 확장 | DEFERRED | 반복 규칙·시간대·완료 회차 분리 설계 | — |
| HOME-A1 | 시스템 캘린더 연결 | 승인 | DEFERRED | READ_CALENDAR 권한·계정 범위 사용자 승인 필요 | — |
| HOME-A2 | 정확 알람 권한 | 승인 | TODO | HOME-09와 함께. 권한 거절 시 지연 안내 | T13 |

## 바로 다음 작업(우선순위)

1. HOME-09: 타이머 종료 알림 — AlarmManager(정확 알람 권한 확인 + 불가 시 `setAndAllowWhileIdle` 지연 안내), 알림 채널, POST_NOTIFICATIONS 런타임 요청, 재부팅/시간대 변경 후 재예약 정책.
2. HOME-06: 드래그로 카드 순서 변경(버튼 이동과 같은 `LayoutEditor.moveTo` 사용).
3. HOME-05/13: `androidx.window` FoldingFeature로 힌지 영역에 필수 버튼이 걸리지 않게.
4. HOME-10: 월 달력 보기.
5. 실기기 검수(HOME-13) — TEST_REPORT의 미검증 목록을 순서대로.
