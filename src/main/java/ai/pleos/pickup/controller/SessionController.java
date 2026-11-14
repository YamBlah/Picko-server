package ai.pleos.pickup.controller;

import ai.pleos.pickup.domain.PickupSession;
import ai.pleos.pickup.dto.CreateSessionRequest;
import ai.pleos.pickup.dto.RedeemRequest;
import ai.pleos.pickup.dto.SessionResponse;
import ai.pleos.pickup.dto.WebSessionView;
import ai.pleos.pickup.service.PickupSessionService;
import ai.pleos.pickup.service.SmsSender;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.NoSuchElementException;

@Tag(name = "1. Sessions", description = "픽업 세션 생성 / 토큰↔쿠키 교환 / 세션 조회")
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    public static final String COOKIE_NAME = "PSID";

    private final PickupSessionService service;
    private final SmsSender sms;

    @Value("${app.sms-title:}")   private String smsTitle;
    @Value("${app.sms-template}") private String smsTemplate;

    public SessionController(PickupSessionService service, SmsSender sms) {
        this.service = service;
        this.sms = sms;
    }

    @Operation(
        summary = "세션 생성 + SMS 발송",
        description = """
            운전자 앱이 호출. 추천 API(/api/pickup/recommend)로 받은 Top1 후보의 메타(label, category,
            walkFromDestinationM/Min, vehicleDistanceM, vehicleEtaMin)를 그대로 함께 넘기면
            탑승자 웹에 바로 표시됨.

            passengerPhone 이 포함되고 SOLAPI 환경변수가 세팅돼 있으면 자동 SMS 발송.
            아니면 응답의 webUrl 을 운전자가 수동 전달하면 됨.
            """
    )
    @PostMapping
    public SessionResponse create(@RequestBody @Valid CreateSessionRequest req) {
        PickupSession session = service.create(req);
        String webUrl = service.buildWebUrl(session.getToken());

        boolean smsSent = false;
        String smsDetail = "no_phone";
        if (req.passengerPhone() != null && !req.passengerPhone().isBlank()) {
            String message = smsTemplate.replace("{url}", webUrl);
            SmsSender.Result r = sms.send(req.passengerPhone(), smsTitle, message);
            smsSent = r.sent();
            smsDetail = r.detail();
        }

        return SessionResponse.from(session, webUrl, smsSent, smsDetail);
    }

    @Operation(
        summary = "토큰 → 쿠키 교환 (매직 링크)",
        description = """
            탑승자 웹이 ?t=TOKEN 으로 들어오면 자동 호출. 토큰을 1회 소비하면서 세션 쿠키(PSID)를 발급.
            쿠키는 HttpOnly + Secure(HTTPS시) + SameSite=Lax, 4시간 유효.
            이미 사용된 토큰은 400 (already_redeemed) 반환.
            """
    )
    @PostMapping("/redeem")
    public WebSessionView redeem(@RequestBody @Valid RedeemRequest req,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        PickupSession session = service.redeem(req.token());
        writeSessionCookie(request, response, session.getWebSessionId(),
                           Duration.between(java.time.Instant.now(), session.getWebSessionExpiresAt()));
        return WebSessionView.from(session);
    }

    @Operation(
        summary = "쿠키로 세션 조회 (새로고침 복구)",
        description = "PSID 쿠키로 현재 세션 정보 반환. 쿠키 없거나 만료되면 401."
    )
    @GetMapping("/me")
    public WebSessionView me(HttpServletRequest request) {
        String sid = readSessionCookie(request);
        if (sid == null) throw new NoSuchElementException("no_cookie");
        PickupSession session = service.findByWebSessionId(sid);
        return WebSessionView.from(session);
    }

    // ===== 쿠키 유틸 =====
    private void writeSessionCookie(HttpServletRequest req, HttpServletResponse res,
                                    String value, Duration ttl) {
        boolean secure = isHttps(req);
        res.addHeader("Set-Cookie", String.format(
                "%s=%s; Path=/; HttpOnly; SameSite=Lax; Max-Age=%d%s",
                COOKIE_NAME, value, ttl.toSeconds(), secure ? "; Secure" : ""
        ));
    }

    private static boolean isHttps(HttpServletRequest req) {
        if (req.isSecure()) return true;
        String proto = req.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(proto);
    }

    private String readSessionCookie(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    // ===== 에러 핸들링 =====
    @ExceptionHandler(PickupSessionService.RedeemException.class)
    public ResponseEntity<ErrorBody> redeemError(PickupSessionService.RedeemException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorBody> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody(e.getMessage()));
    }

    public record ErrorBody(String reason) {}
}
