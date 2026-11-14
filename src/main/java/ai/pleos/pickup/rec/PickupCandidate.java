package ai.pleos.pickup.rec;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 추천 후보 지점.
 * score 가 낮을수록 좋음. walkFromOsrm=true 는 OSRM 실측, false 는 Haversine 추정.
 */
@Schema(name = "PickupCandidate", description = "추천 후보 지점. 점수 낮을수록 좋음")
public record PickupCandidate(
        @Schema(example = "37.5520") double lat,
        @Schema(example = "126.9180") double lng,

        @Schema(description = "후보 출처", example = "poi_CS2",
                allowableValues = {"route_sample", "poi_CS2", "poi_CE7", "poi_SW8", "poi_BK9"})
        String source,

        @Schema(description = "사람이 읽을 라벨 (POI 이름 등)", example = "CU 합정역점")
        String label,

        @Schema(description = "카테고리", example = "편의점")
        String category,

        @Schema(description = "목적지 → 후보 거리 (m). 최종 top K 는 OSRM 실측, 나머진 Haversine 추정",
                example = "182.5")
        double walkFromDestinationM,

        @Schema(description = "목적지 → 후보 도보 시간 (분)", example = "2.4")
        double walkFromDestinationMin,

        @Schema(description = "차량 이동 거리 (m)", example = "1821")
        double vehicleDistanceM,

        @Schema(description = "차량 도착 예상 (분, Kakao 실측 평균속도 기반)", example = "4.5")
        double vehicleEtaMin,

        @Schema(description = "차량 경로에서 이 후보까지의 추가 우회 거리 (m). route_sample 은 0",
                example = "45.3")
        double detourM,

        @Schema(description = "우회 시간 (분)", example = "0.11")
        double detourMin,

        @Schema(description = "POI 선호 보너스 (편의점/카페 0.8, 지하철/은행 0.3, 경로 0)",
                example = "0.8")
        double poiBonus,

        @Schema(description = "도보 거리가 OSRM 실측이면 true, Haversine 추정이면 false", example = "true")
        boolean walkFromOsrm,

        @Schema(description = "종합 점수 (낮을수록 좋음). walkMin + 0.3*detourMin - poiBonus", example = "2.1")
        double score,

        @Schema(description = "금지 구역 (소화장치/교차로/횡단보도/버스정류소/어린이보호구역/주정차금지) 과 겹치면 true.\n" +
                "모두 금지인 경우의 '덜 나쁜' 폴백 표시용.", example = "false")
        boolean inExcludedZone,

        @Schema(description = "겹치는 금지 구역 source:name (최대 3개)")
        List<String> excludedReasons,

        @Schema(description = "점수 디버깅용 reasons")
        List<String> reasons
) {}
