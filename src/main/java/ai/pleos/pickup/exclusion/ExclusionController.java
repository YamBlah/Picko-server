package ai.pleos.pickup.exclusion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "3. Exclusions", description = "주정차 금지 구역 조회 (디버깅/시각화용)")
@RestController
@RequestMapping("/api/exclusions")
public class ExclusionController {

    private final ExclusionZoneService service;

    public ExclusionController(ExclusionZoneService service) {
        this.service = service;
    }

    @Operation(
        summary = "통계",
        description = "메모리에 로드된 전체 금지 구역 수 + 소스별 카운트"
    )
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "total", service.size(),
                "bySource", service.countBySource()
        );
    }

    @Operation(
        summary = "단일 좌표 금지 구역 체크",
        description = "주어진 좌표가 어떤 금지 구역(들)에 겹치는지 반환. 추천 결과 디버깅에 유용."
    )
    @GetMapping("/check")
    public Map<String, Object> check(
            @Parameter(description = "위도") @RequestParam double lat,
            @Parameter(description = "경도") @RequestParam double lng
    ) {
        List<ExclusionZone> hits = service.findContaining(lat, lng);
        return Map.of(
                "lat", lat,
                "lng", lng,
                "excluded", !hits.isEmpty(),
                "hits", hits
        );
    }

    @Operation(
        summary = "bbox 내 금지 구역 전체 조회",
        description = "지도 시각화에 사용. R-tree 인덱스로 수만 건 중 1ms 이내 응답."
    )
    @GetMapping("/in-box")
    public Map<String, Object> inBox(
            @Parameter(description = "최소 위도") @RequestParam double minLat,
            @Parameter(description = "최소 경도") @RequestParam double minLng,
            @Parameter(description = "최대 위도") @RequestParam double maxLat,
            @Parameter(description = "최대 경도") @RequestParam double maxLng
    ) {
        List<ExclusionZone> zones = service.findInBox(minLat, minLng, maxLat, maxLng);
        return Map.of(
                "count", zones.size(),
                "zones", zones
        );
    }
}
