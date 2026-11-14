package ai.pleos.pickup.exclusion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주정차 금지 구역 로드/조회.
 * - 시작 시 pickup_exclusions.geojson 을 메모리에 올리고 R-tree (STRtree) 인덱스를 만든다.
 * - 조회는 평균 O(log n). 수만 건에서도 1ms 미만.
 */
@Service
public class ExclusionZoneService {

    private static final Logger log = LoggerFactory.getLogger(ExclusionZoneService.class);
    private static final String RESOURCE = "pickup_exclusions.geojson";

    /** 위도 1도 ≈ 111,320 m */
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;
    /** 지구 반지름 (m) */
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private List<ExclusionZone> zones = List.of();
    private STRtree index;

    @PostConstruct
    void load() throws IOException {
        var mapper = new ObjectMapper();
        try (InputStream is = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode root = mapper.readTree(is);
            JsonNode features = root.path("features");

            List<ExclusionZone> list = new ArrayList<>(features.size());
            for (JsonNode f : features) {
                JsonNode coords = f.path("geometry").path("coordinates");
                if (!coords.isArray() || coords.size() < 2) continue;
                double lng = coords.get(0).asDouble();
                double lat = coords.get(1).asDouble();
                JsonNode p = f.path("properties");
                list.add(new ExclusionZone(
                        p.path("source").asText(""),
                        p.path("name").asText(""),
                        lat, lng,
                        p.path("radius_m").asDouble(5)
                ));
            }
            this.zones = list;
            buildIndex();
            log.info("[Exclusion] loaded {} zones, bySource={}",
                    list.size(), countBySource());
        }
    }

    private void buildIndex() {
        STRtree tree = new STRtree();
        for (ExclusionZone z : zones) {
            Envelope env = boundingBoxOf(z);
            tree.insert(env, z);
        }
        tree.build();
        this.index = tree;
    }

    /** 반경 포함 bounding box (경도 범위는 위도에 따라 축소) */
    private static Envelope boundingBoxOf(ExclusionZone z) {
        double dLat = z.radiusM() / METERS_PER_DEGREE_LAT;
        double dLng = z.radiusM() / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(z.lat())));
        return new Envelope(
                z.lng() - dLng, z.lng() + dLng,
                z.lat() - dLat, z.lat() + dLat
        );
    }

    /** 이 지점이 어떤 금지 구역이라도 포함되면 true */
    @SuppressWarnings("unchecked")
    public boolean isExcluded(double lat, double lng) {
        List<ExclusionZone> candidates = index.query(new Envelope(lng, lng, lat, lat));
        for (ExclusionZone z : candidates) {
            if (haversineM(lat, lng, z.lat(), z.lng()) <= z.radiusM()) return true;
        }
        return false;
    }

    /** 이 지점이 속한 모든 금지 구역 반환 (디버깅/UI용) */
    @SuppressWarnings("unchecked")
    public List<ExclusionZone> findContaining(double lat, double lng) {
        List<ExclusionZone> candidates = index.query(new Envelope(lng, lng, lat, lat));
        return candidates.stream()
                .filter(z -> haversineM(lat, lng, z.lat(), z.lng()) <= z.radiusM())
                .toList();
    }

    /** bbox 안의 모든 금지 구역 반환 (지도에 한 번에 그릴 때용) */
    @SuppressWarnings("unchecked")
    public List<ExclusionZone> findInBox(double minLat, double minLng, double maxLat, double maxLng) {
        List<ExclusionZone> candidates = index.query(new Envelope(minLng, maxLng, minLat, maxLat));
        return candidates;
    }

    public int size() { return zones.size(); }

    public Map<String, Long> countBySource() {
        return zones.stream().collect(Collectors.groupingBy(ExclusionZone::source, Collectors.counting()));
    }

    /** Haversine 거리 (m) */
    private static double haversineM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
