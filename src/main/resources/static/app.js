// ===== 상태 =====
let MEET_POINT = { lat: 37.49793, lng: 127.02757 };
let VEHICLE_POS = { lat: MEET_POINT.lat, lng: MEET_POINT.lng };
let MY_POS = null;
let sessionInfo = null;

let map;
let vehicleMarker, meMarker, meetMarker, pathLine;

// ===== 세션 부트스트랩 =====
(async function bootstrap() {
  const params = new URLSearchParams(location.search);
  const token = params.get('t');

  try {
    if (token) {
      const res = await fetch('/api/sessions/redeem', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ token }),
      });

      if (res.ok) {
        sessionInfo = await res.json();
      } else {
        const body = await res.text();
        const meRes = await fetch('/api/sessions/me', { credentials: 'same-origin' });
        if (meRes.ok) sessionInfo = await meRes.json();
        else throw new Error('redeem_failed: ' + body);
      }
      history.replaceState(null, '', location.pathname);
    } else {
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
  const q = (sel) => document.querySelector(sel);
  if (q('.vehicle-model') && s.vehicleModel) q('.vehicle-model').textContent = s.vehicleModel;
  if (q('.vehicle-plate') && s.vehiclePlate) q('.vehicle-plate').textContent = s.vehiclePlate;
  if (q('.vehicle-color') && s.vehicleColor) q('.vehicle-color').textContent = `차량 색상 · ${s.vehicleColor}`;
  if (q('.pickup-name')  && s.pickupName)   q('.pickup-name').textContent  = s.pickupName;
}

function showExpired(reason) {
  document.body.innerHTML = `
    <div class="expired">
      <div class="emoji">🔗</div>
      <div class="title">링크가 만료되었거나 유효하지 않아요</div>
      <div class="desc">운전자에게 새 링크를 요청해 주세요.</div>
      <div class="reason">(${reason})</div>
    </div>`;
}

// ===== 지도 초기화 =====
function initMap() {
  const center = new kakao.maps.LatLng(MEET_POINT.lat, MEET_POINT.lng);
  map = new kakao.maps.Map(document.getElementById('map'), { center, level: 4 });

  drawVehicleMarker();
  drawMeetMarker();

  document.getElementById('recenterBtn').addEventListener('click', () => {
    if (MY_POS) {
      map.setCenter(new kakao.maps.LatLng(MY_POS.lat, MY_POS.lng));
      map.setLevel(3);
    } else {
      map.setCenter(new kakao.maps.LatLng(MEET_POINT.lat, MEET_POINT.lng));
    }
  });

  connectWebSocket();
}

// ===== 마커 (디자인 시스템) =====
function makeVehicleMarkerContent() {
  const wrap = document.createElement('div');
  wrap.className = 'marker-combined';
  wrap.innerHTML = `
    <div class="marker-badge vehicle">
      <div class="vehicle-halo"><img src="assets/vehicle_radial_halo.svg" alt=""/></div>
      <img src="assets/ic_driver_marker.svg" alt=""/>
    </div>
    <div class="marker-label">
      <div class="label-title">픽업 차량</div>
      <div class="label-sub">현재 위치</div>
    </div>`;
  return wrap;
}

function makePedestrianMarkerContent() {
  const wrap = document.createElement('div');
  wrap.className = 'marker-combined';
  wrap.innerHTML = `
    <div class="marker-badge pedestrian">
      <img src="assets/ic_pedestrian_marker.svg" alt=""/>
    </div>
    <div class="marker-label">
      <div class="label-title">탑승자</div>
      <div class="label-sub">픽업 지점으로 이동 중</div>
    </div>`;
  return wrap;
}

function makeMeetMarkerContent(subText = '추천 픽업 위치') {
  const wrap = document.createElement('div');
  wrap.className = 'marker-combined';
  wrap.innerHTML = `
    <div class="marker-pickup-badge"><img src="assets/marker_pickup_badge.svg" alt=""/></div>
    <div class="marker-label">
      <div class="label-title">${subText}</div>
      <div class="label-sub" data-role="meet-sub">도보 약 2분</div>
    </div>`;
  return wrap;
}

function drawVehicleMarker() {
  vehicleMarker = new kakao.maps.CustomOverlay({
    position: new kakao.maps.LatLng(VEHICLE_POS.lat, VEHICLE_POS.lng),
    content: makeVehicleMarkerContent(),
    xAnchor: 0.1, yAnchor: 0.5, zIndex: 3,
  });
  vehicleMarker.setMap(map);
}

function drawMeetMarker() {
  const label = (sessionInfo && sessionInfo.pickupName) ? sessionInfo.pickupName : '픽업 지점';
  meetMarker = new kakao.maps.CustomOverlay({
    position: new kakao.maps.LatLng(MEET_POINT.lat, MEET_POINT.lng),
    content: makeMeetMarkerContent(label),
    xAnchor: 0.1, yAnchor: 0.5, zIndex: 4,
  });
  meetMarker.setMap(map);
}

function drawMeMarker() {
  if (meMarker || !MY_POS) return;
  meMarker = new kakao.maps.CustomOverlay({
    position: new kakao.maps.LatLng(MY_POS.lat, MY_POS.lng),
    content: makePedestrianMarkerContent(),
    xAnchor: 0.1, yAnchor: 0.5, zIndex: 2,
  });
  meMarker.setMap(map);
}

// ===== WebSocket =====
let ws = null;
let wsReconnectTimer = null;

function connectWebSocket() {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  const url = `${scheme}://${location.host}/ws/pedestrian`;
  try { ws?.close(); } catch {}
  ws = new WebSocket(url);
  ws.addEventListener('message', (ev) => {
    try { handleWsMessage(JSON.parse(ev.data)); } catch {}
  });
  ws.addEventListener('close', scheduleReconnect);
  ws.addEventListener('error', () => {});
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
      break;
    case 'eta':
      updateEta(msg.minutes);
      break;
  }
}
function updateEta(minutes) {
  const etaEl = document.querySelector('.eta-value');
  if (etaEl && minutes != null) etaEl.textContent = `${minutes}분`;
}

// ===== Geolocation =====
let geoWatchId = null;

const ctaBtn   = document.querySelector('.cta-btn');
const ctaLabel = ctaBtn.querySelector('span');
const ctaIcon  = ctaBtn.querySelector('.cta-icon');

ctaBtn.addEventListener('click', startSharingLocation);

function setBtnSharing() {
  ctaBtn.classList.add('sharing');
  ctaBtn.disabled = true;
  ctaIcon.src = 'assets/ic_pin_check_sharing.svg';
  ctaLabel.textContent = '내 위치 공유 중';
}

function setBtnDefault(label) {
  ctaBtn.classList.remove('sharing');
  ctaBtn.disabled = false;
  ctaIcon.src = 'assets/ic_send.svg';
  ctaLabel.textContent = label ?? '운전자에게 내 위치 보내기';
}

function startSharingLocation() {
  if (!('geolocation' in navigator)) {
    showGeoError('이 브라우저는 위치 기능을 지원하지 않습니다.');
    return;
  }
  if (geoWatchId !== null) return;

  ctaLabel.textContent = '위치 조회 중…';
  ctaBtn.disabled = true;

  geoWatchId = navigator.geolocation.watchPosition(
    (pos) => {
      const { latitude: lat, longitude: lng } = pos.coords;
      MY_POS = { lat, lng };
      drawMeMarker();
      meMarker?.setPosition(new kakao.maps.LatLng(lat, lng));

      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'pedestrian_location', lat, lng }));
        setBtnSharing();
      } else {
        ctaLabel.textContent = '연결 대기 중…';
      }
    },
    (err) => {
      setBtnDefault('다시 시도');
      showGeoError(geoErrorDetail(err));
      geoWatchId = null;
    },
    { enableHighAccuracy: true, maximumAge: 1000, timeout: 15000 }
  );
}

function geoErrorDetail(err) {
  switch (err.code) {
    case 1: return '위치 권한이 거부되었습니다.\n설정 > 위치 서비스 허용 후 재시도';
    case 2: return '위치 정보를 가져올 수 없음.\nWi-Fi/GPS 가 꺼져있는지 확인';
    case 3: return '위치 조회 시간 초과 — 다시 시도';
    default: return err.message || '알 수 없는 오류';
  }
}

function showGeoError(msg) {
  let banner = document.querySelector('.geo-error-banner');
  if (!banner) {
    banner = document.createElement('div');
    banner.className = 'geo-error-banner';
    document.querySelector('.sheet').prepend(banner);
  }
  banner.textContent = msg;
}
