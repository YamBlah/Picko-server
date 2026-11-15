# Picko Server

**현대차 공모전 출품작 — 차량↔탑승자 실시간 위치 공유 + 최적 픽업 위치 추천 서비스의 백엔드.**

운전자(차량)가 탑승자에게 링크를 전달하면 양방향으로 실시간 위치가 공유되고, 서버가 카카오 POI/길찾기 + 서울시 공공 금지 구역 데이터를 종합해 **"세우기 좋은 지점"을 Top N 추천**한다.

**프로덕션**: https://picko.up.railway.app  
**Swagger**: https://picko.up.railway.app/swagger

---

## 구성 리포

| 리포 | 역할 |
|---|---|
| **picko-server** (이 리포) | Spring Boot API + WebSocket 중계 + 추천 엔진 |
| **pleos-web** | 탑승자용 모바일 웹 (Spring static 으로 서빙) |
| **pleos-app** | 운전자용 Android Automotive 앱 (Pleos Connect) |

---

## 기술 스택

- **Spring Boot 3.3** / Java 17 / Gradle (Kotlin DSL)
- **Spring Data JPA** + H2 (dev) → PostgreSQL (prod 전환 가능)
- **Spring WebSocket** (raw frames, STOMP X — 심플 JSON 중계)
- **JTS Topology Suite** — R-tree 로 금지 구역 공간 인덱싱 (45K건)
- **Jackson** — DTO ↔ JSON
- **Springdoc OpenAPI** — Swagger UI 자동 생성
- **외부**:
  - [Kakao Mobility Directions](https://apis-navi.kakaomobility.com) — 차량 경로
  - [Kakao Local Category](https://developers.kakao.com/docs/latest/ko/local/dev-guide) — POI 검색
  - [OSRM foot](https://routing.openstreetmap.de) — 도보 라우팅
  - [SOLAPI](https://solapi.com) — SMS 발송

---

## 패키지 구조

```
ai.pleos.pickup/
├── PleosPickupApplication.java
│
├── domain/           PickupSession, SessionStatus (JPA 엔티티)
├── repository/       JpaRepository
├── dto/              요청/응답 DTO (Swagger 스키마 포함)
│
├── controller/       SessionController, DebugController
├── rec/              PickupRecommender, RecommendationController, PickupCandidate
├── exclusion/        ExclusionZoneService, ExclusionController (금지 구역 R-tree)
├── ws/               SessionChannelRegistry, Vehicle/PedestrianWebSocketHandler
├── client/           KakaoDirectionsClient, KakaoPoiClient, OsrmWalkingClient
├── service/          PickupSessionService, SmsSender
└── config/           WebSocketConfig, OpenApiConfig
```

---

## 핵심 플로우

```
┌──────────────────┐        ┌────────────────────────┐        ┌──────────────────┐
│ Pleos 앱 (운전자) │ ◀────▶ │       picko-server     │ ◀────▶ │  모바일 웹 (탑승자)│
│                   │   WS   │                        │   WS   │                   │
│ 1. 추천 요청      │ ◀───── │ Kakao Directions +     │        │                   │
│ 2. Top1 선택      │        │ POI + 금지구역 필터     │        │                   │
│ 3. 세션 생성      │ ───▶   │ 세션 DB 저장           │        │                   │
│ 4. (option) SMS   │        │ SOLAPI 자동 발송       │ ─────▶ │ SMS 수신          │
│                   │        │                        │        │ 링크 클릭         │
│                   │        │                        │ ◀───── │ POST /redeem       │
│                   │        │ 쿠키 PSID 발급          │ ─────▶ │ 지도 로드         │
│ 5. GPS 송출       │ ────▶ │ SessionChannelRegistry │ ─────▶ │ 차량 마커 이동    │
│                   │        │ 세션별 양방향 중계      │        │                   │
│                   │ ◀──── │                        │ ◀───── │ 6. 내 위치 송출   │
│ 탑승자 좌표 표시   │        │                        │        │                   │
│ 7. (option) 취소  │ ────▶ │ status=CANCELED        │ ─────▶ │ 알림 + 자동 종료  │
└──────────────────┘        └────────────────────────┘        └──────────────────┘
```

---

## API 요약

### Sessions
- `POST /api/sessions` — 세션 생성 (+ SMS 자동 발송)
- `POST /api/sessions/redeem` — 토큰 → 쿠키 교환 (매직 링크)
- `GET  /api/sessions/me` — 쿠키로 세션 조회
- `POST /api/sessions/cancel` — 운전자 취소 + 보행자 WS 알림

### Recommendation
- `GET  /api/pickup/recommend` — 차량+목적지 → Top N 추천
  - (POST 버전은 hidden, vehicleRoute 외부 주입 가능)

### Exclusions (금지 구역)
- `GET /api/exclusions/stats` — 전체 통계 (총 45,042건)
- `GET /api/exclusions/check` — 좌표 1개 금지 여부
- `GET /api/exclusions/in-box` — bbox 조회

### WebSocket
- `wss://.../ws/vehicle?token={TOKEN}` — 운전자 (토큰 인증)
- `wss://.../ws/pedestrian` — 탑승자 (쿠키 PSID 인증)
- 메시지: `{"type":"vehicle_location","lat":..,"lng":..}` / `pedestrian_location` / `session_canceled`

전체 API 는 Swagger 참고 → https://picko.up.railway.app/swagger

---

## 추천 알고리즘

```
입력: 차량 위치(v) + 목적지(d) + limit N

1. Kakao 모빌리티로 v→d 경로 조회 (실측 distance/duration)
2. 경로 80m 간격 샘플링 → "경로상 후보"
3. d 주변 400m POI (편의점/카페/지하철역/은행) 조회 → "POI 후보"
4. 각 후보에 대해:
   - 금지 구역(소화장치/교차로/횡단보도/버스정류소/어린이보호구역/주정차금지) 겹침 검사 (JTS R-tree)
   - score = walkMin + 0.3*detourMin − poiBonus (POI 선호 가중)
5. Top K (= N*2) 에 OSRM 도보 라우팅으로 정확한 walkM 재계산 후 재정렬
6. 비금지 우선 → 점수 오름차순 → Top N 반환
```

금지 구역 데이터 (5개 공공데이터 통합, 지오코딩 후 R-tree 인덱싱):
- 비상소화장치 2,594
- 교차로 7,592 + 횡단보도 21,775
- 버스정류소 11,231 (한강선착장 제외)
- 어린이보호구역 1,626 (반경 300m)
- 서울 주정차금지 224

---

## 로컬 실행

### 전제
- JDK 17
- Gradle (wrapper 포함돼서 별도 설치 X)

### 실행
```bash
./gradlew bootRun
```
→ `http://localhost:5173` 접속 (정적 web + API 둘 다 서빙)

### 환경변수
```bash
export KAKAO_REST_API_KEY=xxx       # 필수. Kakao Developers 에서 발급
export SOLAPI_API_KEY=xxx           # 없으면 SMS mock 모드
export SOLAPI_API_SECRET=xxx
export SOLAPI_SENDER_PHONE=01012345678
export APP_WEB_BASE_URL=http://localhost:5173
```

### H2 콘솔 (dev)
`http://localhost:5173/h2-console`
- JDBC URL: `jdbc:h2:mem:pickup`
- User: `sa` (비밀번호 없음)

### 금지 구역 데이터 재생성
```bash
cd data
python3 build_exclusions.py
# → src/main/resources/pickup_exclusions.geojson 갱신 (약 2분, Kakao 지오코딩 호출)
```

---

## 배포

자세한 가이드: [DEPLOY.md](./DEPLOY.md)

요약:
1. Railway 가입 + GitHub 리포 연결
2. Dockerfile 자동 감지 → 빌드/배포
3. **Variables** 등록: `KAKAO_REST_API_KEY`, `APP_WEB_BASE_URL`, `SOLAPI_*`
4. **Networking → Target Port** = 8080

프로덕션: https://picko.up.railway.app

---

## 참고 사항

- **H2 인메모리 DB**: 재시작 시 세션 데이터 소실. 공모전 데모용엔 OK, 실서비스 전환 시 PostgreSQL 로 교체 (`spring.datasource` 만 바꿔주면 JPA 가 알아서 처리)
- **SOLAPI IP whitelist**: Railway outbound IP 가 재배포 시 바뀔 수 있음. `/api/debug/outbound-ip` 로 확인 후 SOLAPI 콘솔에서 갱신 필요 (클라우드 공급사 GCP 추가해도 직접 IP 1개 이상 필수)
- **OSRM 공개 서버**: `routing.openstreetmap.de` — 데모용. 트래픽 많거나 상용 서비스는 자체 OSRM 호스팅 권장
- **카카오 쿼터**: Directions 는 비제휴 `/v1/directions` 엔드포인트 사용 (REST 키만 있으면 OK, 별도 승인 X)
