// ===== 상태 =====
let MEET_POINT = { lat: 37.49793, lng: 127.02757 };
let VEHICLE_POS = { lat: MEET_POINT.lat, lng: MEET_POINT.lng }; // 서버에서 받기 전까지 더미
let MY_POS     = null; // Geolocation으로 채움 (2단계)
let sessionInfo = null; // /me 또는 /redeem 응답

let map, vehicleMarker, meMarker, meetMarker, pathLine;

// ===== 세션 부트스트랩: 토큰 교환 또는 쿠키 복구 =====
(async function bootstrap() {
  const params = new URLSearchParams(location.search);
  const token = params.get('t');

  try {
    if (token) {
      // SMS 링크로 처음 들어온 경우 — 토큰을 쿠키로 교환
      const res = await fetch('/api/sessions/redeem', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ token }),
      });

      if (res.ok) {
        sessionInfo = await res.json();
      } else {
        // redeem 실패 (already_redeemed / expired / invalid_token 등)
        // 쿠키가 남아있을 수도 있으니 /me 로 폴백 시도
        const body = await res.text();
        const meRes = await fetch('/api/sessions/me', { credentials: 'same-origin' });
        if (meRes.ok) {
          sessionInfo = await meRes.json();
        } else {
          throw new Error('redeem_failed: ' + body);
        }
      }

      // URL에서 토큰 즉시 제거 (히스토리/Referer 노출 방지)
      history.replaceState(null, '', location.pathname);
    } else {
      // 새로고침 등 — 쿠키로 세션 복구
      const res = await fetch('/api/sessions/me', { credentials: 'same-origin' });
      if (!res.ok) throw new Error('no_session');
      sessionInfo = await res.json();
    }

    applySessionInfo(sessionInfo);
    kakao.maps.load(initMap);

  } catch (e) {
    showExpired(e.message);
  }
})();

function applySessionInfo(s) {
  MEET_POINT = { lat: s.pickupLat, lng: s.pickupLng };
  // 바텀시트 UI 업데이트
  const q = (sel) => document.querySelector(sel);
  if (q('.vehicle-model') && s.vehicleModel) q('.vehicle-model').textContent = s.vehicleModel;
  if (q('.vehicle-plate') && s.vehiclePlate) q('.vehicle-plate').textContent = s.vehiclePlate;
  if (q('.vehicle-color') && s.vehicleColor) q('.vehicle-color').textContent = `차량 색상 · ${s.vehicleColor}`;
  if (q('.pickup-name')  && s.pickupName)   q('.pickup-name').textContent  = s.pickupName;
  if (q('.marker-meet') && s.pickupName)    q('.marker-meet').textContent  = `만남 지점 · ${s.pickupName}`;
}

function showExpired(reason) {
  document.body.innerHTML = `
    <div style="min-height:100vh;display:flex;align-items:center;justify-content:center;flex-direction:column;padding:24px;text-align:center;background:#f7f9fd;color:#172239;font-family:-apple-system,sans-serif;">
      <div style="font-size:48px;margin-bottom:12px;">🔗</div>
      <div style="font-size:18px;font-weight:700;margin-bottom:6px;">링크가 만료되었거나 유효하지 않아요</div>
      <div style="font-size:13px;color:#64728a;">운전자에게 새 링크를 요청해 주세요.</div>
      <div style="font-size:11px;color:#a0a9bd;margin-top:16px;">(${reason})</div>
    </div>`;
}

function initMap() {
  const center = new kakao.maps.LatLng(MEET_POINT.lat, MEET_POINT.lng);
  map = new kakao.maps.Map(document.getElementById('map'), { center, level: 3 });

  drawVehicleMarker();
  drawMeetMarker();
  drawPath([VEHICLE_POS, MEET_POINT]);

  document.getElementById('recenterBtn').addEventListener('click', () => {
    if (MY_POS) {
      map.setCenter(new kakao.maps.LatLng(MY_POS.lat, MY_POS.lng));
      map.setLevel(3);
    }
  });

  connectWebSocket();
}

function drawMeMarker() {
  if (meMarker || !MY_POS) return;
  const content = document.createElement('div');
  content.className = 'marker-me';
  meMarker = new kakao.maps.CustomOverlay({
    position: new kakao.maps.LatLng(MY_POS.lat, MY_POS.lng),
    content, xAnchor: 0.5, yAnchor: 0.5, zIndex: 2,
  });
  meMarker.setMap(map);
}

// ===== WebSocket (쿠키 인증, 자동 재연결) =====
let ws = null;
let wsReconnectTimer = null;

function connectWebSocket() {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  const url = `${scheme}://${location.host}/ws/pedestrian`;

  try { ws?.close(); } catch {}
  ws = new WebSocket(url);

  ws.addEventListener('open', () => {
    console.log('[WS] connected');
  });

  ws.addEventListener('message', (ev) => {
    try {
      const msg = JSON.parse(ev.data);
      handleWsMessage(msg);
    } catch (e) {
      console.warn('[WS] bad message', ev.data);
    }
  });

  ws.addEventListener('close', (ev) => {
    console.warn('[WS] closed', ev.code, ev.reason);
    scheduleReconnect();
  });

  ws.addEventListener('error', (e) => {
    console.warn('[WS] error', e);
  });
}

function scheduleReconnect() {
  clearTimeout(wsReconnectTimer);
  wsReconnectTimer = setTimeout(connectWebSocket, 2000);
}

function handleWsMessage(msg) {
  switch (msg.type) {
    case 'vehicle_location':
      VEHICLE_POS = { lat: msg.lat, lng: msg.lng };
      vehicleMarker.setPosition(new kakao.maps.LatLng(msg.lat, msg.lng));
      drawPath([VEHICLE_POS, MEET_POINT]);
      break;
    case 'eta':
      updateEta(msg.minutes, msg.status);
      break;
    default:
      console.log('[WS] unhandled', msg);
  }
}

function updateEta(minutes, status) {
  const etaEl = document.querySelector('.eta-value');
  const pillEl = document.querySelector('.vehicle-status span');
  if (etaEl && minutes != null) etaEl.textContent = `${minutes}분`;
  if (pillEl && status) pillEl.textContent = status;
}

// ===== 마커 =====
function drawVehicleMarker() {
  const content = document.createElement('div');
  content.className = 'marker-vehicle';
  content.textContent = '차';
  vehicleMarker = new kakao.maps.CustomOverlay({
    position: new kakao.maps.LatLng(VEHICLE_POS.lat, VEHICLE_POS.lng),
    content, xAnchor: 0.5, yAnchor: 0.5, zIndex: 3,
  });
  vehicleMarker.setMap(map);
}

function drawMeetMarker() {
  const content = document.createElement('div');
  content.className = 'marker-meet';
  content.textContent = '만남 지점 · 스타벅스 앞';
  meetMarker = new kakao.maps.CustomOverlay({
    position: new kakao.maps.LatLng(MEET_POINT.lat, MEET_POINT.lng),
    content, xAnchor: 0.5, yAnchor: 1.2, zIndex: 4,
  });
  meetMarker.setMap(map);
}

function drawPath(points) {
  pathLine?.setMap(null);
  pathLine = new kakao.maps.Polyline({
    path: points.map(p => new kakao.maps.LatLng(p.lat, p.lng)),
    strokeWeight: 4,
    strokeColor: '#2766e7',
    strokeOpacity: 0.9,
    strokeStyle: 'solid',
  });
  pathLine.setMap(map);
}

// ===== Geolocation 송출 =====
let geoWatchId = null;
let pedestrianSentCount = 0;

document.querySelector('.cta-btn').addEventListener('click', startSharingLocation);

function startSharingLocation() {
  if (!('geolocation' in navigator)) {
    showGeoError('이 브라우저는 위치 기능을 지원하지 않습니다.');
    return;
  }
  if (geoWatchId !== null) return;

  const btn = document.querySelector('.cta-btn');
  btn.textContent = '위치 조회 중…';
  btn.disabled = true;
  console.log('[geo] watchPosition 시작');

  geoWatchId = navigator.geolocation.watchPosition(
    (pos) => {
      const { latitude: lat, longitude: lng, accuracy } = pos.coords;
      console.log('[geo] 수신', lat, lng, '정확도:', accuracy, 'm');
      MY_POS = { lat, lng };
      drawMeMarker();
      meMarker?.setPosition(new kakao.maps.LatLng(lat, lng));

      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'pedestrian_location', lat, lng }));
        pedestrianSentCount += 1;
        btn.textContent = `위치 공유 중 · 전송 ${pedestrianSentCount}`;
        btn.disabled = false;
      } else {
        btn.textContent = 'WebSocket 미연결';
      }
    },
    (err) => {
      console.warn('[geo] error code=' + err.code, err.message);
      const msg = geoErrorDetail(err);
      showGeoError(msg);
      btn.textContent = '위치 공유 재시도';
      btn.disabled = false;
      geoWatchId = null;
    },
    {
      enableHighAccuracy: true,
      maximumAge: 1000,
      timeout: 15000,
    }
  );
}

function geoErrorDetail(err) {
  // 1 = PERMISSION_DENIED, 2 = POSITION_UNAVAILABLE, 3 = TIMEOUT
  switch (err.code) {
    case 1: return '위치 권한이 거부되었습니다.\n\n' +
      'macOS 사용자: 시스템 설정 > 개인정보 보호 및 보안 > 위치 서비스 → 브라우저 켜져있는지 확인.\n' +
      '브라우저: 주소창 왼쪽 자물쇠 클릭 → 위치 "허용"';
    case 2: return '위치 정보를 가져올 수 없음 (POSITION_UNAVAILABLE).\n\n' +
      '원인 가능성:\n' +
      '· macOS 위치 서비스가 꺼져있음 (설정 > 개인정보 > 위치 서비스)\n' +
      '· Wi-Fi 꺼져있거나 네트워크 측위 불가';
    case 3: return '위치 조회 시간 초과 (TIMEOUT) — 다시 시도';
    default: return err.message || '알 수 없는 오류';
  }
}

function showGeoError(msg) {
  console.warn(msg);
  // 바텀시트 상단에 오류 배너
  let banner = document.querySelector('.geo-error-banner');
  if (!banner) {
    banner = document.createElement('div');
    banner.className = 'geo-error-banner';
    banner.style.cssText = 'margin:8px 16px;padding:12px;background:#fff4f4;border:1px solid #ffb3b3;border-radius:8px;color:#8c2222;font-size:12px;white-space:pre-wrap;';
    document.querySelector('.sheet').prepend(banner);
  }
  banner.textContent = msg;
}
