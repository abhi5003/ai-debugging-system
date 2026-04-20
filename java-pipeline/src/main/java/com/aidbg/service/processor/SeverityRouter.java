package com.aidbg.service.processor;

import com.aidbg.model.Severity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SeverityRouter {

    @Value("${processor.score.critical-threshold:80}")
    private int criticalThreshold;

    @Value("${processor.score.high-threshold:50}")
    private int highThreshold;

    public Severity route(int score) {
        if (score >= criticalThreshold) return Severity.HIGH;
        if (score >= highThreshold)     return Severity.MEDIUM;
        return Severity.LOW;
    }
}
