# SECURITY — 연락 기록

- **인터넷 권한 없음.** 모든 자료는 앱 내부 저장소, 클라우드 자동 백업·기기 이전 제외(allowBackup=false, dataExtractionRules).
- 선택 권한(켤 때만 요청, 거절해도 직접 기록·내보내기 가져오기는 동작): 알림 접근(카톡 기록), READ_CALL_LOG·READ_SMS(번호·시각·종류만 조회, 문자 본문 컬럼은 조회하지 않음), POST_NOTIFICATIONS(매일 알림).
- WorkManager가 자동 추가하는 일반 권한: WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, ACCESS_NETWORK_STATE(네트워크 사용 안 함, 라이브러리 기본 선언).
- 카톡 알림: 패키지 `com.kakao.talk`만 처리, 보낸 사람 이름·시각만 저장, 메시지 본문 저장 안 함. 다른 앱 알림은 패키지 확인 후 즉시 무시.
- 카톡 내보내기 파일: 메모리에서 날짜·이름만 추출, 원문·파일 저장 안 함. 크기·줄·ZIP 항목 수 제한.
- 등록하지 않은 전화번호는 저장하지 않음. 연락처는 사용자가 고른 한 명만(READ_CONTACTS 없음).
- 백업 JSON은 평문(이름·날짜 포함)임을 화면에 안내. 앱 수준 암호화는 하지 않음.
- 서명: 저장소의 `keystore/debug.keystore`(공개 표준 비밀번호 개발용 키). 스토어 배포 금지.
