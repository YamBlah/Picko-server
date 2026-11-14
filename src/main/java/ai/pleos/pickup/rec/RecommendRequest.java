package ai.pleos.pickup.rec;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * POST /api/pickup/recommend 요청.
 * vehicleRoute 를 넣으면 Kakao Directions 호출을 스킵하고 그대로 샘플링에 사용.
 */
@Schema(name = "RecommendRequest", description = "POST 추천 요청 — 외부 차량 경로 전달 가능")
public record RecommendRequest(
        @Schema(description = "차량 현재 위도", example = "37.5567", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Double vLat,
        @Schema(description = "차량 현재 경도", example = "126.9236", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Double vLng,
        @Schema(description = "목적지 위도", example = "37.5499", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Double dLat,
        @Schema(description = "목적지 경도", example = "126.9138", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Double dLng,

        @Schema(description = "반환 개수 (기본 3)", example = "5")
        Integer limit,

        @Schema(description = "외부에서 미리 계산한 차량 경로. 비어있으면 Kakao 모빌리티로 자동 조회")
        List<LatLngDto> vehicleRoute
) {
    @Schema(name = "LatLngDto", description = "경로 포인트 좌표")
    public record LatLngDto(
            @Schema(example = "37.5567") double lat,
            @Schema(example = "126.9236") double lng
    ) {}
}
