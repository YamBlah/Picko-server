package ai.pleos.pickup.rec;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * POST /api/pickup/recommend 요청.
 * vehicleRoute 가 비어있지 않으면 Kakao 모빌리티 호출 스킵하고 그대로 사용.
 */
public record RecommendRequest(
        @NotNull Double vLat,
        @NotNull Double vLng,
        @NotNull Double dLat,
        @NotNull Double dLng,
        Integer limit,
        /** 외부에서 미리 계산한 차량 경로. null 이면 Kakao Directions 로 자동 조회 */
        List<LatLngDto> vehicleRoute
) {
    public record LatLngDto(double lat, double lng) {}
}
