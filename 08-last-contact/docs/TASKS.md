# TASKS — 연락 기록

상태: `TODO → IN_PROGRESS → IMPLEMENTED_UNVERIFIED → DONE`(실기기 확인까지), `BLOCKED_EXTERNAL`, `DEFERRED`.
실기기·에뮬레이터가 없는 환경이라 DONE 항목은 없다. IMPLEMENTED_UNVERIFIED = 코드 + PC 테스트 통과.

| ID | 작업 | 상태 | 남은 것 | 검수 |
|---|---|---|---|---|
| LC-01 | 사람 추가·편집·삭제(연락처 선택기, 별칭, 번호, 그룹, 목표 주기) | IMPLEMENTED_UNVERIFIED | 앱 안 대화상자 입력은 실기기 테스트로 확인(PC에서 360dp 흉내 시 테스트 환경 한계) | LC-T01 |
| LC-02 | 직접 기록·되돌리기·날짜 골라 기록(미래 거절) | IMPLEMENTED_UNVERIFIED | — | LC-T01~03 |
| LC-03 | 목록 정렬·그룹·검색·경과/초과 표시 | IMPLEMENTED_UNVERIFIED | — | LC-T11 |
| LC-04 | 카톡 내보내기 가져오기(형식 4종, ZIP, CP949, 1:1/단체, 공유 받기) | IMPLEMENTED_UNVERIFIED | **실제 최신 카톡 내보내기 파일로 확인 필요**(형식이 카톡 버전마다 다름) | LC-T04~06 |
| LC-05 | 카톡 알림 자동 기록 + 미확인 이름 연결·무시 | IMPLEMENTED_UNVERIFIED | 실제 카톡 알림 구조(1:1·단체·묶음 알림) 실기기 확인 | LC-T07, T08 |
| LC-06 | 통화·문자 가져오기(+82 정규화, 1초 이상 통화) | IMPLEMENTED_UNVERIFIED | 기기별 통화 기록 형식 확인 | LC-T09, T10 |
| LC-07 | 매일 알림(WorkManager) | IMPLEMENTED_UNVERIFIED | 실제 알림 도착·지연 확인 | — |
| LC-08 | 백업·복원(추가/교체) | IMPLEMENTED_UNVERIFIED | 파일 선택 실기기 확인 | LC-T12 |
| LC-09 | 폴드·넓은 화면 2패널, 접근성 | IMPLEMENTED_UNVERIFIED | 글자 200%, TalkBack | — |
| LC-10 | 실기기 검수 | BLOCKED_EXTERNAL | `tools/device-test.ps1` + DEVICE_TEST.md 체크리스트 실행 | 전체 |
| LC-X1 | 홈 화면 위젯(가장 급한 사람 3명) | DEFERRED | — | — |
| LC-X2 | 생일·기념일 알림 | DEFERRED | — | — |
| LC-X3 | 연락 통계(월별 횟수 그래프) | DEFERRED | — | — |

## 바로 다음
1. 실기기에서 `08-last-contact\tools\device-test.bat` 실행 → 결과 보고서로 실패 수정.
2. 실제 카톡 '대화 내보내기' 파일 1:1·단체 하나씩 가져와 날짜가 맞는지 확인(형식이 다르면 파일 앞 10줄을 이름 가린 채 알려주면 해석기 보강).
3. 카톡 알림 자동 기록을 하루 켜 두고 미확인 이름 목록 확인.
