# Fold Deal Board (06. 중고거래 가격 비교)

폴드형 Android 스마트폰용 **개인 중고 매물 비교판**입니다. 여러 중고 플랫폼에서 본 매물을 직접 저장하고,
같은 사양끼리 가격·배송비·상태·확인일을 한 화면에서 비교합니다.

- 독립 앱입니다. 다른 앱, 공용 서버, 공용 DB, 로그인, API 키 없이 동작합니다.
- **자동 가격 수집은 하지 않습니다.** 모든 가격은 사용자가 직접 기록한 값이며 화면에도 그렇게 표시합니다.
- 패키지: `com.miu.fold.deals` · versionName `0.3.0` (versionCode 3) · minSdk 30 · targetSdk 35

## 현재 실제 구현 범위 (0~1단계)

| 기능 | 상태 |
|---|---|
| 매물 직접 입력·수정·삭제(복원 가능), 다른 앱의 텍스트/링크 공유 받기 | 구현 |
| 모델 원문/정규화 키, RAM(총량·모듈 구성)·저장공간 분리, 원문 보존 | 구현 |
| 가격 미확인 / 가격 협의 / 0원 구분, 일괄·개당·보증금 표기 구분 | 구현 |
| 배송비 포함·별도·직거래·미확인 구분, 미확인이면 총비용 미확인 | 구현 |
| 가격 관측 누적(덮어쓰기 없음), 게시가·협상 제안·확인 거래가 구분 | 구현 |
| 2~4개 비교표(항목 열 고정, 차이·미확인 강조, 다른 항목만 보기, 예산 대비) | 구현 |
| 좁은 폭·큰 글꼴에서 매물별 카드로 전환, 600dp 이상에서 목록+상세 2패널 | 구현 |
| 같은 조건 저장 매물 집계(최저·중앙값·범위·표본 수·제외 사유) | 구현 |
| 비교 저장(사용한 가격 관측 고정, 새 관측·삭제·판매 완료 표시) | 구현 |
| 로컬 DB(Room) 영속 저장, 재실행 후 보존 | 구현 |
| 보드 조건 필터: 분류·기종·RAM·저장공간(여러 개 선택), 등록일 24시간/3일/일주일/30일 이내, 가격 확인일 이내. 값이 미확인인 매물은 "확인 필요"로 분리 | 구현 (0.2.0) |
| 등록일 "3일 전·어제·5시간 전" 입력을 처음 저장한 시각 기준 날짜로 변환 | 구현 (0.2.0) |
| 고른 조건(분류·기종·RAM·용량·등록일·확인일·정렬·판매 중만·제외 숨김)을 기기에 저장 — 앱을 껐다 켜도 유지 | 구현 (0.3.0) |
| 스크린샷 첨부, 보드 여러 개(예산·조건 묶음) 전환, 조건 알림, 체크리스트, 내보내기·백업 | 미구현 → `docs/TASKS.md` |

실기기(폴드) 검증은 아직 하지 않았습니다. 검증 범위는 `docs/TEST_REPORT.md`를 보세요.

## 빌드

필요: JDK 17 이상(검증은 OpenJDK 21), Android SDK Platform 35, Build-Tools 35.0.0.

```bash
cd 06-fold-deal-board/android
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # 또는 ANDROID_HOME 설정
./gradlew testDebugUnitTest lintDebug assembleDebug
# 결과: app/build/outputs/apk/debug/app-debug.apk
```

## Windows PC로 가져오기

```bat
cd /d "D:\Miu\Projects"
git clone -b ccr-36ab13ac-iwpu74 https://github.com/LiuMiuL/liu.git "중고거래 가격 비교 어플"
cd "중고거래 가격 비교 어플\06-fold-deal-board\android"
gradlew.bat testDebugUnitTest assembleDebug
```

Android Studio에서는 `06-fold-deal-board/android` 폴더를 열면 됩니다(SDK 경로는 Android Studio가 `local.properties`에 자동 기록).

## 설치·제거

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb uninstall com.miu.fold.deals   # 앱 데이터(로컬 DB)도 함께 삭제됩니다
```

디버그 서명 APK입니다. 스토어 배포용 서명은 아직 설정하지 않았습니다.

## 문서

- `docs/SPEC.md` 기준 명세 · `docs/TASKS.md` 티켓 상태 · `docs/PROGRESS.md` 진행 상황
- `docs/DECISIONS.md` 기술 결정 · `docs/DATA_MODEL.md` 데이터 모델 · `docs/TEST_REPORT.md` 검증 기록 · `docs/SECURITY.md` 권한·보안
