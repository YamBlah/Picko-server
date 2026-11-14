package ai.pleos.pickup.dto;

import ai.pleos.pickup.domain.PickupSession;
import ai.pleos.pickup.domain.SessionStatus;

/**
 * 차량 앱(POST /api/sessions)이 받는 응답.
 * 세션 전체 정보 + webUrl + SMS 상태.
 */
public record SessionResponse(
        String token,
        SessionStatus status,

        // 픽업 지점
        double pickupLat,
        double pickupLng,
        String pickupName,
        String pickupCategory,
        Double walkFromDestinationM,
        Double walkFromDestinationMin,
        Double vehicleDistanceM,
        Double vehicleEtaMin,

        // 차량 정보
        String vehicleModel,
        String vehiclePlate,
        String vehicleColor,

        // 링크 + SMS
        String webUrl,
        boolean smsSent,
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
