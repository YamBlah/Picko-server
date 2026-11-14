package ai.pleos.pickup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "CancelRequest", description = "운전자가 세션을 취소할 때 보내는 요청")
public record CancelRequest(
        @Schema(description = "POST /api/sessions 로 받은 세션 토큰",
                example = "4Ao9hsf6pBDR9Pz8", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String token,

        @Schema(description = "취소 사유 (선택). 로그/통계용", example = "driver_canceled")
        String reason
) {}
