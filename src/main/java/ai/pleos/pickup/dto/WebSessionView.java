package ai.pleos.pickup.dto;

import ai.pleos.pickup.domain.PickupSession;
import ai.pleos.pickup.domain.SessionStatus;

/**
 * 웹(보행자)이 받는 응답. 토큰·webUrl 같은 민감 정보는 포함하지 않음.
 */
public record WebSessionView(
        SessionStatus status,
        double pickupLat,
        double pickupLng,
        String pickupName,
        String vehicleModel,
        String vehiclePlate,
        String vehicleColor
) {
    public static WebSessionView from(PickupSession s) {
        return new WebSessionView(
                s.getStatus(),
                s.getPickupLat(), s.getPickupLng(), s.getPickupName(),
                s.getVehicleModel(), s.getVehiclePlate(), s.getVehicleColor()
        );
    }
}
