package com.aidbg.service.processor;

import com.aidbg.config.ScoringConfig;
import com.aidbg.model.EnrichedIncident;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Incident priority scorer using configurable weights from ScoringConfig.
 *
 * Previously all scoring weights (80, 60, 40, etc.) were hardcoded here.
 * ScoringConfig unifies weights and routing thresholds in one place
 * so they cannot drift apart.
 */
@Component
@RequiredArgsConstructor
public class PriorityScorer {

    private final ScoringConfig config;

    public int score(EnrichedIncident incident) {
        int base = config.getBaseScore(incident.getPriority());

        int boost = 0;

        if (incident.getMetrics() != null) {
            double errorRate = incident.getMetrics().getErrorRatePercent();
            if (errorRate > config.getErrorRateHighThreshold())
                boost += config.getErrorRateHighBoost();
            else if (errorRate > config.getErrorRateMediumThreshold())
                boost += config.getErrorRateMediumBoost();
        }

        if (incident.getTraces() != null) {
            int openProblems = incident.getTraces().getRecentProblemIds().size();
            boost += Math.min(openProblems * config.getBoostPerOpenProblem(), config.getOpenProblemMaxBoost());
        }

        return Math.min(base + boost, 100);
    }
}
