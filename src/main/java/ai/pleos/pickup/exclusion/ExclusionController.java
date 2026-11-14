package ai.pleos.pickup.exclusion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 금지 구역 조회/디버깅 엔드포인트.
 * 추천 로직이 아니라 "이 위치가 왜 제외됐는지" 확인용.
 */
@RestController
@RequestMapping("/api/exclusions")
public class ExclusionController {

    private final ExclusionZoneService service;

    public ExclusionController(ExclusionZoneService service) {
        this.service = service;
    }

    /** 총 개수 + 소스별 통계 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "total", service.size(),
                "bySource", service.countBySource()
        );
    }

    /** 단일 좌표가 금지 구역에 포함되는지 */
    @GetMapping("/check")
    public Map<String, Object> check(@RequestParam double lat, @RequestParam double lng) {
        List<ExclusionZone> hits = service.findContaining(lat, lng);
        return Map.of(
                "lat", lat,
                "lng", lng,
                "excluded", !hits.isEmpty(),
                "hits", hits
        );
    }

    /** bbox 내 모든 금지 구역 (지도 시각화용) */
    @GetMapping("/in-box")
    public Map<String, Object> inBox(@RequestParam double minLat, @RequestParam double minLng,
                                     @RequestParam double maxLat, @RequestParam double maxLng) {
        List<ExclusionZone> zones = service.findInBox(minLat, minLng, maxLat, maxLng);
        return Map.of(
                "count", zones.size(),
                "zones", zones
        );
    }
}
