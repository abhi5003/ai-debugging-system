package com.aidbg.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("ragServiceClient")
    public WebClient ragServiceClient(
            @Value("${rag.service.url:http://localhost:8000}") String url) {
        return WebClient.builder()
            .baseUrl(url)
            .defaultHeader("Content-Type", "application/json")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    }

    @Bean("dynatraceClient")
    public WebClient dynatraceClient(
            @Value("${dynatrace.base-url}") String baseUrl,
            @Value("${dynatrace.api-token}") String token) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Api-Token " + token)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Bean("serviceNowClient")
    public WebClient serviceNowClient(
            @Value("${servicenow.base-url}") String baseUrl,
            @Value("${servicenow.username}") String username,
            @Value("${servicenow.password}") String password) {
        String credentials = java.util.Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes());
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Basic " + credentials)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    }
}
