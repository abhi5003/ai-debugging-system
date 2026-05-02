package com.aidbg.service.dynatrace;

import com.aidbg.model.MetricsData;
import com.aidbg.model.TopologyData;
import com.aidbg.model.TraceData;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Template Method for Dynatrace API calls.
 *
 * Previously EnrichmentService contained three nearly identical fetch
 * methods (fetchMetrics, fetchTraces, fetchTopology) with duplicated
 * WebClient pipeline logic. This class centralises that pipeline and
 * exposes typed fetch methods.
 */
@Service
@Slf4j
public class DynatraceApiClient {

    private final WebClient dynatraceClient;
    private final Duration timeout;

    public DynatraceApiClient(
            @Qualifier("dynatraceClient") WebClient dynatraceClient,
            @Value("${dynatrace.timeout-seconds:5}") int timeoutSeconds) {
        this.dynatraceClient = dynatraceClient;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Fetch metrics for the given entity.
     */
    public CompletableFuture<MetricsData> fetchMetrics(String entityId) {
        return executeGet(
            u -> u.path("/api/v2/metrics/query")
                .queryParam("metricSelector",
                    "builtin:host.cpu.usage,builtin:host.mem.usage,builtin:service.errors.total.rate")
                .queryParam("entitySelector", "entityId(" + entityId + ")")
                .queryParam("resolution", "5m")
                .build(),
            this::parseMetrics,
            this::defaultMetrics
        );
    }

    /**
     * Fetch open problems (traces) for the given entity.
     */
    public CompletableFuture<TraceData> fetchTraces(String entityId) {
        return executeGet(
            u -> u.path("/api/v2/problems")
                .queryParam("entitySelector", "entityId(" + entityId + ")")
                .queryParam("problemSelector", "status(OPEN)")
                .build(),
            this::parseTraces,
            this::defaultTraces
        );
    }

    /**
     * Fetch topology for the given entity.
     */
    public CompletableFuture<TopologyData> fetchTopology(String entityId) {
        return executeGet(
            u -> u.path("/api/v2/entities/" + entityId + "?fields=fromRelationships,toRelationships").build(),
            this::parseTopology,
            this::defaultTopology
        );
    }

    /**
     * Generic GET pipeline: get → retrieve → bodyToMono → timeout → map → onErrorReturn → toFuture.
     */
    private <T> CompletableFuture<T> executeGet(
            java.util.function.Function<UriBuilder, java.net.URI> uriBuilder,
            java.util.function.Function<JsonNode, T> mapper,
            java.util.function.Supplier<T> fallback) {
        return dynatraceClient.get()
            .uri(uriBuilder)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(timeout)
            .map(mapper)
            .onErrorReturn(fallback.get())
            .toFuture();
    }

    private MetricsData parseMetrics(JsonNode node) {
        MetricsData m = new MetricsData();
        try {
            JsonNode results = node.path("result");
            if (results.isArray() && results.size() > 0) {
                for (int i = 0; i < results.size(); i++) {
                    JsonNode result = results.get(i);
                    String metricId = result.path("metricId").asText("");
                    double value = result.path("data").path(0).path("values").path(0).asDouble(0);

                    if (metricId.contains("cpu.usage")) {
                        m.setCpuUsagePercent(value);
                    } else if (metricId.contains("mem.usage")) {
                        m.setMemoryUsagePercent(value);
                    } else if (metricId.contains("errors.total.rate")) {
                        m.setErrorRatePercent(value);
                    }
                }
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

    private MetricsData defaultMetrics() { return new MetricsData(); }

    private TraceData defaultTraces() {
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
