package ai.pleos.pickup.rec;

import ai.pleos.pickup.client.KakaoDirectionsClient;
import ai.pleos.pickup.client.KakaoDirectionsClient.LatLng;
import ai.pleos.pickup.client.KakaoPoiClient;
import ai.pleos.pickup.client.KakaoPoiClient.Poi;
import ai.pleos.pickup.exclusion.ExclusionZone;
import ai.pleos.pickup.exclusion.ExclusionZoneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 픽업 위치 추천 엔진.
 *
 * 입력: 차량 현재 위치 + 운전자가 지정한 "대략적 목적지" (예: "강남역 2번 출구 근처")
 * 출력: 추천 픽업 지점 Top N (랜드마크 이름 포함, 차량 ETA + 목적지로부터 도보 포함)
 *
 * 흐름:
 *   1. 차량 → 목적지 카카오 모빌리티 경로
 *   2. 경로 샘플링 → "경로상 후보"
 *   3. 목적지 주변 POI (편의점/카페/지하철/은행) → "랜드마크 후보"
 *   4. 금지 구역 플래그 추가
 *   5. 정렬: 비금지 우선 → 점수 오름차순
 *
 * 점수 (낮을수록 좋음):
 *   score = walkFromDestinationMin + 0.3 * vehicleDetourMin − poiBonus
 */
@Service
public class PickupRecommender {

    private static final Logger log = LoggerFactory.getLogger(PickupRecommender.class);

    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double WALK_SPEED_M_PER_MIN = 75.0;
    private static final double DRIVE_SPEED_M_PER_MIN = 400.0;
    private static final double SAMPLE_INTERVAL_M = 80.0;
    private static final double MAX_WALK_FROM_DEST_M = 500.0;
    private static final int POI_RADIUS_M = 400;
    private static final List<String> POI_CATEGORIES = List.of("CS2", "CE7", "SW8", "BK9");

    private static final Map<String, String> CATEGORY_LABEL = Map.of(
            "CS2", "편의점",
            "CE7", "카페",
            "SW8", "지하철역",
            "BK9", "은행",
            "MT1", "대형마트"
    );

    private final KakaoDirectionsClient directions;
    private final KakaoPoiClient poiClient;
    private final ExclusionZoneService exclusions;

    public PickupRecommender(KakaoDirectionsClient directions, KakaoPoiClient poiClient,
                             ExclusionZoneService exclusions) {
        this.directions = directions;
        this.poiClient = poiClient;
        this.exclusions = exclusions;
    }

    public List<PickupCandidate> recommend(
            double vehicleLat, double vehicleLng,
            double destinationLat, double destinationLng,
            int limit
    ) {
        // 1) 차량 → 목적지 경로
        List<LatLng> route = directions.fetchRoute(
                new LatLng(vehicleLat, vehicleLng),
                new LatLng(destinationLat, destinationLng)
        );
        if (route.isEmpty()) return List.of();

        // 2) 경로 샘플
        List<SampledPoint> samples = sampleRoute(route, SAMPLE_INTERVAL_M);

        // 3) 목적지 주변 POI
        List<Poi> pois = poiClient.searchAround(destinationLat, destinationLng,
                POI_RADIUS_M, POI_CATEGORIES);

        // 4) 후보 조립
        List<PickupCandidate> all = new ArrayList<>(samples.size() + pois.size());
        for (SampledPoint s : samples) {
            all.add(buildFromRouteSample(s, destinationLat, destinationLng));
        }
        for (Poi p : pois) {
            all.add(buildFromPoi(p, samples, destinationLat, destinationLng));
        }

        // 5) 너무 먼 것 제외 + 금지 구역 플래그
        List<PickupCandidate> enriched = all.stream()
                .filter(c -> c.walkFromDestinationM() <= MAX_WALK_FROM_DEST_M)
                .map(this::flagExclusion)
                .toList();

        long nonExcluded = enriched.stream().filter(c -> !c.inExcludedZone()).count();
        log.info("[Recommend] route={} samples={} pois={} candidates={} nonExcluded={}",
                route.size(), samples.size(), pois.size(), enriched.size(), nonExcluded);

        return enriched.stream()
                .sorted(Comparator.comparing(PickupCandidate::inExcludedZone)
                        .thenComparingDouble(PickupCandidate::score))
                .limit(limit)
                .toList();
    }

    // ===== 후보 생성 =====

    private PickupCandidate buildFromRouteSample(SampledPoint s, double destLat, double destLng) {
        double walkM = haversineM(destLat, destLng, s.lat, s.lng);
        double walkMin = walkM / WALK_SPEED_M_PER_MIN;
        double driveMin = s.cumDistM / DRIVE_SPEED_M_PER_MIN;
        // 경로상 포인트는 detour 0
        double score = walkMin;
        return new PickupCandidate(
                s.lat, s.lng, "route_sample", "경로상 지점", "경로",
                walkM, walkMin,
                s.cumDistM, driveMin,
                score,
                false, List.of(),
                List.of("walkFromDest=%.0fm".formatted(walkM),
                        "drive=%.1fmin".formatted(driveMin))
        );
    }

    private PickupCandidate buildFromPoi(Poi p, List<SampledPoint> samples,
                                         double destLat, double destLng) {
        SampledPoint closest = samples.stream()
                .min(Comparator.comparingDouble(s -> haversineM(s.lat, s.lng, p.lat(), p.lng())))
                .orElse(samples.get(0));
        double detourM = haversineM(closest.lat, closest.lng, p.lat(), p.lng());
        double vehicleDistM = closest.cumDistM + detourM;

        double walkM = haversineM(destLat, destLng, p.lat(), p.lng());
        double walkMin = walkM / WALK_SPEED_M_PER_MIN;
        double driveMin = vehicleDistM / DRIVE_SPEED_M_PER_MIN;
        double detourMin = detourM / DRIVE_SPEED_M_PER_MIN;

        // POI 보너스: 편의점/카페는 쉽게 세울 수 있으니 유리
        double poiBonus = switch (p.category()) {
            case "CS2", "CE7" -> 0.8;
            case "SW8", "BK9" -> 0.3;
            default -> 0.0;
        };
        double score = walkMin + 0.3 * detourMin - poiBonus;

        return new PickupCandidate(
                p.lat(), p.lng(),
                "poi_" + p.category(),
                p.name(),
                CATEGORY_LABEL.getOrDefault(p.category(), p.category()),
                walkM, walkMin,
                vehicleDistM, driveMin,
                score,
                false, List.of(),
                List.of("walkFromDest=%.0fm".formatted(walkM),
                        "drive=%.1fmin".formatted(driveMin),
                        "detour=%.0fm".formatted(detourM))
        );
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
