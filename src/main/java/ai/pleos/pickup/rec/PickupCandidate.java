package ai.pleos.pickup.rec;

import java.util.List;

/**
 * 추천 후보 지점.
 * - 추천 단계에서는 "탑승자 좌표" 를 모르므로, 모든 보행 관련 지표는 "목적지(destination) 대비" 기준.
 * - 실제 세션이 시작되면 탑승자 실시간 좌표 기반으로 ETA가 다시 계산됨.
 * - score 가 낮을수록 좋음.
 * - inExcludedZone=true → 금지 구역 안이지만 "달리 선택지 없을 때의 폴백".
 */
public record PickupCandidate(
        double lat,
        double lng,
        String source,                   // route_sample | poi_CS2 | poi_CE7 | poi_SW8 | poi_BK9
        String label,                    // 사람이 읽을 이름 (POI 이름 또는 "경로상 지점")
        String category,                 // "편의점" | "카페" | "지하철역" | "은행" | "경로"
        double walkFromDestinationM,     // 목적지 ↔ 추천지점 (탑승자가 대략 걸어야 하는 거리)
        double walkFromDestinationMin,
        double vehicleDistanceM,         // 차량 이동 거리 (경로 상)
        double vehicleEtaMin,
        double score,
        boolean inExcludedZone,
        List<String> excludedReasons,    // 겹친 금지 구역 source:name (최대 3)
        List<String> reasons             // 점수 디버깅용
) {}
