package com.aidbg.service.kafka;

import com.aidbg.model.EnrichedIncident;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncidentKafkaProducer {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.incident-events}")      private String topic;
    @Value("${kafka.topics.incident-events-dlt}")  private String dltTopic;

    public void publish(EnrichedIncident incident) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(incident);
            ProducerRecord<String, byte[]> record =
                new ProducerRecord<>(topic, incident.getSysId(), payload);

            record.headers()
                .add("priority",    incident.getPriority().name().getBytes())
                .add("source",      "servicenow".getBytes())
                .add("enrichedAt",  Instant.now().toString().getBytes());

            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Kafka send failed for {}: {}", incident.getNumber(), ex.getMessage());
                    sendToDlt(incident.getSysId(), payload, ex.getMessage());
                } else {
                    log.info("Published {} → partition={} offset={}",
                        incident.getNumber(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });

        } catch (JsonProcessingException e) {
            log.error("Serialization failed for {}: {}", incident.getNumber(), e.getMessage());
        }
    }

    private void sendToDlt(String key, byte[] payload, String reason) {
        ProducerRecord<String, byte[]> dlt = new ProducerRecord<>(dltTopic, key, payload);
        dlt.headers().add("failure-reason", reason.getBytes());
        kafkaTemplate.send(dlt);
        log.warn("Sent to DLT key={}", key);
    }
}
