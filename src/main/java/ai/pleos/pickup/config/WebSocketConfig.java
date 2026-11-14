package ai.pleos.pickup.config;

import ai.pleos.pickup.ws.PedestrianWebSocketHandler;
import ai.pleos.pickup.ws.VehicleWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VehicleWebSocketHandler vehicleHandler;
    private final PedestrianWebSocketHandler pedestrianHandler;

    public WebSocketConfig(VehicleWebSocketHandler vehicleHandler,
                           PedestrianWebSocketHandler pedestrianHandler) {
        this.vehicleHandler = vehicleHandler;
        this.pedestrianHandler = pedestrianHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(vehicleHandler, "/ws/vehicle")
                .setAllowedOriginPatterns("*");
        registry.addHandler(pedestrianHandler, "/ws/pedestrian")
                .setAllowedOriginPatterns("*");
    }
}
