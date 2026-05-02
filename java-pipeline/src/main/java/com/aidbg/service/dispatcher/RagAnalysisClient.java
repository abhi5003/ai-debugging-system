package com.aidbg.service.dispatcher;

import com.aidbg.model.EnrichedIncident;
import com.aidbg.model.IncidentAnalysis;

import java.util.List;

public interface RagAnalysisClient {
    IncidentAnalysis analyze(EnrichedIncident incident);
    List<Double> embed(String text);
}
