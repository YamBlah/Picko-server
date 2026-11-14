package ai.pleos.pickup.rec;

import ai.pleos.pickup.client.KakaoDirectionsClient.LatLng;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        summary = "픽업 위치 추천 Top N (GET)",
        description = """
            차량 현재 위치(v) + 운전자 지정 목적지(d) 기준 Top N.

            처리:
            1. Kakao 모빌리티로 차량 경로 조회 → 80m 간격 샘플링
            2. 목적지 주변 400m POI (편의점/카페/지하철역/은행) 조회
            3. 금지 구역(소화장치/교차로/횡단보도/버스정류소/어린이보호구역/주정차금지) 겹치는 지점 플래그
            4. 점수: walkMin + 0.3*detourMin - poiBonus (낮을수록 좋음)
            5. Top K (= limit*2) 에 OSRM 도보 라우팅으로 정확한 walkM 재계산 후 재정렬
            6. 비금지 우선 → 점수 오름차순 → limit 개 반환

            테스트 좌표:
            - 홍대입구 → 합정 : v=(37.5567, 126.9236) d=(37.5499, 126.9138)
            - 선릉 → 강남     : v=(37.5042, 127.0485) d=(37.4980, 127.0280)
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
        return wrap(recommender.recommend(vLat, vLng, dLat, dLng, limit, null));
    }

    @Hidden   // Swagger UI 에서 숨김. 엔드포인트는 그대로 동작.
    @PostMapping("/recommend")
    public Map<String, Object> recommendPost(@RequestBody @Valid RecommendRequest req) {
        List<LatLng> externalRoute = null;
        if (req.vehicleRoute() != null && !req.vehicleRoute().isEmpty()) {
            externalRoute = req.vehicleRoute().stream()
                    .map(p -> new LatLng(p.lat(), p.lng()))
                    .toList();
        }
        int lim = req.limit() == null ? 3 : req.limit();
        return wrap(recommender.recommend(req.vLat(), req.vLng(), req.dLat(), req.dLng(), lim, externalRoute));
    }

    private Map<String, Object> wrap(List<PickupCandidate> result) {
        return Map.of("count", result.size(), "recommendations", result);
    }
}
