package com.aidbg.model;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TraceData {
    private List<String> recentProblemIds;
    private List<String> slowSpanOperations;
    private int          errorCount;
}
