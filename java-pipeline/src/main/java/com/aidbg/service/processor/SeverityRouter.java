package com.aidbg.service.processor;

import com.aidbg.config.ScoringConfig;
import com.aidbg.model.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Routes a numeric score to a severity level using thresholds
 * defined in ScoringConfig.
 */
@Component
@RequiredArgsConstructor
public class SeverityRouter {

    private final ScoringConfig config;

    public Severity route(int score) {
        if (score >= config.getCriticalThreshold()) return Severity.HIGH;
        if (score >= config.getHighThreshold())     return Severity.MEDIUM;
        return Severity.LOW;
    }
}
