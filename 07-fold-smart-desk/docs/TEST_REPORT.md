# TEST_REPORT

실행일: 2026-10-29(UTC) · 환경: Linux 클라우드 컨테이너, OpenJDK 21, Gradle 8.14.3, Android SDK Platform 36 / Build-Tools 36.0.0
**에뮬레이터·실기기 없음**(KVM 없음) → 설치·실행·실제 폴드 검증은 하지 않았다.

## 실행한 명령과 결과

```bash
cd 07-fold-smart-desk/android
./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

| 항목 | 결과 |
|---|---|
| 빌드 | BUILD SUCCESSFUL |
| 단위·Robolectric 테스트 | **48개 전부 통과**, 실패 0 |
| Lint | 오류 0, 경고 17(모두 '더 새 라이브러리 버전 있음' 안내), 힌트 1 |
| APK | `android/app/build/outputs/apk/debug/app-debug.apk` · 30,803,314 bytes · SHA-256 `77cf7cb9fc831350887139ab5ae0fe5e8c30422d77f206186638806db1f6d81c` · `com.miu.fold.dashboard` 0.1.0(1), 디버그 서명 |

Lint로 잡아 고친 것: API 30~32에서 크래시할 `InputStream.readNBytes`, `PackageManager.ResolveInfoFlags` 사용(NewApi 3건), 백업 규칙 누락.
UI 테스트로 잡아 고친 것: 첫 실행 시 기본 카드 생성 전에 배치를 구독해 "표시할 카드가 없습니다"가 뜨던 경쟁 상태.

## 테스트 클래스

| 클래스 | 수 | 내용 |
|---|---|---|
| `LayoutEditorTest` | 7 | 프로필별 기본 배치, 앞/뒤/위/아래 이동, 원본 불변(취소), span 검증, 숨김·렌더 span 자르기, 기존 배치 보존 reconcile, 창 너비→열 수 |
| `TimerEngineTest` | 7 | T08, T09, 음수 없음·완료 1회, T12 재부팅 중단·재개, 취소 시간 부풀림 없음, 잘못된 전이 무시, 포맷 |
| `UrlValidatorTest` | 4 | https 허용·스킴 보정, T15 위험 스킴 11종 거절, 토큰·계정정보 거절, 패키지명 |
| `DateRulesTest` | 7 | T16 자정 경과·시간대 변경, 할 일 분류, 종일/시간 지정 일정 시간대, 종료<시작 거절, 제목 제안 |
| `RepositoryTest` (Robolectric+Room) | 14 | T05/T06 좁은 배치 변경 시 넓은 배치 불변, 재실행 시 사용자 배치 보존, T04 초기화·카드 삭제 후 데이터 유지, 카드 수량 제한, T02 500자 메모 DB 재오픈 후 유지, 자동 저장 디바운스, T03 저장 공간 부족(SQLiteFullException) 시 실패 표시·초안 보존, T19 완료/되돌리기 연타, T10/T11 활성 세션 1개·프로세스 재생성 복원·완료 기록 1건, 바로가기 거절, T17/T20 백업 왕복(추가/교체·참조 재연결·미지원 카드), 외부/변조 백업 거절, 검색 범위·와일드카드 이스케이프, 픽스처 백업 |
| `HomeUiTest` (Robolectric+Compose) | 6 | T01 360dp 첫 실행 기본 카드·연동 카드 없음, 빠른 메모 입력→DB 저장, 홈 카드에서 타이머 시작·일시정지, **T07 넓은 화면 편집 중 접기(창 900→360dp 재생성) → 편집 프로필 유지·취소 시 배치 그대로**, 600dp·1280dp 렌더 |
| `ScreenshotTest` | 3 | 360/840/1280dp 미리보기 PNG(`docs/screenshots/`). 검증이 아니라 기록용 |

## 검수 시나리오 대응(명세 7장)

| ID | 상태 | 근거 |
|---|---|---|
| T01 | JVM 통과 | HomeUiTest |
| T02 | 일부 | DB 재오픈 유지 통과. **회전 중 입력 유지는 실기기 미검증** |
| T03 | JVM 통과 | 가짜 저장소로 SQLiteFullException 재현. 실제 저장 공간 부족 미재현 |
| T04, T05, T06 | JVM 통과 | RepositoryTest, HomeUiTest |
| T07 | JVM 통과(시뮬레이션) | 리소스 한정자 변경+Activity 재생성. 실제 접기 미검증 |
| T08, T09, T12 | JVM 통과 | 가짜 Clock |
| T10, T11 | JVM 통과 | 새 TimerController로 재생성 흉내 |
| T13 | N/A | 알림·정확 알람 기능 미구현. 권한을 요청하지 않으므로 앱 내부 타이머는 권한과 무관하게 동작 |
| T14 | 미검증 | 실행 불가 시 안내 코드는 있으나 실제 앱 삭제 후 확인은 기기 필요 |
| T15 | JVM 통과 | URL·백업 바로가기·내부 목적지 검증 |
| T16 | JVM 통과 | |
| T17 | JVM 통과 | 미지원 카드 경고·표시, 크래시 없음 |
| T18 | N/A | ImportedSnapshot 미구현 |
| T19 | JVM 통과 | |
| T20 | JVM 통과 | 타이머 '진행 중 세션'은 의도적으로 백업 제외 |

## 미검증(실기기·에뮬레이터 필요)

- 실제 폴드 접기/펼치기·회전·분할 화면·팝업 창, 힌지 위치
- 글자 200%, TalkBack, 키보드 표시 시 입력칸 가림
- 앱 백그라운드 후 복귀·실제 재부팅 후 타이머
- 시스템 공유 시트 수신/발신, SAF 백업 파일 선택, 설치 앱 목록·실행
- 대량 데이터(메모 수천 건) 성능, 앱 업데이트 후 DB 마이그레이션(아직 v1뿐)
- 다크 테마 화면 확인
