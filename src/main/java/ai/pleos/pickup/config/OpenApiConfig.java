package ai.pleos.pickup.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pickupOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Picko API")
                        .description("차량 ↔ 탑승자 실시간 위치 공유 + 픽업 위치 추천 서비스")
                        .version("v0.1"));
    }
}
