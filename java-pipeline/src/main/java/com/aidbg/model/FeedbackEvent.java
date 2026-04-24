package com.aidbg.model;

import lombok.Data;

@Data
public class FeedbackEvent {

    private String sysId;
    private String incidentNumber;

    private boolean accepted;

    private String rootCause;
    private String finalResolution;

    private Double confidence;
}