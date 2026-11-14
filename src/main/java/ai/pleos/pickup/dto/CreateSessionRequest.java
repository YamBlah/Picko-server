package ai.pleos.pickup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 운전자 앱이 추천 Top1 을 선택한 뒤 세션을 생성할 때 보내는 요청.
 * walk/drive/ETA 는 선택 — 있으면 저장되어 탑승자 웹에 그대로 표시됨.
 */
@Schema(
        name = "CreateSessionRequest",
        description = "세션 생성 요청. 추천 API 결과의 1위 후보를 운전자가 선택한 뒤 그 메타를 그대로 전달하는 구조."
)
public record CreateSessionRequest(
        @Schema(description = "픽업 지점 위도", example = "37.5520", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Double pickupLat,

        @Schema(description = "픽업 지점 경도", example = "126.9180", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Double pickupLng,

        @Schema(description = "픽업 지점 이름 (추천 POI 라벨)", example = "CU 합정역점")
        String pickupName,

        @Schema(description = "픽업 지점 카테고리", example = "편의점",
                allowableValues = {"편의점", "카페", "지하철역", "은행", "경로"})
        String pickupCategory,

        @Schema(description = "목적지에서 픽업 지점까지 도보 거리 (m) - 추천 당시 추정값", example = "182.5")
        Double walkFromDestinationM,

        @Schema(description = "도보 분 환산", example = "2.4")
        Double walkFromDestinationMin,

        @Schema(description = "차량 이동 거리 (m)", example = "1821")
        Double vehicleDistanceM,

        @Schema(description = "차량 도착 예상 (분)", example = "4.5")
        Double vehicleEtaMin,

        @Schema(description = "차량 모델", example = "현대 아반떼", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String vehicleModel,

        @Schema(description = "차량 번호판", example = "123가 4567", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String vehiclePlate,

        @Schema(description = "차량 색상", example = "흰색")
        String vehicleColor,

        @Schema(description = "탑승자 전화번호 (있으면 SOLAPI 로 자동 SMS 발송)",
                example = "01012345678")
        String passengerPhone
) {}
