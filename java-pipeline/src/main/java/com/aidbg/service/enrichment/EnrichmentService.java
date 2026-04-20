package com.aidbg.service.enrichment;

import com.aidbg.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class EnrichmentService {

    private final WebClient dynatraceClient;

    @Value("${dynatrace.timeout-seconds:5}")
    private int timeoutSeconds;

    public EnrichmentService(@Qualifier("dynatraceClient") WebClient dynatraceClient) {
        this.dynatraceClient = dynatraceClient;
    }

    public EnrichedIncident enrich(NormalizedIncident incident) {
        String entityId = incident.getConfigurationItem() != null
            ? incident.getConfigurationItem() : "unknown";

        // Fire all three Dynatrace calls in parallel
        CompletableFuture<MetricsData>   mf = fetchMetrics(entityId);
        CompletableFuture<TraceData>     tf = fetchTraces(entityId);
        CompletableFuture<TopologyData>  pf = fetchTopology(entityId);

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

    private CompletableFuture<MetricsData> fetchMetrics(String entityId) {
        return dynatraceClient.get()
            .uri(u -> u.path("/api/v2/metrics/query")
                .queryParam("metricSelector",
                    "builtin:host.cpu.usage,builtin:host.mem.usage,builtin:service.errors.total.rate")
                .queryParam("entitySelector", "entityId(" + entityId + ")")
                .queryParam("resolution", "5m")
                .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .map(this::parseMetrics)
            .onErrorReturn(defaultMetrics())
            .toFuture();
    }

    private CompletableFuture<TraceData> fetchTraces(String entityId) {
        return dynatraceClient.get()
            .uri(u -> u.path("/api/v2/problems")
                .queryParam("entitySelector", "entityId(" + entityId + ")")
                .queryParam("problemSelector", "status(OPEN)")
                .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .map(this::parseTraces)
            .onErrorReturn(defaultTraces())
            .toFuture();
    }

    private CompletableFuture<TopologyData> fetchTopology(String entityId) {
        return dynatraceClient.get()
            .uri("/api/v2/entities/" + entityId + "?fields=fromRelationships,toRelationships")
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .map(this::parseTopology)
            .onErrorReturn(defaultTopology())
            .toFuture();
    }

    private MetricsData parseMetrics(JsonNode node) {
        MetricsData m = new MetricsData();
        try {
            JsonNode results = node.path("result");
            if (results.isArray() && results.size() > 0) {
                m.setCpuUsagePercent(
                    results.get(0).path("data").path(0).path("values").path(0).asDouble(0));
            }
        } catch (Exception e) {
            log.debug("Metrics parse partial: {}", e.getMessage());
        }
        return m;
    }

    private TraceData parseTraces(JsonNode node) {
        List<String> ids = new ArrayList<>();
        node.path("problems").forEach(p -> ids.add(p.path("problemId").asText()));
        return TraceData.builder()
            .recentProblemIds(ids)
            .errorCount(node.path("totalCount").asInt(0))
            .slowSpanOperations(List.of())
            .build();
    }

    private TopologyData parseTopology(JsonNode node) {
        List<String> up = new ArrayList<>(), down = new ArrayList<>();
        node.path("fromRelationships").path("calls")
            .forEach(r -> down.add(r.path("id").asText()));
        node.path("toRelationships").path("isCalledBy")
            .forEach(r -> up.add(r.path("id").asText()));
        return TopologyData.builder()
            .upstreamServices(up)
            .downstreamServices(down)
            .build();
    }

    private MetricsData  defaultMetrics()  { return new MetricsData(); }
    private TraceData    defaultTraces()   {
        return TraceData.builder()
            .recentProblemIds(List.of())
            .slowSpanOperations(List.of())
            .build();
    }
    private TopologyData defaultTopology() {
        return TopologyData.builder()
            .upstreamServices(List.of())
            .downstreamServices(List.of())
            .build();
    }
}
