package ai.pleos.pickup.dto;

import jakarta.validation.constraints.NotBlank;

public record RedeemRequest(@NotBlank String token) {}
