package com.aidbg.service.dispatcher;

import com.aidbg.model.IncidentAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LearningService {

    private final WebClient    ragServiceClient;
    private final JdbcTemplate jdbcTemplate;

    public LearningService(
            @Qualifier("ragServiceClient") WebClient ragServiceClient,
            JdbcTemplate jdbcTemplate) {
        this.ragServiceClient = ragServiceClient;
        this.jdbcTemplate     = jdbcTemplate;
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
                    (sys_id, number, description, root_cause, resolution, priority, embedding, resolved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::vector, now())
                ON CONFLICT (sys_id) DO UPDATE SET
                    root_cause  = EXCLUDED.root_cause,
                    resolution  = EXCLUDED.resolution,
                    embedding   = EXCLUDED.embedding,
                    resolved_at = now()
                """,
                analysis.getSysId(),
                analysis.getNumber(),
                text,
                analysis.getRootCause(),
                analysis.getResolution(),
                "AI_ANALYZED",
                pgVector
            );

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
                ? analysis.getSimilarIncidentNumbers() : List.of())
        );
    }

    private String toPgVector(List<Double> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }
}
