package ai.pleos.pickup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * 쿨SMS v4 발송. 환경변수(코드설정)로 API 키/시크릿/발신번호 세팅.
 * 설정 안 되어 있으면 로그만 찍고 성공 반환 (개발 중 데모 단절 방지).
 *
 * 설정 예:
 *   COOLSMS_API_KEY=NCSXXXXXXX
 *   COOLSMS_API_SECRET=xxxxxxxxxx
 *   COOLSMS_SENDER_PHONE=01012345678   (쿨SMS 콘솔에서 사전 등록된 발신번호)
 */
@Service
public class SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SmsSender.class);
    private static final String ENDPOINT = "https://api.coolsms.co.kr/messages/v4/send";
    private static final SecureRandom RNG = new SecureRandom();

    @Value("${coolsms.api-key:}")        private String apiKey;
    @Value("${coolsms.api-secret:}")     private String apiSecret;
    @Value("${coolsms.sender-phone:}")   private String senderPhone;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public record Result(boolean sent, String detail) {}

    public Result send(String toPhone, String text) {
        if (apiKey.isBlank() || apiSecret.isBlank() || senderPhone.isBlank()) {
            log.info("[SMS mock] (coolsms not configured) to={} text={}", toPhone, text);
            return new Result(false, "sms_not_configured");
        }
        try {
            String date = Instant.now().toString();
            String salt = randomSalt(16);
            String signature = hmacSha256Hex(apiSecret, date + salt);
            String auth = "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s"
                    .formatted(apiKey, date, salt, signature);

            String body = mapper.writeValueAsString(Map.of(
                    "message", Map.of(
                            "to", toPhone.replaceAll("[^0-9]", ""),
                            "from", senderPhone.replaceAll("[^0-9]", ""),
                            "text", text
                    )
            ));

            HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .header("Authorization", auth)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                log.info("[SMS sent] to={} body={}", toPhone, truncate(res.body()));
                return new Result(true, res.body());
            } else {
                log.warn("[SMS failed] HTTP {} body={}", res.statusCode(), truncate(res.body()));
                return new Result(false, "http_" + res.statusCode());
            }
        } catch (Exception e) {
            log.warn("[SMS error] {}", e.getMessage());
            return new Result(false, e.getMessage());
        }
    }

    private static String hmacSha256Hex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String randomSalt(int len) {
        byte[] b = new byte[len];
        RNG.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static String truncate(String s) {
        return s == null ? "null" : s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
