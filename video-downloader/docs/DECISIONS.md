# DECISIONS — VideoGrab

| ID | 결정 | 이유 |
|---|---|---|
| V-01 | 엔진은 youtubedl-android 0.18.1(yt-dlp + Python 3 + FFmpeg + QuickJS) | 유튜브 등 수천 사이트 지원, 유튜브의 JS 확인에 필요한 QuickJS를 라이브러리가 `--js-runtimes`로 연결. 직접 다운로더를 만들면 사이트마다 깨짐 |
| V-02 | 기기 종류별 APK(ABI split: arm64-v8a, armeabi-v7a, x86_64) | 런타임이 ABI마다 약 50MB. 하나로 묶으면 200MB+ |
| V-03 | `useLegacyPackaging = true`(extractNativeLibs) | 라이브러리가 libpython.zip.so 등을 파일로 풀어 실행해야 함 |
| V-04 | 화질은 `-f bv*+ba/b -S res:N,ext:mp4:m4a --merge-output-format mp4` | "N 이하 최고"를 형식 정렬로 고르고, 갤러리 호환을 위해 H.264/AAC mp4 우선. 데스크톱 yt-dlp로 실제 확인 |
| V-05 | `--no-playlist` + `--playlist-end 50` | 재생목록 안 영상 링크는 그 영상만, 재생목록 자체 링크는 50개 상한 |
| V-06 | WorkManager 고유 작업 `APPEND_OR_REPLACE` 하나가 대기열을 순차 처리 | 동시에 여러 개 받으면 차단·발열 위험. 처리 중 새 링크가 와도 뒤에 붙은 작업이 다시 확인 |
| V-07 | 포그라운드 유형 dataSync | Android 14+ 필수. Android 15는 하루 6시간 제한(문서화) |
| V-08 | 작업별 임시 폴더(`noBackupFilesDir/downloads/<id>`) → MediaStore 복사 → 폴더 삭제 | 저장소 권한 불필요(Android 10+), 반쯤 쓴 파일이 갤러리에 보이지 않게 IS_PENDING 사용 |
| V-09 | 파일 템플릿 `%(title).150B [%(id)s].%(ext)s`, 갤러리 이름은 `[id]` 제거 | 겹침 방지 + 읽기 좋은 이름. 조각 파일(`].f<형식ID>.ext`) 판별에도 사용 — 형식 ID에 문자 포함(`faud-Korean`)을 실제 출력에서 발견 |
| V-10 | 엔진 자동 업데이트: 안정판, 하루 1회, 실패 직후는 1시간 간격, 설정으로 끔 | 사이트 변경이 가장 흔한 실패 원인. 참고 앱은 개발판을 매 실행 — 안정성 위해 바꿈 |
| V-11 | 사이트 변경 오류 → 업데이트로 새 버전을 받았을 때만 그 링크 1회 자동 재시도 | 무한 반복 방지(autoRetried) |
| V-12 | 같은 링크가 대기·진행 중이면 추가 안 함(스킴·호스트 대소문자, www./m., 끝 '/' 무시, 쿼리는 유지) | 실수로 두 번 공유 방지. 쿼리가 다르면 다른 영상일 수 있음 |
| V-13 | 알림 권한은 앱에서 처음 다운로드를 누를 때 한 번만 요청 | 거절해도 다운로드·앱 목록은 동작 |
| V-14 | 테스트 JVM을 UTF-8 로캘로 실행 | 이 빌드 환경의 기본 로캘에서 한글 파일 이름이 깨져 테스트가 실제 버그를 가림 |
| V-15 | 참고 저장소(GPL-3.0) 코드 미사용, 아이디어만 반영 | 라이선스 전염 방지(REFERENCE_NOTES.md) |
