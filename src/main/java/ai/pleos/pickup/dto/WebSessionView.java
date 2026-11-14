package ai.pleos.pickup.dto;

import ai.pleos.pickup.domain.PickupSession;
import ai.pleos.pickup.domain.SessionStatus;

/**
 * 웹(보행자)이 받는 응답. 토큰·webUrl·SMS·전화번호 같은 민감/운영 정보는 제외.
 */
public record WebSessionView(
        SessionStatus status,

        double pickupLat,
        double pickupLng,
        String pickupName,
        String pickupCategory,
        Double walkFromDestinationM,
        Double walkFromDestinationMin,
        Double vehicleDistanceM,
        Double vehicleEtaMin,

        String vehicleModel,
        String vehiclePlate,
        String vehicleColor
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
