package ai.pleos.pickup.client;

import ai.pleos.pickup.client.KakaoDirectionsClient.LatLng;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OSRM 공개 서버로 도보 길찾기 (foot profile).
 * Kakao 모빌리티 도보 API는 제휴 전용이라 OSRM 로 대체.
 *
 * 기본 base URL: https://routing.openstreetmap.de/routed-foot
 *   → 공용 데모 서버. 트래픽 많거나 상용 서비스엔 자체 OSRM 호스팅 권장.
 */
@Component
public class OsrmWalkingClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmWalkingClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${osrm.walking-url:https://routing.openstreetmap.de/routed-foot}")
    private String baseUrl;

    public record WalkingRoute(List<LatLng> path, double distanceM, double durationSec) {}

    public WalkingRoute fetchRoute(LatLng origin, LatLng destination) {
        String url = "%s/route/v1/foot/%f,%f;%f,%f?overview=full&geometries=geojson"
                .formatted(baseUrl,
                        origin.lng(), origin.lat(),
                        destination.lng(), destination.lat());
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("[OSRM] HTTP {}", res.statusCode());
                return fallback(origin, destination);
            }
            return parse(res.body());
        } catch (Exception e) {
            log.warn("[OSRM] error: {}", e.getMessage());
            return fallback(origin, destination);
        }
    }

    private WalkingRoute parse(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode routes = root.path("routes");
        if (!routes.isArray() || routes.isEmpty()) return null;
        JsonNode r = routes.get(0);
        double distance = r.path("distance").asDouble();
        double duration = r.path("duration").asDouble();
        JsonNode coords = r.path("geometry").path("coordinates");
        List<LatLng> path = new ArrayList<>();
        for (JsonNode c : coords) {
            if (c.isArray() && c.size() >= 2) {
                path.add(new LatLng(c.get(1).asDouble(), c.get(0).asDouble()));
            }
        }
        return new WalkingRoute(path, distance, duration);
    }

    private WalkingRoute fallback(LatLng o, LatLng d) {
        // 직선 보간 (OSRM 실패 시)
        int steps = 10;
        List<LatLng> p = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            p.add(new LatLng(o.lat() + (d.lat() - o.lat()) * t,
                             o.lng() + (d.lng() - o.lng()) * t));
        }
        double dist = haversineM(o.lat(), o.lng(), d.lat(), d.lng());
        double dur = dist / (75.0 / 60.0); // 75m/min = 1.25m/s
        return new WalkingRoute(p, dist, dur);
    }

    private static double haversineM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6_371_000.0 * Math.asin(Math.sqrt(a));
    }
}
