package ai.pleos.pickup.client;

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
 * 카카오 모빌리티 Directions API 호출 (자동차 길찾기).
 * - vertexes 파싱 후 LatLng 리스트 반환
 * - 실패 시 origin→destination 직선 보간 폴백
 */
@Component
public class KakaoDirectionsClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoDirectionsClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${kakao.rest-api-key}")
    private String restApiKey;

    @Value("${kakao.directions-url}")
    private String directionsUrl;

    public record LatLng(double lat, double lng) {}

    public List<LatLng> fetchRoute(LatLng origin, LatLng destination) {
        String url = "%s?origin=%f,%f&destination=%f,%f&priority=RECOMMEND"
                .formatted(directionsUrl, origin.lng(), origin.lat(), destination.lng(), destination.lat());
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("[Kakao Directions] HTTP {}", res.statusCode());
                return linearFallback(origin, destination);
            }
            List<LatLng> path = parseVertexes(res.body());
            return path.isEmpty() ? linearFallback(origin, destination) : path;
        } catch (Exception e) {
            log.warn("[Kakao Directions] error: {}", e.getMessage());
            return linearFallback(origin, destination);
        }
    }

    private List<LatLng> parseVertexes(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode routes = root.path("routes");
        if (!routes.isArray() || routes.isEmpty()) return List.of();
        JsonNode sections = routes.get(0).path("sections");
        if (!sections.isArray()) return List.of();

        List<LatLng> path = new ArrayList<>();
        for (JsonNode section : sections) {
            for (JsonNode road : section.path("roads")) {
                JsonNode vertexes = road.path("vertexes");
                for (int i = 0; i + 1 < vertexes.size(); i += 2) {
                    path.add(new LatLng(vertexes.get(i + 1).asDouble(), vertexes.get(i).asDouble()));
                }
            }
        }
        return path;
    }

    private List<LatLng> linearFallback(LatLng o, LatLng d) {
        int steps = 20;
        List<LatLng> p = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            p.add(new LatLng(o.lat() + (d.lat() - o.lat()) * t,
                             o.lng() + (d.lng() - o.lng()) * t));
        }
        return p;
    }
}
