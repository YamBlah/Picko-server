package ai.pleos.pickup.ws;

import ai.pleos.pickup.controller.SessionController;
import ai.pleos.pickup.domain.PickupSession;
import ai.pleos.pickup.service.PickupSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.UUID;

/**
 * 웹(보행자) 전용 WS.
 * 접속: ws://host/ws/pedestrian  (쿠키 PSID 로 인증)
 * 2단계-A 에서는 수신만. 2단계-B 에서 보행자 위치 송신 추가 예정.
 */
@Component
public class PedestrianWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PedestrianWebSocketHandler.class);
    private static final String ATTR_SESSION_ID = "pickupSessionId";

    private final PickupSessionService service;
    private final SessionChannelRegistry registry;

    public PedestrianWebSocketHandler(PickupSessionService service, SessionChannelRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
        String psid = readCookie(ws, SessionController.COOKIE_NAME);
        if (psid == null) {
            ws.close(CloseStatus.POLICY_VIOLATION.withReason("no_cookie"));
            return;
        }
        PickupSession session;
        try {
            session = service.findByWebSessionId(psid);
        } catch (RuntimeException e) {
            ws.close(CloseStatus.POLICY_VIOLATION.withReason(e.getMessage()));
            return;
        }
        ws.getAttributes().put(ATTR_SESSION_ID, session.getId());
        registry.bindPedestrian(session.getId(), ws);
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
        UUID sessionId = (UUID) ws.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId == null) return;
        // 2단계-B 에서 보행자 위치를 차량에 중계할 예정
        registry.sendToVehicle(sessionId, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        UUID sessionId = (UUID) ws.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId != null) registry.unbindPedestrian(sessionId, ws);
        log.debug("[WS pedestrian] closed status={}", status);
    }

    private static String readCookie(WebSocketSession ws, String name) {
        HttpHeaders headers = ws.getHandshakeHeaders();
        List<String> cookies = headers.get(HttpHeaders.COOKIE);
        if (cookies == null) return null;
        for (String header : cookies) {
            for (String pair : header.split(";")) {
                String p = pair.trim();
                if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
            }
        }
        return null;
    }
}
