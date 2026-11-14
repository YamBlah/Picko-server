package ai.pleos.pickup.rec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "2. Recommendation", description = "픽업 위치 추천 엔진")
@RestController
@RequestMapping("/api/pickup")
public class RecommendationController {

    private final PickupRecommender recommender;

    public RecommendationController(PickupRecommender recommender) {
        this.recommender = recommender;
    }

    @Operation(
        summary = "픽업 위치 추천 Top N",
        description = """
            차량 현재 위치(v) + 운전자가 지정한 대략적 목적지(d) 기준으로 Top N 추천.

            동작:
            1. 카카오 모빌리티로 차량 경로 조회 → 80m 간격 샘플링
            2. 목적지 주변 400m POI (편의점/카페/지하철역/은행) 조회
            3. 금지 구역(소화장치/교차로/횡단보도/버스정류소/어린이보호구역/주정차금지) 겹치는 지점 플래그
            4. 점수화: 목적지로부터 도보시간 + 0.3*차량 detour - POI 보너스
            5. 비금지 우선 → 점수 오름차순으로 정렬 후 limit 개 반환

            강남/역삼 테스트:
            - vLat=37.5042, vLng=127.0485 (선릉역)
            - dLat=37.4980, dLng=127.0280 (강남역)
            """
    )
    @GetMapping("/recommend")
    public Map<String, Object> recommend(
            @Parameter(description = "차량 현재 위도") @RequestParam double vLat,
            @Parameter(description = "차량 현재 경도") @RequestParam double vLng,
            @Parameter(description = "운전자가 지정한 목적지 위도") @RequestParam double dLat,
            @Parameter(description = "운전자가 지정한 목적지 경도") @RequestParam double dLng,
            @Parameter(description = "반환할 후보 개수 (기본 3)") @RequestParam(defaultValue = "3") int limit
    ) {
        List<PickupCandidate> result = recommender.recommend(vLat, vLng, dLat, dLng, limit);
        return Map.of(
                "count", result.size(),
                "recommendations", result
        );
    }
}
