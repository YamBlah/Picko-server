package ai.pleos.pickup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSessionRequest(
        @NotNull Double pickupLat,
        @NotNull Double pickupLng,
        String pickupName,
        @NotBlank String vehicleModel,
        @NotBlank String vehiclePlate,
        String vehicleColor,
        /** 선택: 있으면 서버가 쿨SMS 로 링크 발송 */
        String passengerPhone
) {}
