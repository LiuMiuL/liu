# TEST_REPORT — 연락 기록

실행: 2026-10-31(UTC) · Linux 컨테이너, OpenJDK 21, Gradle 8.14.3, SDK 36 · **에뮬레이터·실기기 없음**

```bash
./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDeviceTestAndroidTest
```

| 항목 | 결과 |
|---|---|
| PC 테스트 | **37개 통과**, 실패 0 |
| Lint | 오류 0, 경고 23(모두 새 버전 안내) |
| 디버그 APK | 30,823,467 B · SHA-256 `20aa64392b61315a870731544111f4d6737059215ceaefbffb26ea758c6cdb07` |
| 릴리스 APK(R8) | 3,182,887 B · SHA-256 `e00ae4371bf53a979e4b146da35a96997d40c351a2e2f9aacccbdaf7f74e5e7a` · 개발용 키(인증서 `e219795e…4646b2`) · 직렬화·DB·알림 서비스·워커 클래스 보존 확인 |
| 실기기 테스트 APK | 빌드만 성공, **미실행** |

| 클래스 | 수 | 내용 |
|---|---|---|
| KakaoExportParserTest | 9 | 안드로이드 대괄호·옛 안드로이드·PC/iOS 12h/24h·ISO 형식, 오전 12시, 시스템 줄 제외, 비카톡 파일 거절, 긴 줄·잘못된 날짜, 1:1 방향, 단체방 규칙, 두 이름 합치기 |
| ExportReaderTest | 6 | UTF-8 BOM, CP949, ZIP 안 txt, txt 없는 ZIP, 압축 폭탄(한도 축소로 시험), 열기 실패 |
| RulesTest | 7 | 번호 정규화, 이름 키, 경과·초과, 정렬, 카톡 알림 판별(요약·광고·단체·내 메시지), 통화·문자 매칭, 알림 문구 |
| RepositoryTest | 7 | 같은 날·경로 합치기(LC-T01·T03), 최신 선택, 사람 저장 검증, 알림 매칭·미확인·무시·연결(LC-T07·T08), 내보내기 반영·재가져오기 중복 없음(LC-T04), 삭제 연쇄, 백업 추가/교체·거절(LC-T12) |
| UiTest(Robolectric) | 4 | 첫 실행 안내, 오늘 연락함·되돌리기·목록 빠른 버튼, 카톡 공유→확인→저장(LC-T04), 넓은 화면 2패널 |
| PersonDialogTest | 1 | 사람 추가 대화상자: 빈 이름 오류, 이름·번호(+82)·목표 저장 |
| ScreenshotTest | 3 | 예시 데이터 미리보기 |

## 알려진 테스트 한계
Robolectric에서 360×780dp로 흉내 내면 입력칸이 든 대화상자가 idle이 되지 않는다(테마·키보드 여백·내용 각각 제외 실험으로 앱 코드 무관 확인, 기본 크기에서는 0.6초 통과). 앱 안에서 '사람 추가'로 입력하는 흐름은 `DeviceSmokeTest`(실기기)에 넣었다.

## 미검증(실기기 필요)
실제 카톡 내보내기·알림 구조, 알림 접근 '제한된 설정', 통화·문자 권한과 실제 기록, 매일 알림 도착, 연락처 선택기, 백업 파일 선택, 폴드 접기/펼치기, 글자 200%·TalkBack, R8 릴리스 실행.
