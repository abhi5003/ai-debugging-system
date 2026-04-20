package com.aidbg.service.kafka;

import com.aidbg.model.IncidentAnalysis;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisKafkaProducer {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.incident-analysis}")      private String topic;
    @Value("${kafka.topics.incident-analysis-dlt}")  private String dltTopic;

    public void publish(IncidentAnalysis analysis) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(analysis);
            ProducerRecord<String, byte[]> record =
                new ProducerRecord<>(topic, analysis.getSysId(), payload);

            record.headers()
                .add("confidence",  String.valueOf(analysis.getConfidence()).getBytes())
                .add("number",      analysis.getNumber().getBytes())
                .add("analyzedAt",  analysis.getAnalyzedAt().toString().getBytes());

            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Analysis publish failed for {}: {}",
                        analysis.getNumber(), ex.getMessage());
                    sendToDlt(analysis.getSysId(), payload, ex.getMessage());
                } else {
                    log.info("Analysis published for {} confidence={} → partition={} offset={}",
                        analysis.getNumber(), analysis.getConfidence(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });

        } catch (JsonProcessingException e) {
            log.error("Analysis serialization failed for {}: {}",
                analysis.getNumber(), e.getMessage());
        }
    }

    private void sendToDlt(String key, byte[] payload, String reason) {
        ProducerRecord<String, byte[]> dlt = new ProducerRecord<>(dltTopic, key, payload);
        dlt.headers().add("failure-reason", reason.getBytes());
        kafkaTemplate.send(dlt);
    }
}
