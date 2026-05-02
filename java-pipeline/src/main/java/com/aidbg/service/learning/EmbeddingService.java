package com.aidbg.service.learning;

import com.aidbg.model.IncidentAnalysis;
import com.aidbg.service.dispatcher.RagAnalysisClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles embedding generation and text formatting for the learning pipeline.
 *
 * Extracted from LearningService to satisfy Single Responsibility Principle.
 * This class only knows about building learning text and calling the
 * embedding service -- it does not touch the database.
 */
@Service
@Slf4j
public class EmbeddingService {

    private final RagAnalysisClient ragClient;

    public EmbeddingService(RagAnalysisClient ragClient) {
        this.ragClient = ragClient;
    }

    /**
     * Generate an embedding vector for the given analysis.
     * Returns null if the embedding is empty or generation fails.
     */
    public List<Double> generateEmbedding(IncidentAnalysis analysis) {
        try {
            String text = buildLearningText(analysis);
            List<Double> embedding = ragClient.embed(text);

            if (embedding == null || embedding.isEmpty()) {
                log.warn("Empty embedding for {}", analysis.getNumber());
                return null;
            }

            return embedding;
        } catch (Exception e) {
            log.error("Embedding generation failed for {}: {}", analysis.getNumber(), e.getMessage());
            return null;
        }
    }

    /**
     * Convert a double list to the pgvector string format.
     */
    public String toPgVector(List<Double> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1)
                sb.append(",");
        }
        return sb.append("]").toString();
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
}
