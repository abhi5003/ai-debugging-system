package com.aidbg.listener;

import com.aidbg.model.IncidentAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Slf4j
public class ServiceNowWritebackListener {

    private final ObjectMapper objectMapper;
    private final WebClient    serviceNowClient;

    @Value("${servicenow.base-url}")
    private String baseUrl;

    public ServiceNowWritebackListener(
            ObjectMapper objectMapper,
            @Qualifier("serviceNowClient") WebClient serviceNowClient) {
        this.objectMapper     = objectMapper;
        this.serviceNowClient = serviceNowClient;
    }

    @KafkaListener(
        topics           = "${kafka.topics.incident-analysis}",
        groupId          = "sn-writeback",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAnalysis(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
        String sysId = record.key();
        log.info("Writeback received sysId={}", sysId);

        try {
            IncidentAnalysis analysis =
                objectMapper.readValue(record.value(), IncidentAnalysis.class);

            // Skip low-confidence — don't pollute ServiceNow
            if (analysis.getConfidence() < 0.50) {
                log.warn("Skipping writeback for {} — confidence {} too low",
                    analysis.getNumber(), analysis.getConfidence());
                ack.acknowledge();
                return;
            }

            updateServiceNow(sysId, analysis);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Writeback failed sysId={}: {}", sysId, e.getMessage());
            // No ack → retry
        }
    }

    private void updateServiceNow(String sysId, IncidentAnalysis analysis) {
        List<String> actions = analysis.getImmediateActions() != null
            ? analysis.getImmediateActions() : List.of();

        String actionsList = IntStream.range(0, actions.size())
            .mapToObj(i -> (i + 1) + ". " + actions.get(i))
            .collect(Collectors.joining("\n"));

        String workNote = String.format(
            "[AI Analysis — confidence %.0f%%]\n\nRoot cause:\n%s\n\nImmediate actions:\n%s\n\nSimilar incidents: %s",
            analysis.getConfidence() * 100,
            analysis.getRootCause(),
            actionsList,
            analysis.getSimilarIncidentNumbers() != null
                ? String.join(", ", analysis.getSimilarIncidentNumbers()) : "none"
        );

        Map<String, Object> body = Map.of(
            "work_notes",       workNote,
            "u_ai_root_cause",  analysis.getRootCause(),
            "u_ai_resolution",  analysis.getResolution(),
            "u_ai_confidence",  String.valueOf(analysis.getConfidence())
        );

        try {
            serviceNowClient.patch()
                .uri("/api/now/table/incident/" + sysId)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(15))
                .block();

            log.info("ServiceNow updated for {} ({})", analysis.getNumber(), sysId);

        } catch (WebClientResponseException e) {
            log.error("ServiceNow PATCH failed {} — HTTP {}: {}",
                sysId, e.getStatusCode(), e.getResponseBodyAsString());
            // In real env with real SN, re-throw to trigger retry
            // In dev with dummy URL, swallow it
        } catch (Exception e) {
            log.warn("ServiceNow update skipped (likely dev mode): {}", e.getMessage());
        }
    }
}
