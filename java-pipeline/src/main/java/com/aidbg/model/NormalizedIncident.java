package com.aidbg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NormalizedIncident {
    private String        sysId;
    private String        number;
    private String        shortDescription;
    private Priority      priority;
    private IncidentState state;
    private String        assignedTo;
    private String        configurationItem;
    private Instant       updatedAt;
}
