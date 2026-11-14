# Picko 서버 배포 가이드 (Railway)

최단 경로. 10~20분이면 공개 HTTPS URL로 서비스 올라감.

---

## 사전 준비 (1회성)

### 1. GitHub 저장소 생성

```bash
cd ~/Desktop/Personal\ Project/pleos-server
git init
git add .
git commit -m "Picko server initial"
# GitHub에서 pleos-server (private 권장) 저장소 만든 뒤
git remote add origin git@github.com:<본인아이디>/pleos-server.git
git push -u origin main
```

> 🚨 `pleos-web`, `pleos-app` 도 별도 저장소로 올려두면 백업 겸. 이건 선택.

### 2. Railway 가입
- https://railway.com → GitHub 계정으로 가입
- 결제카드 등록 (사용량 과금이지만 무료 $5/월 크레딧 — 이 앱 트래픽 수준엔 거의 안 넘어감)

---

## 배포

1. Railway 콘솔 → **New Project** → **Deploy from GitHub repo** → `pleos-server` 선택
2. Railway가 자동으로 `Dockerfile` 감지 → 빌드 시작 (처음엔 3~5분)
3. 완료되면 **Settings → Networking → Generate Domain** 클릭
   → `pleos-server-production-xxxx.up.railway.app` 같은 URL 생성됨

### 환경변수 등록
Railway 콘솔 → **Variables** 탭에서 추가:

| 키 | 값 | 비고 |
|---|---|---|
| `KAKAO_REST_API_KEY` | `b0664cb48af6552829a377190af6f8db` | (현재 로컬에서 쓰는 거) |
| `APP_WEB_BASE_URL` | `https://pleos-server-production-xxxx.up.railway.app` | ⚠️ 위에서 생성된 Railway URL 그대로 |
| `COOLSMS_API_KEY` | (쿨SMS 가입 후) | 없으면 SMS mock 모드 |
| `COOLSMS_API_SECRET` | | |
| `COOLSMS_SENDER_PHONE` | `01012345678` | 쿨SMS에 등록한 발신번호 |

> ⚠️ `APP_WEB_BASE_URL` 맞게 넣어야 SMS 링크 / `webUrl` 응답이 올바른 도메인 됨.

변수 저장 후 Railway가 자동 재배포.

---

## Kakao 설정 (중요!)

배포된 HTTPS 도메인을 Kakao 쪽에 등록해야 지도가 뜸.

1. https://developers.kakao.com → 내 애플리케이션 → `Picko` → **플랫폼 키**
2. Default JS Key (또는 "테스트용") → **JavaScript SDK 도메인** 에 추가:
   ```
   https://pleos-server-production-xxxx.up.railway.app
   ```
3. 저장

---

## Android 앱 BASE_URL 변경

`pleos-app/app/src/main/java/ai/pleos/myapp/PickupApi.kt`:

```kotlin
const val BASE_URL = "https://pleos-server-production-xxxx.up.railway.app"
```

(기존 `http://10.0.2.2:5173` 에서 바꿈)

변경 후 Android Studio에서 **Rebuild + Run**. 이제 에뮬이든 실기기든 공개 서버로 요청감.

---

## 테스트 체크리스트

### 1. 서버 헬스 확인
```bash
curl https://pleos-server-production-xxxx.up.railway.app/api/exclusions/stats
```
→ `{"total":45042, "bySource":{...}}` 떠야 OK

### 2. 추천 API
```bash
curl "https://pleos-server-production-xxxx.up.railway.app/api/pickup/recommend?vLat=37.517&vLng=127.047&dLat=37.498&dLng=127.028&limit=3"
```

### 3. 세션 생성 + webUrl 수동 복붙
```bash
curl -X POST https://pleos-server-production-xxxx.up.railway.app/api/sessions \
  -H "Content-Type: application/json" \
  -d '{"pickupLat":37.498,"pickupLng":127.030,"pickupName":"CU 역삼강남점","vehicleModel":"현대 아반떼","vehiclePlate":"123가 4567","vehicleColor":"흰색"}'
```
→ 응답의 `webUrl` 을 폰 브라우저에 붙여넣기 → 지도 뜨면 OK

### 4. Android 앱에서 테스트
- Pleos Connect 에뮬 (BASE_URL 바꾼 상태) → "픽업 요청 생성" → 응답 받으면 OK

---

## 재배포 (코드 바꿀 때)

```bash
cd pleos-server
git add -A
git commit -m "변경 내용"
git push
```

Railway가 push 감지 → 자동 재빌드 (2~3분)

---

## 주의 사항

1. **H2 인메모리 DB** — 서버 재시작 시 모든 세션 데이터 사라짐. 프로토타입엔 OK, 실서비스는 Postgres 로 전환.
    - Railway에서 PostgreSQL Add-on 1분이면 붙일 수 있음 (별도 가이드)

2. **Kakao JS SDK 도메인** — Railway URL 바뀌면 (프로젝트 재생성 등) 다시 등록 필요

3. **CoolSMS 미설정 상태** — 서버는 정상 돌지만 실제 문자 안 나감. 응답 `smsDetail: "sms_not_configured"` 로 명시됨

4. **쿼터**
    - Railway 무료 $5/월: 이 앱 수준(저트래픽)엔 안 넘음
    - Kakao Local API: 일 10만 호출 (추천 1회당 5호출 → 2만 추천/일 가능)
    - Kakao Directions: 쿼터 정책 변경 가능, 콘솔에서 확인

---

## Troubleshooting

### 배포 성공했는데 502
→ Railway Logs 확인. 보통 `PORT` 바인딩 실패. `application.yml` 에 `server.port: ${PORT:5173}` 있는지 확인.

### 브라우저에서 지도 안 뜸
→ 콘솔에서 `domain mismatched` 에러면 Kakao JS SDK 도메인에 Railway URL 등록 확인

### Android 앱이 "Failed to connect"
→ `PickupApi.BASE_URL` 바꾸고 Rebuild 안 했을 가능성. Gradle Sync + Clean + Run

### 세션 쿠키가 안 저장됨 (/me 가 401)
→ HTTPS 아니면 Secure 쿠키가 무효. Railway 도메인은 자동 HTTPS니 괜찮음
