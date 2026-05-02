package com.aidbg.service.dispatcher;

import com.aidbg.model.EnrichedIncident;
import com.aidbg.model.IncidentAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class HttpRagAnalysisClient implements RagAnalysisClient {

    private final WebClient ragServiceClient;
    private final Duration analyzeTimeout;
    private final Duration embedTimeout;

    public HttpRagAnalysisClient(
            @Qualifier("ragServiceClient") WebClient ragServiceClient,
            @Value("${rag.service.analyze-timeout-seconds:60}") int analyzeTimeoutSec,
            @Value("${rag.service.embed-timeout-seconds:15}") int embedTimeoutSec) {
        this.ragServiceClient = ragServiceClient;
        this.analyzeTimeout = Duration.ofSeconds(analyzeTimeoutSec);
        this.embedTimeout = Duration.ofSeconds(embedTimeoutSec);
    }

    @Override
    public IncidentAnalysis analyze(EnrichedIncident incident) {
        log.info("Calling RAG service POST /analyze incident={}", incident.getNumber());

        IncidentAnalysis analysis = ragServiceClient.post()
            .uri("/analyze")
            .bodyValue(incident)
            .retrieve()
            .onStatus(status -> status.is5xxServerError(),
                response -> response.bodyToMono(String.class)
                    .map(body -> new RagServiceException(
                        "RAG service error " + response.statusCode() + ": " + body, null)))
            .bodyToMono(IncidentAnalysis.class)
            .timeout(analyzeTimeout)
            .onErrorMap(WebClientResponseException.class,
                e -> new RagServiceException(
                    "RAG HTTP " + e.getStatusCode() + " for " + incident.getNumber()
                        + ": " + e.getResponseBodyAsString(), e))
            .onErrorMap(java.util.concurrent.TimeoutException.class,
                e -> new RagServiceException(
                    "RAG service timeout after " + analyzeTimeout.getSeconds() + "s for "
                        + incident.getNumber(), e))
            .block();

        if (analysis == null) {
            throw new RagServiceException(
                "RAG service returned null for " + incident.getNumber(), null);
        }

        log.info("RAG service responded for {} confidence={}",
            incident.getNumber(), analysis.getConfidence());
        return analysis;
    }

    @Override
    public List<Double> embed(String text) {
        @SuppressWarnings("unchecked")
        List<Double> embedding = ragServiceClient.post()
            .uri("/embed")
            .bodyValue(Map.of("text", text))
            .retrieve()
            .bodyToMono(List.class)
            .timeout(embedTimeout)
            .onErrorMap(WebClientResponseException.class,
                e -> new RagServiceException(
                    "RAG embed HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e))
            .onErrorMap(java.util.concurrent.TimeoutException.class,
                e -> new RagServiceException(
                    "RAG embed service timeout after " + embedTimeout.getSeconds() + "s", e))
            .block();

        if (embedding == null || embedding.isEmpty()) {
            throw new RagServiceException("RAG embed service returned empty embedding", null);
        }

        return embedding;
    }
}
