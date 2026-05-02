package com.aidbg.service.kafka;

import com.aidbg.model.IncidentAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for incident analysis results.
 * Extends AbstractKafkaProducer to reuse shared DLT and error handling.
 */
@Component
@Slf4j
public class AnalysisKafkaProducer extends AbstractKafkaProducer<IncidentAnalysis> {

    @Value("${kafka.topics.incident-analysis}")
    private String topic;

    @Value("${kafka.topics.incident-analysis-dlt}")
    private String dltTopic;

    public AnalysisKafkaProducer(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            ObjectMapper objectMapper) {
        super(kafkaTemplate, objectMapper);
    }

    @Override
    protected String getTopic(IncidentAnalysis item) {
        return topic;
    }

    @Override
    protected String getDltTopic() {
        return dltTopic;
    }

    @Override
    protected String getItemKey(IncidentAnalysis item) {
        return item.getSysId();
    }

    @Override
    protected String getItemNumber(IncidentAnalysis item) {
        return item.getNumber();
    }

    @Override
    protected void addCustomHeaders(ProducerRecord<String, byte[]> record, IncidentAnalysis item) {
        record.headers()
            .add("confidence", String.valueOf(item.getConfidence()).getBytes())
            .add("number",     item.getNumber().getBytes())
            .add("analyzedAt", item.getAnalyzedAt().toString().getBytes());
    }
}
