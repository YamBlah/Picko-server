package ai.pleos.pickup.dto;

import ai.pleos.pickup.domain.PickupSession;
import ai.pleos.pickup.domain.SessionStatus;

/**
 * 차량 앱(POST /api/sessions)이 받는 응답.
 * - token / webUrl: SMS 발송 되지 않았을 때 수동 전달용
 * - smsSent: 서버가 SMS 를 실제로 보냈는지 여부
 */
public record SessionResponse(
        String token,
        SessionStatus status,
        double pickupLat,
        double pickupLng,
        String pickupName,
        String vehicleModel,
        String vehiclePlate,
        String vehicleColor,
        String webUrl,
        boolean smsSent,
        String smsDetail
) {
    public static SessionResponse from(PickupSession s, String webUrl, boolean smsSent, String smsDetail) {
        return new SessionResponse(
                s.getToken(), s.getStatus(),
                s.getPickupLat(), s.getPickupLng(), s.getPickupName(),
                s.getVehicleModel(), s.getVehiclePlate(), s.getVehicleColor(),
                webUrl, smsSent, smsDetail
        );
    }
}
