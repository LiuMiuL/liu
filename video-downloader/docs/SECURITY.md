# SECURITY — VideoGrab

## 권한
- INTERNET, ACCESS_NETWORK_STATE: 다운로드·엔진 업데이트
- FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC: 백그라운드 다운로드
- POST_NOTIFICATIONS: 진행·완료 알림(처음 다운로드할 때 요청, 거절 가능)
- WAKE_LOCK, RECEIVE_BOOT_COMPLETED: WorkManager가 자동 추가
- 저장소 권한 없음(MediaStore에 앱이 만든 항목만 씀). 연락처·위치·카메라 없음.

## 외부 통신
- 사용자가 공유·붙여넣은 링크의 사이트에만 접속한다(yt-dlp).
- 엔진 업데이트: GitHub의 yt-dlp **안정판** 릴리스를 받는다. 자동 업데이트는 하루 1회이며 설정에서 끌 수 있다. 내려받는 것은 실행 코드이므로 공식 저장소 외 경로는 쓰지 않는다.
- 분석·광고 SDK 없음. 다운로드 기록을 외부로 보내지 않는다.

## 데이터
- 다운로드 기록(링크·제목·상태)은 앱 내부 Room DB, 설정은 DataStore. OS 백업·기기 이전에서 제외.
- 임시 파일은 `noBackupFilesDir`에 두고 완료·실패·취소 시 삭제.
- 공유 텍스트는 링크를 찾는 데이터로만 사용(최대 20,000자), http/https 외 스킴은 무시.

## 하지 않는 것
- DRM 해제, 로그인·쿠키 사용, 프록시·차단 우회. 오류 안내에서도 우회 방법을 권하지 않는다.
