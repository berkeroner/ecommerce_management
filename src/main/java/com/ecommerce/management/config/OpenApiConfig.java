package com.ecommerce.management.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ecommerceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Mini E-Ticaret API")
                .version("v1")
                .description("Müşteri, kategori, ürün ve müşteri adresi yönetimi için REST API."));
    }
}
