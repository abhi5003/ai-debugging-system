package com.aidbg.service.dispatcher;

import com.aidbg.model.FeedbackEvent;
import com.aidbg.model.IncidentAnalysis;
import com.aidbg.service.learning.EmbeddingService;
import com.aidbg.service.learning.LearningRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Orchestration layer for the learning pipeline.
 *
 * Previously this class was a God Class handling embedding generation,
 * SQL operations, feedback updates, and scheduled recalculations.
 * Now it delegates to:
 *   - EmbeddingService   (embedding generation + pgvector formatting)
 *   - LearningRepository (all JDBC data access)
 *
 * The confidence threshold is now configurable via @Value, aligned
 * with the Python RAG service's confidence_threshold.
 */
@Service
@Slf4j
public class LearningService {

    private final EmbeddingService   embeddingService;
    private final LearningRepository learningRepository;

    @Value("${learning.min-confidence:0.7}")
    private double minConfidence;

    public LearningService(
            EmbeddingService embeddingService,
            LearningRepository learningRepository) {
        this.embeddingService   = embeddingService;
        this.learningRepository = learningRepository;
    }

    // ── Public API: store analysis embeddings ──────────────────────

    public void store(IncidentAnalysis analysis) {
        List<Double> embedding = embeddingService.generateEmbedding(analysis);
        if (embedding == null) return;

        learningRepository.upsertEmbedding(
            analysis.getSysId(),
            analysis.getNumber(),
            buildLearningText(analysis),
            analysis.getRootCause(),
            analysis.getResolution(),
            analysis.getSource(),
            analysis.getConfidence(),
            analysis.getConfigurationItem(),
            embeddingService.toPgVector(embedding));
    }

    // ── Public API: store from feedback events ─────────────────────

    public void storeAI(FeedbackEvent event) {
        IncidentAnalysis analysis = IncidentAnalysis.builder()
            .sysId(event.getSysId())
            .number(event.getIncidentNumber())
            .rootCause(event.getRootCause())
            .resolution(event.getFinalResolution())
            .confidence(event.getConfidence())
            .source("AI")
            .configurationItem(event.getConfigurationItem())
            .build();
        storeInternal(analysis);
    }

    public void storeHuman(FeedbackEvent event) {
        IncidentAnalysis analysis = IncidentAnalysis.builder()
            .sysId(event.getSysId())
            .number(event.getIncidentNumber())
            .rootCause(event.getRootCause())
            .resolution(event.getFinalResolution())
            .confidence(1.0)
            .source("HUMAN")
            .configurationItem(event.getConfigurationItem())
            .build();
        storeInternal(analysis);
    }

    // ── Public API: feedback handling ──────────────────────────────

    public void updateFeedback(String sysId, boolean accepted) {
        learningRepository.updateFeedback(sysId, accepted);

        Map<String, Object> row = learningRepository.getFeedbackStats(sysId);
        double baseConfidence = ((Number) row.get("confidence")).doubleValue();
        int acc = ((Number) row.get("acceptance_count")).intValue();
        int rej = ((Number) row.get("rejection_count")).intValue();

        double newConfidence = calculateAdjustedConfidence(baseConfidence, acc, rej);

        learningRepository.updateConfidence(sysId, newConfidence);

        log.info("Feedback updated sysId={} accepted={} newConfidence={}",
            sysId, accepted, newConfidence);
    }

    // ── Scheduled tasks ────────────────────────────────────────────

    @Scheduled(fixedRate = 300000)
    public void recalculateConfidence() {
        int updated = learningRepository.recalculateAllConfidence();
        log.info("Recalculated confidence for {} records", updated);
    }

    // ── Internal helpers ───────────────────────────────────────────

    private void storeInternal(IncidentAnalysis analysis) {
        if ("AI".equals(analysis.getSource()) &&
                analysis.getConfidence() < minConfidence) {
            return;
        }

        List<Double> embedding = embeddingService.generateEmbedding(analysis);
        if (embedding == null) return;

        learningRepository.upsertEmbedding(
            analysis.getSysId(),
            analysis.getNumber(),
            buildLearningText(analysis),
            analysis.getRootCause(),
            analysis.getResolution(),
            analysis.getSource(),
            analysis.getConfidence(),
            analysis.getConfigurationItem(),
            embeddingService.toPgVector(embedding));

        log.info("Learning stored incident={} source={}",
            analysis.getNumber(), analysis.getSource());
    }

    private String buildLearningText(IncidentAnalysis analysis) {
        return String.format(
            "number: %s\nroot_cause: %s\nresolution: %s\nsimilar_incidents: %s",
            analysis.getNumber(),
            analysis.getRootCause(),
            analysis.getResolution(),
            String.join(", ", analysis.getSimilarIncidentNumbers() != null
                ? analysis.getSimilarIncidentNumbers()
                : List.of()));
    }

    private double calculateAdjustedConfidence(
            double baseConfidence,
            int acceptanceCount,
            int rejectionCount) {
        int total = acceptanceCount + rejectionCount;
        if (total == 0) return baseConfidence;

        double successRate = (double) acceptanceCount / total;
        return (baseConfidence * 0.6) + (successRate * 0.4);
    }
}
