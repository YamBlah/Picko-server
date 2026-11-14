package ai.pleos.pickup.ws;

import ai.pleos.pickup.domain.PickupSession;
import ai.pleos.pickup.repository.PickupSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * 차량(Pleos 앱) 전용 WS.
 * 접속: ws://host/ws/vehicle?token={SESSION_TOKEN}
 * 수신 메시지(JSON): {"type":"vehicle_location","lat":..,"lng":..}
 * 수신 시 매칭된 보행자에게 그대로 중계.
 */
@Component
public class VehicleWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VehicleWebSocketHandler.class);
    private static final String ATTR_SESSION_ID = "pickupSessionId";

    private final PickupSessionRepository repo;
    private final SessionChannelRegistry registry;

    public VehicleWebSocketHandler(PickupSessionRepository repo, SessionChannelRegistry registry) {
        this.repo = repo;
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
        String token = extractQueryParam(ws.getUri(), "token");
        if (token == null) {
            ws.close(CloseStatus.POLICY_VIOLATION.withReason("missing_token"));
            return;
        }
        PickupSession session = repo.findByToken(token).orElse(null);
        if (session == null) {
            ws.close(CloseStatus.POLICY_VIOLATION.withReason("invalid_token"));
            return;
        }
        ws.getAttributes().put(ATTR_SESSION_ID, session.getId());
        registry.bindVehicle(session.getId(), ws);
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
        UUID sessionId = (UUID) ws.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId == null) return;
        log.info("[WS vehicle→pedestrian] session={} payload={}", sessionId, message.getPayload());
        registry.sendToPedestrian(sessionId, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        UUID sessionId = (UUID) ws.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId != null) registry.unbindVehicle(sessionId, ws);
        log.debug("[WS vehicle] closed status={}", status);
    }

    private static String extractQueryParam(URI uri, String key) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String pair : uri.getQuery().split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
