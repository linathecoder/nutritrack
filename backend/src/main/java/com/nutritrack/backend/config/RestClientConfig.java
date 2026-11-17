package com.nutritrack.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides a shared {@link RestClient} bean used by services that call external APIs
 * (e.g. Gemini). The OpenFoodFacts / barcode scan flow is NOT affected by this bean.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .build();
    }
}
