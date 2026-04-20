package com.aidbg.service.processor;

import com.aidbg.model.EnrichedIncident;
import org.springframework.stereotype.Component;

@Component
public class PriorityScorer {

    public int score(EnrichedIncident incident) {
        int base = switch (incident.getPriority()) {
            case CRITICAL -> 80;
            case HIGH     -> 60;
            case MEDIUM   -> 40;
            case LOW      -> 20;
            default       -> 10;
        };

        int boost = 0;

        if (incident.getMetrics() != null) {
            double errorRate = incident.getMetrics().getErrorRatePercent();
            if (errorRate > 10) boost += 15;
            else if (errorRate > 5) boost += 8;
        }

        if (incident.getTraces() != null) {
            int openProblems = incident.getTraces().getRecentProblemIds().size();
            boost += Math.min(openProblems * 5, 15);
        }

        return Math.min(base + boost, 100);
    }
}
