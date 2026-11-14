package ai.pleos.pickup.service;

import ai.pleos.pickup.domain.PickupSession;
import ai.pleos.pickup.dto.CreateSessionRequest;
import ai.pleos.pickup.repository.PickupSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.NoSuchElementException;

@Service
public class PickupSessionService {

    public static final Duration TOKEN_TTL        = Duration.ofHours(2);
    public static final Duration WEB_SESSION_TTL  = Duration.ofHours(4);

    private static final SecureRandom RNG = new SecureRandom();

    private final PickupSessionRepository repo;

    @Value("${app.web-base-url}")
    private String webBaseUrl;

    public PickupSessionService(PickupSessionRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public PickupSession create(CreateSessionRequest req) {
        String token = randomUrlSafe(12);  // 16자 URL-safe
        Instant now = Instant.now();
        PickupSession session = new PickupSession(
                token, now.plus(TOKEN_TTL),
                req.pickupLat(), req.pickupLng(), req.pickupName(),
                req.vehicleModel(), req.vehiclePlate(), req.vehicleColor(),
                req.passengerPhone()
        );
        return repo.save(session);
    }

    /** 토큰 → 쿠키 교환. 만료/이미 사용/없음 → 예외 */
    @Transactional
    public PickupSession redeem(String token) {
        PickupSession session = repo.findByToken(token)
                .orElseThrow(() -> new RedeemException("invalid_token"));

        Instant now = Instant.now();
        if (session.isRedeemed()) throw new RedeemException("already_redeemed");
        if (session.isTokenExpired(now)) throw new RedeemException("expired");

        String webSessionId = randomUrlSafe(32);  // 43자 URL-safe
        session.redeem(webSessionId, now.plus(WEB_SESSION_TTL));
        return session;
    }

    @Transactional(readOnly = true)
    public PickupSession findByWebSessionId(String webSessionId) {
        PickupSession session = repo.findByWebSessionId(webSessionId)
                .orElseThrow(() -> new NoSuchElementException("invalid_cookie"));
        if (!session.isWebSessionValid(Instant.now())) {
            throw new NoSuchElementException("session_expired");
        }
        return session;
    }

    public String buildWebUrl(String token) {
        String base = webBaseUrl == null ? "" : webBaseUrl.trim();
        // Railway UI 등에서 scheme 이 생략돼 저장되기도 하니 보정
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "https://" + base;
        }
        // 끝에 슬래시 중복 방지
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/?t=" + token;
    }

    private String randomUrlSafe(int bytes) {
        byte[] buf = new byte[bytes];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public static class RedeemException extends RuntimeException {
        public RedeemException(String reason) { super(reason); }
    }
}
