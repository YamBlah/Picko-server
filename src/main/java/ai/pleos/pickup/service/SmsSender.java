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
 * SOLAPI (구 CoolSMS) v4 문자 발송.
 * - IP whitelist: SOLAPI 콘솔에서 "클라우드 공급사" → GCP 선택 (Railway = GCP)
 * - HMAC-SHA256 서명 인증
 *
 * 환경변수:
 *   SOLAPI_API_KEY=NCSXXXXXXXX
 *   SOLAPI_API_SECRET=xxxxxxxxxx
 *   SOLAPI_SENDER_PHONE=01012345678   (사전 등록한 발신번호)
 */
@Service
public class SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SmsSender.class);
    private static final String ENDPOINT = "https://api.solapi.com/messages/v4/send";
    private static final SecureRandom RNG = new SecureRandom();

    @Value("${solapi.api-key:}")      private String apiKey;
    @Value("${solapi.api-secret:}")   private String apiSecret;
    @Value("${solapi.sender-phone:}") private String senderPhone;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public record Result(boolean sent, String detail) {}

    public Result send(String toPhone, String text) {
        return send(toPhone, null, text);
    }

    /**
     * @param subject LMS 제목 (null/blank 이면 생략). 메시지가 SMS 로 보내지는 경우 무시됨.
     */
    public Result send(String toPhone, String subject, String text) {
        if (apiKey.isBlank() || apiSecret.isBlank() || senderPhone.isBlank()) {
            log.info("[SMS mock] (solapi not configured) to={} subject={} text={}",
                    toPhone, subject, truncate(text));
            return new Result(false, "sms_not_configured");
        }
        try {
            String date = Instant.now().toString();
            String salt = randomHex(16);
            String signature = hmacSha256Hex(apiSecret, date + salt);
            String auth = "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s"
                    .formatted(apiKey, date, salt, signature);

            java.util.Map<String, Object> message = new java.util.LinkedHashMap<>();
            message.put("to", onlyDigits(toPhone));
            message.put("from", onlyDigits(senderPhone));
            message.put("text", text);
            if (subject != null && !subject.isBlank()) {
                message.put("subject", subject);
            }
            String body = mapper.writeValueAsString(Map.of("message", message));

            HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .header("Authorization", auth)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                log.info("[SMS sent] to={} body={}", toPhone, truncate(res.body()));
                return new Result(true, truncate(res.body()));
            } else {
                log.warn("[SMS failed] HTTP {} body={}", res.statusCode(), truncate(res.body()));
                return new Result(false, "http_" + res.statusCode() + " " + truncate(res.body()));
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

    private static String randomHex(int len) {
        byte[] b = new byte[len];
        RNG.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static String onlyDigits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    private static String truncate(String s) {
        return s == null ? "null" : s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
