package ai.pleos.pickup.rec;

import java.util.List;

/**
 * 추천 후보 지점.
 * - walkFromDestination* : 최종 top N 은 OSRM 도보 라우팅 기반 (실제 길), 나머진 Haversine 추정
 * - vehicleEtaMin : Kakao Directions 의 실측 avg speed 기반
 * - score : walkMin + 0.3*detourMin - poiBonus (낮을수록 좋음)
 */
public record PickupCandidate(
        double lat,
        double lng,
        String source,                   // route_sample | poi_CS2 | poi_CE7 | poi_SW8 | poi_BK9
        String label,
        String category,                 // 편의점 | 카페 | 지하철역 | 은행 | 경로
        double walkFromDestinationM,
        double walkFromDestinationMin,
        double vehicleDistanceM,
        double vehicleEtaMin,
        double detourM,                  // 차량이 원 경로에서 벗어나는 거리 (route_sample 은 0)
        double detourMin,
        double poiBonus,                 // POI 선호 보너스 (route_sample 은 0)
        boolean walkFromOsrm,            // true 면 OSRM 실측, false 면 Haversine 추정
        double score,
        boolean inExcludedZone,
        List<String> excludedReasons,
        List<String> reasons
) {}
