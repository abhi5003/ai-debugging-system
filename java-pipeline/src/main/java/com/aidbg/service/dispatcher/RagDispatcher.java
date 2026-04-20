package com.aidbg.service.dispatcher;

import com.aidbg.model.EnrichedIncident;
import com.aidbg.model.IncidentAnalysis;
import com.aidbg.model.Severity;
import com.aidbg.service.kafka.AnalysisKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Component
@Slf4j
public class RagDispatcher {

    private final WebClient              ragServiceClient;
    private final AnalysisKafkaProducer  analysisProducer;
    private final LearningService        learningService;
    private final ExecutorService        mediumPool =
        Executors.newFixedThreadPool(4);
    private final BlockingQueue<EnrichedIncident> lowSeverityQueue =
        new LinkedBlockingQueue<>(500);

    public RagDispatcher(
            @Qualifier("ragServiceClient") WebClient ragServiceClient,
            AnalysisKafkaProducer analysisProducer,
            LearningService learningService) {
        this.ragServiceClient  = ragServiceClient;
        this.analysisProducer  = analysisProducer;
        this.learningService   = learningService;
    }

    public void dispatch(EnrichedIncident incident, Severity severity) {
        switch (severity) {
            case HIGH   -> callRag(incident);
            case MEDIUM -> mediumPool.submit(() -> callRag(incident));
            case LOW    -> {
                if (!lowSeverityQueue.offer(incident)) {
                    log.warn("Low severity queue full — dropping {}", incident.getNumber());
                }
            }
        }
        log.info("Dispatched {} as {}", incident.getNumber(), severity);
    }

    private void callRag(EnrichedIncident incident) {
        try {
            log.info("POST /analyze → rag-service  incident={}", incident.getNumber());

            IncidentAnalysis analysis = ragServiceClient.post()
                .uri("/analyze")
                .bodyValue(incident)
                .retrieve()
                .bodyToMono(IncidentAnalysis.class)
                .timeout(Duration.ofSeconds(60))
                .block();

            if (analysis == null) {
                log.error("RAG returned null for {}", incident.getNumber());
                return;
            }

            log.info("RAG responded for {} confidence={}",
                incident.getNumber(), analysis.getConfidence());

            // Publish to Kafka incident-analysis topic
            analysisProducer.publish(analysis);

            // Feed learning pipeline if confidence is high enough
            if (analysis.getConfidence() >= 0.70) {
                learningService.store(analysis);
            }

        } catch (WebClientResponseException e) {
            log.error("RAG HTTP {} for {}: {}",
                e.getStatusCode(), incident.getNumber(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("RAG call failed for {}: {}", incident.getNumber(), e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 120_000)
    public void drainLowSeverityQueue() {
        List<EnrichedIncident> batch = new ArrayList<>();
        lowSeverityQueue.drainTo(batch, 20);
        if (!batch.isEmpty()) {
            log.info("Draining {} low-severity incidents", batch.size());
            batch.forEach(this::callRag);
        }
    }
}
