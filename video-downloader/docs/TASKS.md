# TASKS — VideoGrab

상태: `TODO → IN_PROGRESS → IMPLEMENTED_UNVERIFIED → DONE`(실기기 확인까지), `DEFERRED`, `BLOCKED_EXTERNAL`.
현재는 실기기가 없어 JVM/Robolectric + 데스크톱 yt-dlp 확인까지만 했다.

| ID | 작업 | 상태 | 검증 |
|---|---|---|---|
| VG-01 | 공유 받기 → 즉시 대기열(ShareReceiverActivity) | IMPLEMENTED_UNVERIFIED | ShareReceiverTest |
| VG-02 | 링크 추출·중복 방지·여러 링크 | IMPLEMENTED_UNVERIFIED | UrlExtractorTest, QueueProcessorTest |
| VG-03 | 화질 4종 yt-dlp 옵션 | IMPLEMENTED_UNVERIFIED | DownloadCommandTest + 데스크톱 yt-dlp 실행 |
| VG-04 | 순차 대기열 처리·진행률·제목 | IMPLEMENTED_UNVERIFIED | QueueProcessorTest, 실제 yt-dlp 출력 fixture |
| VG-05 | 갤러리(MediaStore) 저장, 실패 시 되돌리기 | IMPLEMENTED_UNVERIFIED | QueueProcessorTest(가짜 저장) — **MediaStore 실제 저장은 기기에서 미검증** |
| VG-06 | 백그라운드 포그라운드 작업·알림 | IMPLEMENTED_UNVERIFIED | 미검증(기기 필요) |
| VG-07 | 취소·다시 시도·강제 종료 후 이어 받기 | IMPLEMENTED_UNVERIFIED | QueueProcessorTest |
| VG-08 | 오류 한국어 안내 | IMPLEMENTED_UNVERIFIED | ErrorClassifierTest(실제 오류 fixture 포함) |
| VG-09 | 엔진 자동 업데이트·1회 자동 재시도 | IMPLEMENTED_UNVERIFIED | AutoEngineUpdaterTest, QueueProcessorTest — 실제 GitHub 업데이트는 기기에서 미검증 |
| VG-10 | 설정: Wi-Fi만, 자동 업데이트, 수동 업데이트 | IMPLEMENTED_UNVERIFIED | — |
| VG-11 | 실기기(폴드) 검증: 유튜브 1080p/720p/소리만, 쇼츠, 재생목록, 취소, 강제 종료 | TODO | 기기 필요 |
| VG-12 | 메인 화면 UI 테스트(Compose) | TODO | — |
| VG-13 | Wi-Fi 설정을 바꾸면 이미 예약된 작업에도 적용 | TODO | 현재는 다음 추가 링크부터 |
| VG-14 | 화질 직접 고르기(다운로드 전 형식 목록), 자막 저장 | TODO | — |
| VG-15 | 완료 항목 "파일도 삭제", 앱 내 재생 | TODO | — |
| VG-X1 | 앱 내 브라우저·스트림 감지 | DEFERRED | 범위 밖(REFERENCE_NOTES) |
| VG-X2 | 로그인 필요 영상(쿠키) | DEFERRED | 계정·약관 위험, 사용자 결정 필요 |
