package com.aidbg.model;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TopologyData {
    private List<String> upstreamServices;
    private List<String> downstreamServices;
    private String       hostGroup;
}
