package com.merchanthub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI merchantHubOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("MerchantHub API")
                .version("0.1.0")
                .description("Multi-tenant e-commerce analytics & inventory platform.")
                .license(new License().name("MIT")));
    }
}
