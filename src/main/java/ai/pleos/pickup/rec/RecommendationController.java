package ai.pleos.pickup.rec;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pickup")
public class RecommendationController {

    private final PickupRecommender recommender;

    public RecommendationController(PickupRecommender recommender) {
        this.recommender = recommender;
    }

    /**
     * GET /api/pickup/recommend?vLat=..&vLng=..&dLat=..&dLng=..&limit=3
     *
     * 차량 현재 위치 (v) → 운전자가 지정한 대략적 목적지 (d) 기준
     * 픽업 지점 Top N 반환.
     */
    @GetMapping("/recommend")
    public Map<String, Object> recommend(
            @RequestParam double vLat, @RequestParam double vLng,
            @RequestParam double dLat, @RequestParam double dLng,
            @RequestParam(defaultValue = "3") int limit
    ) {
        List<PickupCandidate> result = recommender.recommend(vLat, vLng, dLat, dLng, limit);
        return Map.of(
                "count", result.size(),
                "recommendations", result
        );
    }
}
