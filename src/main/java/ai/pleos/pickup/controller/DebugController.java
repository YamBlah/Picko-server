package ai.pleos.pickup.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 디버그 전용. SOLAPI IP whitelist 등 외부 IP 확인용.
 * 공모전 끝나면 삭제 권장.
 */
@Tag(name = "9. Debug", description = "임시 디버그 엔드포인트")
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @Operation(summary = "서버 outbound IP 확인", description = "이 IP를 SOLAPI 허용 대상에 추가")
    @GetMapping("/outbound-ip")
    public Map<String, Object> outboundIp() {
        HttpClient http = HttpClient.newHttpClient();
        try {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create("https://api.ipify.org"))
                            .timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            return Map.of("ip", res.body().trim());
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}
