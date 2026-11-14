package ai.pleos.pickup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 세션 생성 요청. 추천 결과(PickupCandidate)를 운전자가 선택한 뒤 그 데이터를 그대로 넘겨받는 형태.
 * walk/drive/ETA 는 선택 — 있으면 저장 후 탑승자 웹에 보여줌.
 */
public record CreateSessionRequest(
        @NotNull Double pickupLat,
        @NotNull Double pickupLng,
        String pickupName,            // e.g. "CU 역삼강남점"
        String pickupCategory,        // 편의점 | 카페 | 지하철역 | 은행 | 경로

        Double walkFromDestinationM,
        Double walkFromDestinationMin,
        Double vehicleDistanceM,
        Double vehicleEtaMin,

        @NotBlank String vehicleModel,
        @NotBlank String vehiclePlate,
        String vehicleColor,

        /** 있으면 서버가 쿨SMS 로 링크 발송 */
        String passengerPhone
) {}
