package com.aidbg.service.kafka;

import com.aidbg.model.EnrichedIncident;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Kafka producer for enriched incident events.
 * Extends AbstractKafkaProducer to reuse shared DLT and error handling.
 */
@Component
@Slf4j
public class IncidentKafkaProducer extends AbstractKafkaProducer<EnrichedIncident> {

    @Value("${kafka.topics.incident-events}")
    private String topic;

    @Value("${kafka.topics.incident-events-dlt}")
    private String dltTopic;

    public IncidentKafkaProducer(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            ObjectMapper objectMapper) {
        super(kafkaTemplate, objectMapper);
    }

    @Override
    protected String getTopic(EnrichedIncident item) {
        return topic;
    }

    @Override
    protected String getDltTopic() {
        return dltTopic;
    }

    @Override
    protected String getItemKey(EnrichedIncident item) {
        return item.getSysId();
    }

    @Override
    protected String getItemNumber(EnrichedIncident item) {
        return item.getNumber();
    }

    @Override
    protected void addCustomHeaders(ProducerRecord<String, byte[]> record, EnrichedIncident item) {
        record.headers()
            .add("priority",   item.getPriority().name().getBytes())
            .add("source",     "servicenow".getBytes())
            .add("enrichedAt", Instant.now().toString().getBytes());
    }
}
