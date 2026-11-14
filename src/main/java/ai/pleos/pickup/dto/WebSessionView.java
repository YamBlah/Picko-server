package ai.pleos.pickup.dto;

import ai.pleos.pickup.domain.PickupSession;
import ai.pleos.pickup.domain.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 탑승자 웹이 받는 응답. 토큰/webUrl/SMS/전화번호 같은 민감/운영 정보는 제외.
 */
@Schema(name = "WebSessionView", description = "탑승자 웹용 세션 뷰 (민감 정보 제외)")
public record WebSessionView(
        @Schema(description = "세션 상태", example = "CREATED") SessionStatus status,

        @Schema(description = "픽업 지점 위도", example = "37.5520") double pickupLat,
        @Schema(description = "픽업 지점 경도", example = "126.9180") double pickupLng,
        @Schema(description = "픽업 지점 이름", example = "CU 합정역점") String pickupName,
        @Schema(description = "픽업 카테고리", example = "편의점")  String pickupCategory,
        @Schema(description = "목적지 → 픽업 도보 거리 (m)", example = "182.5") Double walkFromDestinationM,
        @Schema(description = "도보 분", example = "2.4")        Double walkFromDestinationMin,
        @Schema(description = "차량 이동 거리 (m)", example = "1821") Double vehicleDistanceM,
        @Schema(description = "차량 도착 예상 (분)", example = "4.5") Double vehicleEtaMin,

        @Schema(description = "차량 모델", example = "현대 아반떼") String vehicleModel,
        @Schema(description = "차량 번호판", example = "123가 4567") String vehiclePlate,
        @Schema(description = "차량 색상", example = "흰색")        String vehicleColor
) {
    public static WebSessionView from(PickupSession s) {
        return new WebSessionView(
                s.getStatus(),
                s.getPickupLat(), s.getPickupLng(), s.getPickupName(), s.getPickupCategory(),
                s.getWalkFromDestinationM(), s.getWalkFromDestinationMin(),
                s.getVehicleDistanceM(), s.getVehicleEtaMin(),
                s.getVehicleModel(), s.getVehiclePlate(), s.getVehicleColor()
        );
    }
}
