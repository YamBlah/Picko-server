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
 * - vertexes + summary(distance/duration) 파싱
 * - 실패 시 직선 보간 폴백
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

    /**
     * @param path        경로상 좌표 리스트 (lng,lat 아니라 lat,lng 순서)
     * @param distanceM   총 이동 거리 (미터)
     * @param durationSec 총 소요 시간 (초, 카카오 실측 기반)
     */
    public record RouteResult(List<LatLng> path, double distanceM, double durationSec) {
        public boolean isEmpty() { return path.isEmpty(); }
        public double avgSpeedMpm() {
            return durationSec > 0 && distanceM > 0
                    ? distanceM / (durationSec / 60.0)
                    : 400.0;  // 폴백: 서울 도심 평균 ~24km/h
        }
    }

    public RouteResult fetchRoute(LatLng origin, LatLng destination) {
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
            RouteResult r = parse(res.body());
            return r.isEmpty() ? linearFallback(origin, destination) : r;
        } catch (Exception e) {
            log.warn("[Kakao Directions] error: {}", e.getMessage());
            return linearFallback(origin, destination);
        }
    }

    private RouteResult parse(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode routes = root.path("routes");
        if (!routes.isArray() || routes.isEmpty()) return new RouteResult(List.of(), 0, 0);

        JsonNode route = routes.get(0);
        double totalDist = route.path("summary").path("distance").asDouble(0);
        double totalDur  = route.path("summary").path("duration").asDouble(0);

        List<LatLng> path = new ArrayList<>();
        for (JsonNode section : route.path("sections")) {
            for (JsonNode road : section.path("roads")) {
                JsonNode vertexes = road.path("vertexes");
                for (int i = 0; i + 1 < vertexes.size(); i += 2) {
                    path.add(new LatLng(vertexes.get(i + 1).asDouble(), vertexes.get(i).asDouble()));
                }
            }
        }
        return new RouteResult(path, totalDist, totalDur);
    }

    private RouteResult linearFallback(LatLng o, LatLng d) {
        int steps = 20;
        List<LatLng> p = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            p.add(new LatLng(o.lat() + (d.lat() - o.lat()) * t,
                             o.lng() + (d.lng() - o.lng()) * t));
        }
        double dist = haversineM(o.lat(), o.lng(), d.lat(), d.lng());
        double dur = dist / (400.0 / 60.0);  // 400m/min
        return new RouteResult(p, dist, dur);
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
