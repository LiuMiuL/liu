# SECURITY — 권한·저장 범위·외부 전송

## 권한

- **요청하는 위험 권한 없음.** INTERNET, 위치, 연락처, 캘린더, 알림, 저장소 전체 접근, 접근성 서비스 모두 없음.
  (APK에 보이는 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`은 AndroidX가 자동 추가하는 앱 내부 서명 권한)
- 설치 앱 조회: `QUERY_ALL_PACKAGES` 없음. 매니페스트 `<queries>`에 런처(MAIN/LAUNCHER)와 https 열기 인텐트만 선언.
- 파일: 백업 내보내기/가져오기는 시스템 파일 선택기(SAF)로 사용자가 고른 파일만.

## 저장 범위

- 데이터는 앱 내부 저장소(Room DB, DataStore, `files/backups`)에만 저장. 다른 앱 DB를 읽지 않는다.
- 앱 내부 저장소는 OS 샌드박스로 보호되지만 **별도 앱 수준 암호화는 하지 않는다**(둘은 다른 것).
- `allowBackup=false` + `dataExtractionRules`로 클라우드 자동 백업·기기 이전에서 제외(사용자 확인 없는 외부 전송 방지).

## 외부 전송·실행

- 네트워크 통신 코드 없음. 웹 바로가기는 브라우저에 `ACTION_VIEW`로 넘길 뿐 앱이 직접 접속하지 않는다.
- 바로가기 URL은 https 기본, http는 경고, 그 외 스킴(javascript:, intent:, file:, content:, data: 등) 거절. 계정정보(user:pass@)·토큰/키로 보이는 쿼리 파라미터 저장 거절. 열 때 한 번 더 검증.
- 다른 앱에서 공유받은 텍스트는 데이터로만 취급: 미리보기 → 사용자가 '메모로 저장'을 눌러야 저장. 20,000자 상한. 실행·링크 자동 열기 없음.
- 백업 JSON은 평문이며 메모 등 개인 내용이 그대로 들어간다(화면에 안내). 토큰·API 키·PC 인증정보는 애초에 저장하지 않으므로 포함되지 않는다.
- 가져온 백업의 바로가기는 재검증 후 위험한 것을 제외한다. 백업 안의 문자열은 명령으로 실행되지 않는다.
- 로그·분석 SDK 없음. 비밀값을 코드·Git에 넣지 않았다(`local.properties`는 .gitignore).

## 남은 위험·후속

- 알림 기능 추가 시 POST_NOTIFICATIONS·SCHEDULE_EXACT_ALARM은 필요한 시점에만 요청하고 거절 시 대체 안내.
- 시스템 캘린더 연결은 별도 승인 전 구현하지 않는다.
