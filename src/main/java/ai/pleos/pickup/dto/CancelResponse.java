package ai.pleos.pickup.dto;

import ai.pleos.pickup.domain.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CancelResponse", description = "세션 취소 응답")
public record CancelResponse(
        @Schema(description = "전환된 세션 상태", example = "CANCELED")
        SessionStatus status,

        @Schema(description = "세션 UUID (내부 식별자)", example = "e5b52352-1be3-4cb6-87ae-44d22e6d51c6")
        String sessionId,

        @Schema(description = "보행자 웹에 push 된 WS 메시지 type", example = "session_canceled")
        String broadcast
) {}
