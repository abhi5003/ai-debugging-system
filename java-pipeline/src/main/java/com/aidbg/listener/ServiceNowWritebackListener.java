package com.aidbg.listener;

import com.aidbg.model.IncidentAnalysis;
import com.aidbg.service.dynatrace.WorkNoteBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

@Component
@Slf4j
public class ServiceNowWritebackListener {

    private final ObjectMapper objectMapper;
    private final WebClient    serviceNowClient;

    @Value("${servicenow.base-url}")
    private String baseUrl;

    @Value("${servicenow.writeback-timeout-seconds:15}")
    private int writebackTimeoutSec;

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

        try {
            IncidentAnalysis analysis =
                objectMapper.readValue(record.value(), IncidentAnalysis.class);

            if (analysis.getStatus() == null) {
                analysis.setStatus("PENDING_APPROVAL");
            }

            if (analysis.getSource() == null) {
                analysis.setSource("AI");
            }

            Double confidence = analysis.getConfidence();
            if (confidence == null || Double.isNaN(confidence)) {
                analysis.setConfidence(0.0);
            }

            log.info("Writeback received sysId={} incident={} confidence={} status={}",
                sysId,
                analysis.getNumber(),
                analysis.getConfidence(),
                analysis.getStatus()
            );

            updateServiceNow(sysId, analysis);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Writeback failed sysId={}: {}", sysId, e.getMessage());
            // No ack → retry
        }
    }

    private void updateServiceNow(String sysId, IncidentAnalysis analysis) {
        String workNote = WorkNoteBuilder.from(analysis).build();

        Map<String, Object> body = Map.of(
            "work_notes",       workNote,
            "u_ai_root_cause",  safe(analysis.getRootCause()),
            "u_ai_resolution",  safe(analysis.getResolution()),
            "u_ai_confidence",  analysis.getConfidence(),
            "u_ai_status",      "PENDING_APPROVAL"
        );

        serviceNowClient.patch()
            .uri("/api/now/table/incident/" + sysId)
            .bodyValue(body)
            .retrieve()
            .toBodilessEntity()
            .timeout(Duration.ofSeconds(writebackTimeoutSec))
            .block();

        log.info("ServiceNow updated for {} ({}) with AI suggestion",
            analysis.getNumber(), sysId);
    }

    private String safe(String val) {
        return val != null ? val : "N/A";
    }
}
