package ai.pleos.pickup.rec;

import ai.pleos.pickup.client.KakaoDirectionsClient;
import ai.pleos.pickup.client.KakaoDirectionsClient.LatLng;
import ai.pleos.pickup.client.KakaoDirectionsClient.RouteResult;
import ai.pleos.pickup.client.KakaoPoiClient;
import ai.pleos.pickup.client.KakaoPoiClient.Poi;
import ai.pleos.pickup.client.OsrmWalkingClient;
import ai.pleos.pickup.client.OsrmWalkingClient.WalkingRoute;
import ai.pleos.pickup.exclusion.ExclusionZone;
import ai.pleos.pickup.exclusion.ExclusionZoneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 픽업 위치 추천 엔진 (정확도 개선 버전).
 *
 * 변경점:
 *  - 차량 ETA: Kakao Directions 응답의 summary.distance/duration 으로 실제 평균 속도 계산
 *  - 도보 거리/시간: Haversine 로 대략 스코어 → Top K 는 OSRM 실제 길찾기로 교체 후 재정렬
 */
@Service
public class PickupRecommender {

    private static final Logger log = LoggerFactory.getLogger(PickupRecommender.class);

    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double WALK_SPEED_M_PER_MIN = 75.0;
    private static final double SAMPLE_INTERVAL_M = 80.0;
    private static final double MAX_WALK_FROM_DEST_M = 500.0;
    private static final int POI_RADIUS_M = 400;
    private static final List<String> POI_CATEGORIES = List.of("CS2", "CE7", "SW8", "BK9");
    private static final int OSRM_ENRICH_FACTOR = 2;   // top limit*2 를 OSRM 로 정확화

    private static final Map<String, String> CATEGORY_LABEL = Map.of(
            "CS2", "편의점",
            "CE7", "카페",
            "SW8", "지하철역",
            "BK9", "은행",
            "MT1", "대형마트"
    );

    private final KakaoDirectionsClient directions;
    private final KakaoPoiClient poiClient;
    private final OsrmWalkingClient osrm;
    private final ExclusionZoneService exclusions;

    // OSRM 호출 병렬화용 — 최대 8개 동시 (공용 데모 서버 과부하 방지)
    private final ExecutorService osrmPool = Executors.newFixedThreadPool(8);

    public PickupRecommender(KakaoDirectionsClient directions,
                             KakaoPoiClient poiClient,
                             OsrmWalkingClient osrm,
                             ExclusionZoneService exclusions) {
        this.directions = directions;
        this.poiClient = poiClient;
        this.osrm = osrm;
        this.exclusions = exclusions;
    }

    public List<PickupCandidate> recommend(
            double vehicleLat, double vehicleLng,
            double destinationLat, double destinationLng,
            int limit
    ) {
        return recommend(vehicleLat, vehicleLng, destinationLat, destinationLng, limit, null);
    }

    /**
     * @param externalRoute 외부에서 계산한 차량 경로 (ex. 운전자 네비 결과). null 이면 Kakao 호출.
     */
    public List<PickupCandidate> recommend(
            double vehicleLat, double vehicleLng,
            double destinationLat, double destinationLng,
            int limit,
            List<LatLng> externalRoute
    ) {
        // 1) 차량 → 목적지 경로
        RouteResult route;
        if (externalRoute != null && !externalRoute.isEmpty()) {
            // 외부 입력 경로 — 거리 합산 + 기본 속도(400 m/min) 가정
            double dist = 0;
            for (int i = 1; i < externalRoute.size(); i++) {
                dist += haversineM(externalRoute.get(i - 1).lat(), externalRoute.get(i - 1).lng(),
                                   externalRoute.get(i).lat(), externalRoute.get(i).lng());
            }
            route = new RouteResult(externalRoute, dist, dist / 400.0 * 60.0);
            log.info("[Recommend] external route used ({}pts, {}m)", externalRoute.size(), (int) dist);
        } else {
            route = directions.fetchRoute(
                    new LatLng(vehicleLat, vehicleLng),
                    new LatLng(destinationLat, destinationLng)
            );
        }
        if (route.isEmpty()) return List.of();
        double avgSpeed = route.avgSpeedMpm();

        // 2) 경로 샘플 + 목적지 주변 POI
        List<SampledPoint> samples = sampleRoute(route.path(), SAMPLE_INTERVAL_M);
        List<Poi> pois = poiClient.searchAround(destinationLat, destinationLng, POI_RADIUS_M, POI_CATEGORIES);

        // 3) 1차 후보 — Haversine 기반 rough score
        List<PickupCandidate> all = new ArrayList<>(samples.size() + pois.size());
        for (SampledPoint s : samples) {
            all.add(buildFromRouteSample(s, destinationLat, destinationLng, avgSpeed));
        }
        for (Poi p : pois) {
            all.add(buildFromPoi(p, samples, destinationLat, destinationLng, avgSpeed));
        }

        // 4) 거리 필터 + 금지 구역 플래그 + 1차 정렬
        List<PickupCandidate> firstPass = all.stream()
                .filter(c -> c.walkFromDestinationM() <= MAX_WALK_FROM_DEST_M)
                .map(this::flagExclusion)
                .sorted(Comparator.comparing(PickupCandidate::inExcludedZone)
                        .thenComparingDouble(PickupCandidate::score))
                .limit((long) limit * OSRM_ENRICH_FACTOR)
                .toList();

        // 5) Top K 에 대해 OSRM 실측 도보 (병렬)
        LatLng destLL = new LatLng(destinationLat, destinationLng);
        List<CompletableFuture<PickupCandidate>> futures = firstPass.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> enrichWalking(c, destLL), osrmPool))
                .toList();
        List<PickupCandidate> enriched = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        log.info("[Recommend] route={}pts samples={} pois={} 1stPass={} enrichedWithOsrm={} returning={}",
                route.path().size(), samples.size(), pois.size(),
                firstPass.size(), enriched.size(), Math.min(limit, enriched.size()));

        // 6) 재정렬 (OSRM 반영) 후 top limit 반환
        return enriched.stream()
                .sorted(Comparator.comparing(PickupCandidate::inExcludedZone)
                        .thenComparingDouble(PickupCandidate::score))
                .limit(limit)
                .toList();
    }

    // ===== 후보 생성 =====

    private PickupCandidate buildFromRouteSample(SampledPoint s, double destLat, double destLng, double avgSpeed) {
        double walkM = haversineM(destLat, destLng, s.lat, s.lng);
        double walkMin = walkM / WALK_SPEED_M_PER_MIN;
        double driveMin = s.cumDistM / avgSpeed;
        double score = walkMin;  // route_sample: detour=0, poiBonus=0
        return new PickupCandidate(
                s.lat, s.lng, "route_sample", "경로상 지점", "경로",
                walkM, walkMin, s.cumDistM, driveMin,
                0.0, 0.0, 0.0, false,
                score, false, List.of(),
                List.of("walk~%.0fm (추정)".formatted(walkM),
                        "drive=%.1fmin (실측)".formatted(driveMin))
        );
    }

    private PickupCandidate buildFromPoi(Poi p, List<SampledPoint> samples,
                                         double destLat, double destLng, double avgSpeed) {
        SampledPoint closest = samples.stream()
                .min(Comparator.comparingDouble(s -> haversineM(s.lat, s.lng, p.lat(), p.lng())))
                .orElse(samples.get(0));
        double detourM = haversineM(closest.lat, closest.lng, p.lat(), p.lng());
        double vehicleDistM = closest.cumDistM + detourM;
        double detourMin = detourM / avgSpeed;
        double driveMin = vehicleDistM / avgSpeed;

        double walkM = haversineM(destLat, destLng, p.lat(), p.lng());
        double walkMin = walkM / WALK_SPEED_M_PER_MIN;
        double poiBonus = switch (p.category()) {
            case "CS2", "CE7" -> 0.8;
            case "SW8", "BK9" -> 0.3;
            default -> 0.0;
        };
        double score = scoreOf(walkMin, detourMin, poiBonus);

        return new PickupCandidate(
                p.lat(), p.lng(), "poi_" + p.category(), p.name(),
                CATEGORY_LABEL.getOrDefault(p.category(), p.category()),
                walkM, walkMin, vehicleDistM, driveMin,
                detourM, detourMin, poiBonus, false,
                score, false, List.of(),
                List.of("walk~%.0fm (추정)".formatted(walkM),
                        "drive=%.1fmin (실측)".formatted(driveMin),
                        "detour=%.0fm".formatted(detourM))
        );
    }

    // ===== OSRM 보강 =====

    private PickupCandidate enrichWalking(PickupCandidate c, LatLng dest) {
        WalkingRoute wr = osrm.fetchRoute(dest, new LatLng(c.lat(), c.lng()));
        double walkM = wr.distanceM();
        double walkMin = wr.durationSec() / 60.0;
        double newScore = scoreOf(walkMin, c.detourMin(), c.poiBonus());

        List<String> reasons = new ArrayList<>(c.reasons());
        reasons.removeIf(r -> r.startsWith("walk~"));
        reasons.add(0, "walk=%.0fm (OSRM)".formatted(walkM));

        return new PickupCandidate(
                c.lat(), c.lng(), c.source(), c.label(), c.category(),
                walkM, walkMin, c.vehicleDistanceM(), c.vehicleEtaMin(),
                c.detourM(), c.detourMin(), c.poiBonus(), true,
                newScore, c.inExcludedZone(), c.excludedReasons(), reasons
        );
    }

    private static double scoreOf(double walkMin, double detourMin, double poiBonus) {
        return walkMin + 0.3 * detourMin - poiBonus;
    }

    private PickupCandidate flagExclusion(PickupCandidate c) {
        List<ExclusionZone> hits = exclusions.findContaining(c.lat(), c.lng());
        if (hits.isEmpty()) return c;
        List<String> reasons = hits.stream().limit(3)
                .map(z -> z.source() + ":" + z.name())
                .toList();
        return new PickupCandidate(
                c.lat(), c.lng(), c.source(), c.label(), c.category(),
                c.walkFromDestinationM(), c.walkFromDestinationMin(),
                c.vehicleDistanceM(), c.vehicleEtaMin(),
                c.detourM(), c.detourMin(), c.poiBonus(), c.walkFromOsrm(),
                c.score(), true, reasons, c.reasons()
        );
    }

    // ===== 유틸 =====

    private record SampledPoint(double lat, double lng, double cumDistM) {}

    private List<SampledPoint> sampleRoute(List<LatLng> route, double intervalM) {
        List<SampledPoint> out = new ArrayList<>();
        if (route.isEmpty()) return out;
        LatLng prev = route.get(0);
        out.add(new SampledPoint(prev.lat(), prev.lng(), 0));
        double cum = 0, sinceLast = 0;
        for (int i = 1; i < route.size(); i++) {
            LatLng cur = route.get(i);
            double seg = haversineM(prev.lat(), prev.lng(), cur.lat(), cur.lng());
            cum += seg;
            sinceLast += seg;
            if (sinceLast >= intervalM) {
                out.add(new SampledPoint(cur.lat(), cur.lng(), cum));
                sinceLast = 0;
            }
            prev = cur;
        }
        LatLng last = route.get(route.size() - 1);
        SampledPoint tail = out.get(out.size() - 1);
        if (tail.lat != last.lat() || tail.lng != last.lng()) {
            out.add(new SampledPoint(last.lat(), last.lng(), cum));
        }
        return out;
    }

    private static double haversineM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
