package com.aidbg.service.dynatrace;

import com.aidbg.model.IncidentAnalysis;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builder pattern for ServiceNow work note formatting.
 *
 * Previously the workNote string was a hardcoded format blob inside
 * ServiceNowWritebackListener, making it difficult to test or modify.
 * This builder encapsulates the formatting logic.
 */
public class WorkNoteBuilder {

    private final IncidentAnalysis analysis;

    private WorkNoteBuilder(IncidentAnalysis analysis) {
        this.analysis = analysis;
    }

    public static WorkNoteBuilder from(IncidentAnalysis analysis) {
        return new WorkNoteBuilder(analysis);
    }

    public String build() {
        List<String> actions = analysis.getImmediateActions() != null
            ? analysis.getImmediateActions() : List.of();

        String actionsList = IntStream.range(0, actions.size())
            .mapToObj(i -> (i + 1) + ". " + actions.get(i))
            .collect(Collectors.joining("\n"));

        return String.format(
            "\uD83E\uDD16 AI Suggested Analysis (Needs Review)\n\n" +
            "Confidence: %.0f%%\n\n" +
            "Root Cause:\n%s\n\n" +
            "Resolution:\n%s\n\n" +
            "Immediate Actions:\n%s\n\n" +
            "Similar Incidents: %s\n\n" +
            "\u26A0 Please ACCEPT or REJECT this suggestion.",
            safePercent(analysis.getConfidence()),
            safe(analysis.getRootCause()),
            safe(analysis.getResolution()),
            actionsList,
            analysis.getSimilarIncidentNumbers() != null
                ? String.join(", ", analysis.getSimilarIncidentNumbers()) : "none"
        );
    }

    private String safe(String val) {
        return val != null ? val : "N/A";
    }

    private double safePercent(Double val) {
        return val != null ? val * 100 : 0.0;
    }
}
