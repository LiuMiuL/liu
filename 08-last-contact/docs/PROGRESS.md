# PROGRESS — 연락 기록

## 현재
0.1.0: 명세의 1~4단계 기능 구현, PC 테스트 37개 통과, Lint 오류 0, 디버그·릴리스 APK 생성. 실기기 미검증.

## 작업 기록(2026-10-30~31)
- 카카오 공식 API에 대화 기록 읽기가 없어 내보내기 파일·알림·통화문자·직접 기록 4경로로 설계(SPEC.md).
- 실패와 원인
  1. 같은 날 여러 경로 중 최신을 고를 때 `atMillis ?: -1`(Int)과 Long 비교로 ClassCastException → `-1L`.
  2. 줄바꿈 없는 거대 파일·압축 폭탄에서 한 줄을 통째로 읽어 메모리 부족 위험(테스트가 10분 넘게 멈춤) → 글자 단위로 읽으며 줄 길이·전체 길이 한도 확인.
  3. 화면 테스트가 끝나지 않음: 테스트에서 `activity.intent = null` 설정이 액티비티 종료를 막음 → 제거.
  4. WorkManager 예약 실패 예외가 앱 실행을 막을 수 있음 → 예외를 잡아 예약만 건너뜀.
  5. 앱을 열 때 설정을 읽기 전 기본값으로 '둘 다 꺼짐'으로 보고 예약을 취소할 수 있던 경쟁 상태 → 저장된 설정을 읽은 뒤 판단.
  6. Robolectric에서 화면을 360×780dp로 흉내 내면 입력칸이 든 대화상자가 idle 상태가 되지 않음(기본 크기·입력칸 없는 대화상자는 정상, 스레드 덤프상 측정 반복). 앱 테마·키보드 여백·대화상자 내용 각각 빼 보는 실험으로 앱 코드와 무관함을 확인 → 대화상자는 단독 테스트, 앱 안 흐름은 실기기 테스트로 이동. **실기기에서 '사람 추가'가 정상인지 꼭 확인**.
- 시간 초과 대비 재개 예약(13:00 KST) 설정.

## 주요 파일
`android/app/src/main/java/com/miu/lastcontact/`
- `domain/` KakaoExportParser(해석), ImportPlanner(1:1/단체 규칙), KakaoNotificationRules, CallSmsRules, ContactRules(경과·초과·정렬)
- `data/` 엔터티·DAO·ContactRepository(합치기·매칭·미확인 연결·가져오기 반영)
- `capture/` KakaoNotificationListener, CallSmsImporter, Reminder(WorkManager)
- `importer/` ExportReader(txt/zip/CP949, 크기 제한), ImportViewModel·화면
- `people/`, `settings/`, `backup/`, `ui/`
- 테스트: `app/src/test`(37), `app/src/androidTest/DeviceSmokeTest`(4, 실기기용, 미실행)

## 다음 실행
```bash
cd 08-last-contact/android
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```
Windows: `08-last-contact\tools\device-test.bat`
