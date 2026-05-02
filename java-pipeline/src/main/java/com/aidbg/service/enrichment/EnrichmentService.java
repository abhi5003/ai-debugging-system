package com.aidbg.service.enrichment;

import com.aidbg.model.EnrichedIncident;
import com.aidbg.model.NormalizedIncident;
import com.aidbg.service.dynatrace.DynatraceApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Enriches a normalized incident with Dynatrace observability data.
 *
 * Delegates HTTP calls to DynatraceApiClient (Template Method pattern)
 * instead of building WebClient pipelines inline.
 */
@Service
@Slf4j
public class EnrichmentService {

    private final DynatraceApiClient dynatraceClient;

    public EnrichmentService(DynatraceApiClient dynatraceClient) {
        this.dynatraceClient = dynatraceClient;
    }

    public EnrichedIncident enrich(NormalizedIncident incident) {
        String entityId = incident.getConfigurationItem() != null
            ? incident.getConfigurationItem() : "unknown";

        // Fire all three Dynatrace calls in parallel
        CompletableFuture<com.aidbg.model.MetricsData>   mf = dynatraceClient.fetchMetrics(entityId);
        CompletableFuture<com.aidbg.model.TraceData>     tf = dynatraceClient.fetchTraces(entityId);
        CompletableFuture<com.aidbg.model.TopologyData>  pf = dynatraceClient.fetchTopology(entityId);

        // Wait up to 6 seconds total
        try {
            CompletableFuture.allOf(mf, tf, pf)
                .orTimeout(6, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.warn("Enrichment partial timeout for {}: {}",
                        incident.getNumber(), ex.getMessage());
                    return null;
                })
                .join();
        } catch (Exception e) {
            log.warn("Enrichment join error for {}: {}", incident.getNumber(), e.getMessage());
        }

        return EnrichedIncident.builder()
            .sysId(incident.getSysId())
            .number(incident.getNumber())
            .shortDescription(incident.getShortDescription())
            .priority(incident.getPriority())
            .state(incident.getState().name())
            .assignedTo(incident.getAssignedTo())
            .configurationItem(incident.getConfigurationItem())
            .updatedAt(incident.getUpdatedAt())
            .metrics(mf.getNow(defaultMetrics()))
            .traces(tf.getNow(defaultTraces()))
            .topology(pf.getNow(defaultTopology()))
            .enrichedAt(Instant.now())
            .build();
    }

    private com.aidbg.model.MetricsData defaultMetrics()  { return new com.aidbg.model.MetricsData(); }
    private com.aidbg.model.TraceData defaultTraces() {
        return com.aidbg.model.TraceData.builder()
            .recentProblemIds(java.util.List.of())
            .slowSpanOperations(java.util.List.of())
            .build();
    }
    private com.aidbg.model.TopologyData defaultTopology() {
        return com.aidbg.model.TopologyData.builder()
            .upstreamServices(java.util.List.of())
            .downstreamServices(java.util.List.of())
            .build();
    }
}
