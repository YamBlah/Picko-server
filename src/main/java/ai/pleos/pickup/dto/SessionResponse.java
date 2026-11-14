package ai.pleos.pickup.dto;

import ai.pleos.pickup.domain.PickupSession;
import ai.pleos.pickup.domain.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 차량 앱(POST /api/sessions)이 받는 응답.
 * 세션 전체 정보 + webUrl (SMS 미발송 시 수동 전달용) + SMS 발송 상태.
 */
@Schema(name = "SessionResponse", description = "세션 생성 응답 — 운전자 앱이 받음")
public record SessionResponse(
        @Schema(description = "SMS 링크에 포함되는 1회용 토큰", example = "4Ao9hsf6pBDR9Pz8")
        String token,

        @Schema(description = "세션 상태", example = "CREATED")
        SessionStatus status,

        @Schema(description = "픽업 지점 위도", example = "37.5520")
        double pickupLat,
        @Schema(description = "픽업 지점 경도", example = "126.9180")
        double pickupLng,
        @Schema(description = "픽업 지점 이름", example = "CU 합정역점")
        String pickupName,
        @Schema(description = "픽업 카테고리", example = "편의점")
        String pickupCategory,
        @Schema(description = "목적지 → 픽업 도보 거리 (m)", example = "182.5")
        Double walkFromDestinationM,
        @Schema(description = "도보 분", example = "2.4")
        Double walkFromDestinationMin,
        @Schema(description = "차량 이동 거리 (m)", example = "1821")
        Double vehicleDistanceM,
        @Schema(description = "차량 도착 예상 (분)", example = "4.5")
        Double vehicleEtaMin,

        @Schema(description = "차량 모델", example = "현대 아반떼")
        String vehicleModel,
        @Schema(description = "차량 번호판", example = "123가 4567")
        String vehiclePlate,
        @Schema(description = "차량 색상", example = "흰색")
        String vehicleColor,

        @Schema(description = "탑승자용 웹 링크 (SMS 발송 안 됐으면 수동으로 전달)",
                example = "https://picko.up.railway.app/?t=4Ao9hsf6pBDR9Pz8")
        String webUrl,

        @Schema(description = "SMS 실제 발송 여부", example = "true")
        boolean smsSent,

        @Schema(description = "SMS 결과 상세: no_phone / sms_not_configured / http_XXX / 성공시 msgId", example = "no_phone")
        String smsDetail
) {
    public static SessionResponse from(PickupSession s, String webUrl, boolean smsSent, String smsDetail) {
        return new SessionResponse(
                s.getToken(), s.getStatus(),
                s.getPickupLat(), s.getPickupLng(), s.getPickupName(), s.getPickupCategory(),
                s.getWalkFromDestinationM(), s.getWalkFromDestinationMin(),
                s.getVehicleDistanceM(), s.getVehicleEtaMin(),
                s.getVehicleModel(), s.getVehiclePlate(), s.getVehicleColor(),
                webUrl, smsSent, smsDetail
        );
    }
}
