#!/usr/bin/env python3
"""
주정차 금지 구역 통합 GeoJSON 생성기.

입력: ~/Downloads 의 공공데이터 JSON들
출력: ../src/main/resources/pickup_exclusions.geojson (Spring Boot 리소스로 바로 들어감)

규칙:
  1. 비상소화장치 — 반경 5m
  2. 교차로/횡단보도 — 교차로(중복제거) 5m, 횡단보도 모두 5m
  3. 버스정류소 — 반경 10m, 한강선착장 제외
  4. 어린이보호구역 — 반경 300m (법규 기준), 주소 지오코딩
  5. 서울 주정차금지 — 반경 = 길이/2 + 20m, 도로명 지오코딩
"""
import json
import time
import requests
from pathlib import Path
from collections import Counter

from pyproj import Transformer

DOWNLOADS = Path.home() / "Downloads"
OUTPUT = Path(__file__).parent.parent / "src/main/resources/pickup_exclusions.geojson"
KAKAO_KEY = "b0664cb48af6552829a377190af6f8db"

tm_to_wgs = Transformer.from_crs("EPSG:5186", "EPSG:4326", always_xy=True)

features = []


def add_point(source, name, lat, lng, radius_m, **extra):
    """SEOUL 범위 검증 포함"""
    if not (37.4 <= lat <= 37.8 and 126.6 <= lng <= 127.3):
        return False
    features.append({
        "type": "Feature",
        "geometry": {"type": "Point", "coordinates": [lng, lat]},
        "properties": {"source": source, "name": name,
                       "radius_m": radius_m, "mode": "circle", **extra}
    })
    return True


_session = requests.Session()
_session.headers.update({"Authorization": f"KakaoAK {KAKAO_KEY}"})

def geocode(query, kind="address"):
    endpoint = "address.json" if kind == "address" else "keyword.json"
    try:
        r = _session.get(f"https://dapi.kakao.com/v2/local/search/{endpoint}",
                         params={"query": query}, timeout=10)
        if r.status_code != 200:
            return None
        docs = r.json().get("documents", [])
        if docs:
            return float(docs[0]["y"]), float(docs[0]["x"])
    except Exception:
        pass
    return None


def progress(cur, total, label):
    print(f"\r  [{label}] {cur}/{total}", end="", flush=True)


# ============================================================
# 1. 비상소화장치 (TM 변환, radius 5m)
# ============================================================
print("[1/5] 비상소화장치...")
path = DOWNLOADS / "서울시 비상소화장치 위치정보-GI_EMERHYD_P.json"
with open(path, encoding="utf-8") as f:
    data = json.load(f)
added = 0
for item in data:
    try:
        x = float(item["x좌표"]); y = float(item["y좌표"])
        lng, lat = tm_to_wgs.transform(x, y)
        if add_point("fire_hydrant", str(item.get("비상소화장치고유번호", ""))[:50],
                     lat, lng, 5):
            added += 1
    except Exception:
        pass
print(f"  → {added}/{len(data)}개 추가")

# ============================================================
# 2. 교차로/횡단보도 (TM 변환)
# ============================================================
print("[2/5] 교차로/횡단보도...")
path = DOWNLOADS / "서울시 교차로 및 횡단보도 시설·위치정보_20260824-교차로 및 횡단보도 시설 위치정보.json"
with open(path, encoding="utf-8") as f:
    data = json.load(f)
intersections = {}   # 교차로관리번호 → (name, lng, lat)
crosswalk_added = 0
for item in data:
    try:
        x = float(item["x좌표"]); y = float(item["y좌표"])
        lng, lat = tm_to_wgs.transform(x, y)
    except Exception:
        continue
    # 횡단보도 — 모두
    if add_point("crosswalk", item.get("교차로명", ""), lat, lng, 5,
                 type=item.get("횡단보도종류", "")):
        crosswalk_added += 1
    # 교차로 — 중복 제거
    iid = str(item.get("교차로관리번호", ""))
    if iid and iid not in intersections:
        intersections[iid] = (item.get("교차로명", ""), lng, lat)
intersection_added = 0
for iid, (name, lng, lat) in intersections.items():
    if add_point("intersection", name, lat, lng, 5, intersection_id=iid):
        intersection_added += 1
print(f"  → 횡단보도 {crosswalk_added}, 교차로 {intersection_added}")

# ============================================================
# 3. 버스정류소 (WGS84, 한강선착장 제외, radius 10m)
# ============================================================
print("[3/5] 버스정류소...")
path = DOWNLOADS / "서울시버스정류소위치정보(20260804)-Data.json"
with open(path, encoding="utf-8") as f:
    data = json.load(f)
added = 0; skipped = 0
for item in data:
    if item.get("정류소타입") == "한강선착장":
        skipped += 1
        continue
    try:
        lng = float(item["x좌표"]); lat = float(item["y좌표"])
        if add_point("bus_stop", item.get("정류소명", ""), lat, lng, 10,
                     node_id=item.get("node_id", ""), stop_type=item.get("정류소타입", "")):
            added += 1
    except Exception:
        pass
print(f"  → {added}개 추가 (한강선착장 {skipped}개 제외)")

# ============================================================
# 4. 어린이보호구역 (지오코딩, radius 300m)
# ============================================================
print("[4/5] 어린이보호구역 지오코딩...")
path = DOWNLOADS / "어린이 보호구역 지정현황(2026.6월말 기준)-어린이보호구역 지정현황.json"
with open(path, encoding="utf-8") as f:
    data = json.load(f)
total = len(data)
success = 0; failed = []
for i, item in enumerate(data, 1):
    progress(i, total, "school_zone")
    gu = item.get("관할_자치구", "")
    addr = f"서울특별시 {gu} {item.get('도로명_주소','')}"
    result = geocode(addr, "address")
    if not result:
        # 시설명 keyword 검색 fallback
        name = item.get("시설명", "")
        if name:
            result = geocode(f"서울 {gu} {name}", "keyword")
    if result:
        lat, lng = result
        if add_point("school_zone", item.get("시설명", ""), lat, lng, 300,
                     facility_type=item.get("시설유형", ""), gu=gu):
            success += 1
    else:
        failed.append(item.get("시설명", ""))
    time.sleep(0.03)
print(f"\n  → {success}/{total} 성공")
if failed:
    print(f"    실패 {len(failed)}개 샘플: {failed[:3]}")

# ============================================================
# 5. 서울 주정차금지 (전국 데이터에서 서울 필터링, radius = 길이/2 + 20m)
# ============================================================
print("[5/5] 서울 주정차금지 (전국 파일에서 서울 필터링)...")
path = DOWNLOADS / "전국주정차금지_지정_구역표준데이터.json"
with open(path, encoding="utf-8") as f:
    nat = json.load(f)
all_seoul = [r for r in nat["records"] if "서울" in r.get("시도명", "")]
print(f"  서울 레코드: {len(all_seoul)}건")
total = len(all_seoul); success = 0; failed = []
for i, rec in enumerate(all_seoul, 1):
    progress(i, total, "no_parking")
    # 길이 파싱 (km → m 변환)
    raw = str(rec.get("금지구역총연장", "0")).strip()
    try:
        v = float(raw)
        length_m = v * 1000 if v < 10 else v
    except Exception:
        length_m = 100
    radius_m = max(30, int(length_m / 2 + 20))
    # 1차: 도로명 address 검색
    addr = f"{rec.get('시도명','')} {rec.get('시군구명','')} {rec.get('도로명','')}".strip()
    result = geocode(addr, "address") if addr else None
    # 2차: 상세위치에서 첫 랜드마크 keyword 검색
    if not result:
        detail = rec.get("상세위치", "")
        for lm in detail.replace("(", " ").replace(")", " ").split("~"):
            lm = lm.strip()
            if len(lm) >= 3:
                result = geocode(f"서울 {rec.get('시군구명','')} {lm}", "keyword")
                if result:
                    break
    if result:
        lat, lng = result
        if add_point("no_parking",
                     rec.get("상세위치", "")[:60],
                     lat, lng, radius_m,
                     road=rec.get("도로명", ""),
                     gu=rec.get("시군구명", ""),
                     length_m=int(length_m)):
            success += 1
    else:
        failed.append(f"{rec.get('시군구명','')} {rec.get('도로명','')}")
    time.sleep(0.03)
print(f"\n  → {success}/{total} 성공")
if failed:
    print(f"    실패 {len(failed)}개 샘플: {failed[:3]}")

# ============================================================
# 저장
# ============================================================
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_text(json.dumps({
    "type": "FeatureCollection",
    "features": features
}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

print()
print(f"✅ 완성: {OUTPUT}")
print(f"총 features: {len(features)}")
cnt = Counter(f["properties"]["source"] for f in features)
for src, n in cnt.most_common():
    print(f"  {src}: {n:>6}")
print(f"파일 크기: {OUTPUT.stat().st_size / 1024 / 1024:.1f} MB")
