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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카카오 로컬 카테고리 검색 ( /v2/local/search/category.json ).
 * 픽업에 좋은 "랜드마크" POI 를 가져오기 위한 용도.
 *
 * 카테고리 코드 참고:
 *   CS2 = 편의점,  CE7 = 카페,  SW8 = 지하철역,  BK9 = 은행,  MT1 = 대형마트
 */
@Component
public class KakaoPoiClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoPoiClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${kakao.rest-api-key}")
    private String restApiKey;

    public record Poi(String id, String name, String category, double lat, double lng, String address) {}

    /**
     * 지정 카테고리들을 center 주변 radiusM 내에서 조회해 (place_id 기준) 중복 제거.
     * 카테고리당 1 API 호출. categories 3개면 호출 3번.
     */
    public List<Poi> searchAround(double centerLat, double centerLng, int radiusM, List<String> categories) {
        Map<String, Poi> dedupe = new HashMap<>();
        for (String cat : categories) {
            try {
                String url = "https://dapi.kakao.com/v2/local/search/category.json"
                        + "?category_group_code=" + cat
                        + "&x=" + centerLng + "&y=" + centerLat
                        + "&radius=" + Math.min(radiusM, 20000)
                        + "&sort=distance&size=15";
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", "KakaoAK " + restApiKey)
                        .timeout(Duration.ofSeconds(5)).GET().build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    log.warn("[Kakao POI] {} HTTP {}", cat, res.statusCode());
                    continue;
                }
                JsonNode docs = mapper.readTree(res.body()).path("documents");
                for (JsonNode d : docs) {
                    String id = d.path("id").asText();
                    if (dedupe.containsKey(id)) continue;
                    dedupe.put(id, new Poi(
                            id,
                            d.path("place_name").asText(""),
                            d.path("category_group_code").asText(cat),
                            Double.parseDouble(d.path("y").asText("0")),
                            Double.parseDouble(d.path("x").asText("0")),
                            d.path("road_address_name").asText(d.path("address_name").asText(""))
                    ));
                }
            } catch (Exception e) {
                log.warn("[Kakao POI] {} error: {}", cat, e.getMessage());
            }
        }
        return new ArrayList<>(dedupe.values());
    }
}
