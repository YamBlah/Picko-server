package ai.pleos.pickup.controller;

import ai.pleos.pickup.domain.PickupSession;
import ai.pleos.pickup.dto.CreateSessionRequest;
import ai.pleos.pickup.dto.RedeemRequest;
import ai.pleos.pickup.dto.SessionResponse;
import ai.pleos.pickup.dto.WebSessionView;
import ai.pleos.pickup.service.PickupSessionService;
import ai.pleos.pickup.service.SmsSender;
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

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    public static final String COOKIE_NAME = "PSID";

    private final PickupSessionService service;
    private final SmsSender sms;

    @Value("${app.sms-template}")
    private String smsTemplate;

    public SessionController(PickupSessionService service, SmsSender sms) {
        this.service = service;
        this.sms = sms;
    }

    /**
     * 차량(Pleos 앱)이 호출 → 세션 생성 + (전화번호 있으면) SMS 자동 발송.
     * 전화번호 없거나 쿨SMS 설정 안 된 경우엔 webUrl 수동 전달용으로 응답에 포함.
     */
    @PostMapping
    public SessionResponse create(@RequestBody @Valid CreateSessionRequest req) {
        PickupSession session = service.create(req);
        String webUrl = service.buildWebUrl(session.getToken());

        boolean smsSent = false;
        String smsDetail = "no_phone";
        if (req.passengerPhone() != null && !req.passengerPhone().isBlank()) {
            String message = smsTemplate.replace("{url}", webUrl);
            SmsSender.Result r = sms.send(req.passengerPhone(), message);
            smsSent = r.sent();
            smsDetail = r.detail();
        }

        return SessionResponse.from(session, webUrl, smsSent, smsDetail);
    }

    /** 웹이 SMS 링크를 열 때 호출 → 쿠키 발급, 토큰 폐기 */
    @PostMapping("/redeem")
    public WebSessionView redeem(@RequestBody @Valid RedeemRequest req,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        PickupSession session = service.redeem(req.token());
        writeSessionCookie(request, response, session.getWebSessionId(),
                           Duration.between(java.time.Instant.now(), session.getWebSessionExpiresAt()));
        return WebSessionView.from(session);
    }

    /** 새로고침 등으로 다시 로드될 때 쿠키로 세션 조회 */
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
