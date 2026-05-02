package com.aidbg.service.dispatcher;

import com.aidbg.model.EnrichedIncident;
import com.aidbg.model.IncidentAnalysis;
import com.aidbg.model.Severity;
import com.aidbg.service.kafka.AnalysisKafkaProducer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Dispatches incidents to the RAG service based on severity.
 *
 * Uses a Spring-managed ThreadPoolTaskExecutor instead of raw
 * Executors.newFixedThreadPool() to ensure proper lifecycle
 * management and avoid thread leaks on context shutdown.
 */
@Component
@Slf4j
public class RagDispatcher {

    private final RagAnalysisClient      ragClient;
    private final AnalysisKafkaProducer  analysisProducer;
    private final ThreadPoolTaskExecutor mediumPool;
    private final BlockingQueue<EnrichedIncident> lowSeverityQueue =
        new LinkedBlockingQueue<>(500);

    public RagDispatcher(
            RagAnalysisClient ragClient,
            AnalysisKafkaProducer analysisProducer) {
        this.ragClient        = ragClient;
        this.analysisProducer = analysisProducer;

        this.mediumPool = new ThreadPoolTaskExecutor();
        mediumPool.setCorePoolSize(4);
        mediumPool.setMaxPoolSize(8);
        mediumPool.setQueueCapacity(100);
        mediumPool.setThreadNamePrefix("rag-medium-");
        mediumPool.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        mediumPool.initialize();
    }

    @PreDestroy
    public void shutdown() {
        mediumPool.shutdown();
    }

    public void dispatch(EnrichedIncident incident, Severity severity) {
        switch (severity) {
            case HIGH   -> callRag(incident);
            case MEDIUM -> mediumPool.submit(() -> callRag(incident));
            case LOW    -> {
                if (!lowSeverityQueue.offer(incident)) {
                    log.warn("Low severity queue full - dropping {}", incident.getNumber());
                }
            }
        }
        log.info("Dispatched {} as {}", incident.getNumber(), severity);
    }

    @CircuitBreaker(name = "ragService", fallbackMethod = "fallbackAnalyze")
    void callRag(EnrichedIncident incident) {
        IncidentAnalysis analysis = ragClient.analyze(incident);

        analysis.setStatus("PENDING_APPROVAL");
        analysis.setSource("AI");

        analysisProducer.publish(analysis);
    }

    private void fallbackAnalyze(EnrichedIncident incident, Throwable t) {
        log.error("Circuit breaker OPEN for {} — using fallback: {}",
            incident.getNumber(), t.getMessage());

        IncidentAnalysis fallback = IncidentAnalysis.builder()
            .sysId(incident.getSysId())
            .number(incident.getNumber())
            .rootCause("Analysis unavailable — RAG service unreachable")
            .resolution("Manual review required")
            .immediateActions(List.of("Escalate to on-call engineer"))
            .confidence(0.0)
            .status("PENDING_APPROVAL")
            .source("AI-FALLBACK")
            .analyzedAt(Instant.now())
            .build();

        analysisProducer.publish(fallback);
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
