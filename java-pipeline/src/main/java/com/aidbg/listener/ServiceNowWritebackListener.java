package com.aidbg.listener;

import com.aidbg.model.IncidentAnalysis;
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
    
        try {
            IncidentAnalysis analysis =
                objectMapper.readValue(record.value(), IncidentAnalysis.class);
    
            // ✅ Defensive defaults
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

        List<String> actions = analysis.getImmediateActions() != null
            ? analysis.getImmediateActions() : List.of();
    
        String actionsList = IntStream.range(0, actions.size())
            .mapToObj(i -> (i + 1) + ". " + actions.get(i))
            .collect(Collectors.joining("\n"));
    
        String workNote = String.format(
            "🤖 AI Suggested Analysis (Needs Review)\n\n" +
            "Confidence: %.0f%%\n\n" +
            "Root Cause:\n%s\n\n" +
            "Resolution:\n%s\n\n" +
            "Immediate Actions:\n%s\n\n" +
            "Similar Incidents: %s\n\n" +
            "⚠ Please ACCEPT or REJECT this suggestion.",
            safePercent(analysis.getConfidence()),
            safe(analysis.getRootCause()),
            safe(analysis.getResolution()),
            actionsList,
            analysis.getSimilarIncidentNumbers() != null
                ? String.join(", ", analysis.getSimilarIncidentNumbers()) : "none"
        );
    
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
            .timeout(Duration.ofSeconds(15))
            .block();
    
        log.info("ServiceNow updated for {} ({}) with AI suggestion",
            analysis.getNumber(), sysId);
    }

    private String safe(String val) {
        return val != null ? val : "N/A";
    }
    
    private double safePercent(Double val) {
        return val != null ? val * 100 : 0.0;
    }
}
