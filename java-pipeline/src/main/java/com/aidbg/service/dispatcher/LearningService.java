package com.aidbg.service.dispatcher;

import com.aidbg.model.FeedbackEvent;
import com.aidbg.model.IncidentAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LearningService {

    private final WebClient ragServiceClient;
    private final JdbcTemplate jdbcTemplate;

    public LearningService(
            @Qualifier("ragServiceClient") WebClient ragServiceClient,
            JdbcTemplate jdbcTemplate) {
        this.ragServiceClient = ragServiceClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void store(IncidentAnalysis analysis) {
        try {
            String text = buildLearningText(analysis);

            // Ask Python RAG service to embed the resolved incident
            @SuppressWarnings("unchecked")
            List<Double> embedding = ragServiceClient.post()
                    .uri("/embed")
                    .bodyValue(Map.of("text", text))
                    .retrieve()
                    .bodyToMono(List.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            if (embedding == null || embedding.isEmpty()) {
                log.warn("Empty embedding for {}", analysis.getNumber());
                return;
            }

            String pgVector = toPgVector(embedding);

            jdbcTemplate.update("""
                        INSERT INTO incident_embeddings
                        (sys_id, number, description, root_cause, resolution,
                         source, confidence,
                         feedback_count, acceptance_count, rejection_count,
                         embedding, resolved_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?::vector, now())
                        ON CONFLICT (sys_id) DO UPDATE SET
                            resolution = EXCLUDED.resolution,
                            source = EXCLUDED.source,
                            confidence = EXCLUDED.confidence,
                            embedding = EXCLUDED.embedding
                    """,
                    analysis.getSysId(),
                    analysis.getNumber(),
                    text,
                    analysis.getRootCause(),
                    analysis.getResolution(),
                    analysis.getSource(),
                    analysis.getConfidence(),
                    toPgVector(embedding));

            log.info("Stored learning embedding for {}", analysis.getNumber());

        } catch (Exception e) {
            log.error("Learning store failed for {}: {}", analysis.getNumber(), e.getMessage());
        }
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

    private String toPgVector(List<Double> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1)
                sb.append(",");
        }
        return sb.append("]").toString();
    }

    public void storeAI(FeedbackEvent event) {

        IncidentAnalysis analysis = IncidentAnalysis.builder()
                .sysId(event.getSysId())
                .number(event.getIncidentNumber())
                .rootCause(event.getRootCause())
                .resolution(event.getFinalResolution())
                .confidence(event.getConfidence())
                .source("AI")
                .build();

        storeInternal(analysis);
    }

    public void storeHuman(FeedbackEvent event) {

        IncidentAnalysis analysis = IncidentAnalysis.builder()
                .sysId(event.getSysId())
                .number(event.getIncidentNumber())
                .rootCause(event.getRootCause())
                .resolution(event.getFinalResolution())
                .confidence(1.0) // HUMAN = gold data
                .source("HUMAN")
                .build();

        storeInternal(analysis);
    }

    private void storeInternal(IncidentAnalysis analysis) {

        // ❌ skip low confidence ONLY for AI
        if ("AI".equals(analysis.getSource()) &&
                analysis.getConfidence() != (Double) null &&
                analysis.getConfidence() < 0.7) {
            return;
        }

        String text = buildLearningText(analysis);

        List<Double> embedding = ragServiceClient.post()
                .uri("/embed")
                .bodyValue(Map.of("text", text))
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (embedding == null || embedding.isEmpty())
            return;

        jdbcTemplate.update("""
                INSERT INTO incident_embeddings
                (sys_id, number, description, root_cause, resolution, source, confidence, embedding, resolved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector, now())
                ON CONFLICT (sys_id) DO UPDATE SET
                resolution = EXCLUDED.resolution,
                source = EXCLUDED.source,
                confidence = EXCLUDED.confidence,
                embedding = EXCLUDED.embedding
                """,
                analysis.getSysId(),
                analysis.getNumber(),
                text,
                analysis.getRootCause(),
                analysis.getResolution(),
                analysis.getSource(),
                analysis.getConfidence(),
                toPgVector(embedding));

        log.info("Learning stored incident={} source={}",
                analysis.getNumber(), analysis.getSource());
    }

    public void updateFeedback(String sysId, boolean accepted) {

        // 1. Update counts
        if (accepted) {
            jdbcTemplate.update("""
                        UPDATE incident_embeddings
                        SET feedback_count = feedback_count + 1,
                            acceptance_count = acceptance_count + 1
                        WHERE sys_id = ?
                    """, sysId);
        } else {
            jdbcTemplate.update("""
                        UPDATE incident_embeddings
                        SET feedback_count = feedback_count + 1,
                            rejection_count = rejection_count + 1
                        WHERE sys_id = ?
                    """, sysId);
        }

        // 2. Fetch updated counts
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT confidence, acceptance_count, rejection_count
                    FROM incident_embeddings
                    WHERE sys_id = ?
                """, sysId);

        double baseConfidence = ((Number) row.get("confidence")).doubleValue();
        int acc = ((Number) row.get("acceptance_count")).intValue();
        int rej = ((Number) row.get("rejection_count")).intValue();

        // 3. Calculate new confidence
        double newConfidence = calculateAdjustedConfidence(baseConfidence, acc, rej);

        // 4. Update confidence
        jdbcTemplate.update("""
                    UPDATE incident_embeddings
                    SET confidence = ?
                    WHERE sys_id = ?
                """, newConfidence, sysId);

        log.info("Feedback updated sysId={} accepted={} newConfidence={}",
                sysId, accepted, newConfidence);
    }

    private double calculateAdjustedConfidence(
            double baseConfidence,
            int acceptanceCount,
            int rejectionCount) {
        int total = acceptanceCount + rejectionCount;

        if (total == 0)
            return baseConfidence;

        double successRate = (double) acceptanceCount / total;

        return (baseConfidence * 0.6) + (successRate * 0.4);
    }

    @Scheduled(fixedRate = 300000) // every 5 minutes
    public void recalculateConfidence() {

        int updated = jdbcTemplate.update("""
                    UPDATE incident_embeddings
                    SET confidence =
                        CASE
                            WHEN feedback_count = 0 THEN confidence
                            ELSE (acceptance_count::float / feedback_count)
                        END
                """);

        log.info("Recalculated confidence for {} records", updated);
    }

}
