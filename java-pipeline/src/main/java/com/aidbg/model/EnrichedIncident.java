package com.aidbg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnrichedIncident {
    private String        sysId;
    private String        number;
    private String        shortDescription;
    private Priority      priority;
    private String        state;
    private String        assignedTo;
    private String        configurationItem;
    private Instant       updatedAt;
    private MetricsData   metrics;
    private TraceData     traces;
    private TopologyData  topology;
    private Instant       enrichedAt;
}
