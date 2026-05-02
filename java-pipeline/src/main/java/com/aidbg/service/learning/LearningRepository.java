package com.aidbg.service.learning;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * Repository pattern for all incident_embeddings database operations.
 *
 * Extracted from LearningService to separate data access from business
 * logic. All SQL INSERT, UPDATE, and SELECT queries live here.
 */
@Repository
@Slf4j
public class LearningRepository {

    private final JdbcTemplate jdbcTemplate;

    public LearningRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Upsert an incident embedding (AI source, no feedback tracking).
     */
    public void upsertEmbedding(String sysId, String number, String description,
                                 String rootCause, String resolution, String source,
                                 double confidence, String configurationItem,
                                 String embeddingVector) {
        jdbcTemplate.update("""
                INSERT INTO incident_embeddings
                (sys_id, number, description, root_cause, resolution,
                 source, confidence, configuration_item,
                 feedback_count, acceptance_count, rejection_count,
                 embedding, resolved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?::vector, now())
                ON CONFLICT (sys_id) DO UPDATE SET
                    resolution = EXCLUDED.resolution,
                    source = EXCLUDED.source,
                    confidence = EXCLUDED.confidence,
                    configuration_item = EXCLUDED.configuration_item,
                    embedding = EXCLUDED.embedding
                """,
            sysId, number, description, rootCause, resolution,
            source, confidence, configurationItem, embeddingVector);

        log.info("Stored learning embedding for {}", number);
    }

    /**
     * Update feedback counts and recalculate confidence for a sys_id.
     */
    public void updateFeedback(String sysId, boolean accepted) {
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
    }

    /**
     * Read current confidence and feedback counts.
     */
    public Map<String, Object> getFeedbackStats(String sysId) {
        return jdbcTemplate.queryForMap("""
            SELECT confidence, acceptance_count, rejection_count
            FROM incident_embeddings
            WHERE sys_id = ?
            """, sysId);
    }

    /**
     * Update confidence value for a sys_id.
     */
    public void updateConfidence(String sysId, double newConfidence) {
        jdbcTemplate.update("""
            UPDATE incident_embeddings
            SET confidence = ?
            WHERE sys_id = ?
            """, newConfidence, sysId);
    }

    /**
     * Recalculate confidence for all records with feedback.
     */
    public int recalculateAllConfidence() {
        return jdbcTemplate.update("""
            UPDATE incident_embeddings
            SET confidence =
                CASE
                    WHEN feedback_count = 0 THEN confidence
                    ELSE (acceptance_count::float / feedback_count)
                END
            """);
    }
}
