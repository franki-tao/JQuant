package jquant.service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jquantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("JQuant Pricing & Analytics API")
                        .version("v1")
                        .description("Extensible REST & OpenAPI service layer powered by JQuanlib (Java 17 QuantLib port). "
                                + "Designed for interactive bond analytics, multi-engine option comparison, and future curve/CMS modules.")
                        .license(new License().name("QuantLib-compatible")));
    }
}
