# CardListener - 카드 알림 자동기록

카드 결제 알림을 감지하여 회계팀장 앱에 자동 기록하는 Android 앱.

## 빌드
- GitHub Actions: push 시 자동 빌드, Artifacts에서 APK 다운로드
- 로컬: Android Studio에서 Build > Build APK

## 지원 카드사
하나카드, 삼성카드, KB국민카드, 신한카드, 롯데카드

## 서버
POST https://coinbell.cafe24.com/acct/api/auto-record
