package ai.pleos.pickup.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 세션 ID 단위로 차량/보행자 WebSocket 연결을 보관하고,
 * 한쪽에서 받은 메시지를 반대쪽에게 중계한다.
 */
@Component
public class SessionChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionChannelRegistry.class);

    private final Map<UUID, WebSocketSession> vehicleConn = new ConcurrentHashMap<>();
    private final Map<UUID, WebSocketSession> pedestrianConn = new ConcurrentHashMap<>();

    public void bindVehicle(UUID sessionId, WebSocketSession ws) {
        WebSocketSession prev = vehicleConn.put(sessionId, ws);
        closeQuietly(prev);
        log.info("[WS] vehicle bound session={}", sessionId);
    }

    public void bindPedestrian(UUID sessionId, WebSocketSession ws) {
        WebSocketSession prev = pedestrianConn.put(sessionId, ws);
        closeQuietly(prev);
        log.info("[WS] pedestrian bound session={}", sessionId);
    }

    public void unbindVehicle(UUID sessionId, WebSocketSession ws) {
        vehicleConn.remove(sessionId, ws);
        log.info("[WS] vehicle unbound session={}", sessionId);
    }

    public void unbindPedestrian(UUID sessionId, WebSocketSession ws) {
        pedestrianConn.remove(sessionId, ws);
        log.info("[WS] pedestrian unbound session={}", sessionId);
    }

    public void sendToPedestrian(UUID sessionId, String json) {
        WebSocketSession ws = pedestrianConn.get(sessionId);
        if (ws == null) {
            log.warn("[WS] no pedestrian connected for session={}, dropping message", sessionId);
            return;
        }
        send(ws, json);
    }

    public void sendToVehicle(UUID sessionId, String json) {
        send(vehicleConn.get(sessionId), json);
    }

    private void send(WebSocketSession ws, String json) {
        if (ws == null || !ws.isOpen()) return;
        try {
            ws.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("[WS] send failed: {}", e.getMessage());
            closeQuietly(ws);
        }
    }

    private void closeQuietly(WebSocketSession ws) {
        if (ws == null) return;
        try { ws.close(); } catch (IOException ignored) {}
    }
}
