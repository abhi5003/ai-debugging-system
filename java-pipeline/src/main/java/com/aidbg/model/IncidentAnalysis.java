package com.aidbg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncidentAnalysis {
    private String sysId;
    private String number;
    private String rootCause;
    private String resolution;
    private List<String> immediateActions;
    private double confidence;
    private List<String> similarIncidentNumbers;
    private List<String> agentReasoningTrace;
    private int retrievalAttempts;
    private Instant analyzedAt;
    private String status;
    private String source;
    private String configurationItem;
}
