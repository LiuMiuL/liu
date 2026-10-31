# DATA_MODEL — Room `last_contact.db` v1

스키마: `android/app/schemas/com.miu.lastcontact.data.AppDatabase/1.json`

| 테이블 | 필드 | 규칙 |
|---|---|---|
| person | id, name, aliases(줄바꿈), phones(정규화, 쉼표), groupName, goalDays?, memo, createdAt, updatedAt | 이름 필수, 목표 1~3650일 |
| contact_event | id, personId(FK CASCADE), day(yyyy-MM-dd), atMillis?, channel, direction, note, dedupeKey(유일), createdAt | channel: MANUAL·KAKAO_EXPORT·KAKAO_NOTIFICATION·CALL·SMS / direction: IN·OUT·BOTH·UNKNOWN |
| unmatched_sighting | (nameKey, day, channel) PK, displayName, lastAt, count, roomName? | 알림에서 본 미등록 이름의 날짜만 |
| ignored_name | nameKey PK, displayName | 무시 목록 |

설정(DataStore): 내 카톡 이름, 카톡 기록 켜짐, 통화·문자 자동, 매일 알림·시각, 정렬, 마지막 동기화 시각.

백업 JSON: `header{appId=com.miu.lastcontact, schemaVersion=1, appVersion, exportedAt, recordCounts}` + people·events·unmatched·ignored·settings. 복원 검사: 크기 20MB, 앱 ID, 스키마, 항목 수, 사람 ID 중복, 기록→사람 참조, 날짜·경로 형식. ADD는 새 ID로 옮기고 유일 키 재계산, REPLACE는 내부 사전 백업 후 교체(한 트랜잭션).
