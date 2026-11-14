package ai.pleos.pickup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RedeemRequest", description = "토큰 → 쿠키 교환 요청")
public record RedeemRequest(
        @Schema(description = "POST /api/sessions 로 받은 1회용 토큰",
                example = "4Ao9hsf6pBDR9Pz8", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String token
) {}
