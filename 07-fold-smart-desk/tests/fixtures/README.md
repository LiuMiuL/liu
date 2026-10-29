# 테스트 픽스처

JVM 테스트(`android/app/src/test`)는 픽스처를 코드 안에서 직접 만든다(가짜 Clock, 인메모리/임시 Room DB).
아래 파일은 사람이 직접 '백업 가져오기'를 시험할 때 쓰는 예시다. 실제 사용 데이터가 아니다.

- `backup-unknown-card.json`: 지원하지 않는 카드 종류(`pc_status_v9`)와 위험한 바로가기(`javascript:`)가 섞인 백업.
  가져오면 '지원하지 않는 카드' 안내가 나오고 위험한 바로가기는 제외되어야 한다(HOME-T15, T17).
