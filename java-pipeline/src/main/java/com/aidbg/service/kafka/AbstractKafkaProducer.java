package com.aidbg.service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Template Method pattern for Kafka producers with shared DLT and
 * error-handling logic.
 *
 * Previously IncidentKafkaProducer and AnalysisKafkaProducer each
 * implemented identical send-to-DLT and whenComplete error handling.
 * Subclasses only need to supply the payload, key, topic names, and
 * custom headers.
 */
@Slf4j
public abstract class AbstractKafkaProducer<T> {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;

    protected AbstractKafkaProducer(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(T item) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(item);
            String key = getItemKey(item);
            String topic = getTopic(item);
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, payload);
            addCustomHeaders(record, item);

            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Kafka send failed for {}: {}", getItemNumber(item), ex.getMessage());
                    sendToDlt(key, payload, ex.getMessage());
                } else {
                    log.info("Published {} → partition={} offset={}",
                        getItemNumber(item),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });

        } catch (JsonProcessingException e) {
            log.error("Serialization failed for {}: {}", getItemNumber(item), e.getMessage());
        }
    }

    private void sendToDlt(String key, byte[] payload, String reason) {
        ProducerRecord<String, byte[]> dlt = new ProducerRecord<>(getDltTopic(), key, payload);
        dlt.headers().add("failure-reason", reason.getBytes());
        kafkaTemplate.send(dlt);
        log.warn("Sent to DLT key={}", key);
    }

    /** Subclasses provide the Kafka topic for this item type. */
    protected abstract String getTopic(T item);

    /** Subclasses provide the DLT topic. */
    protected abstract String getDltTopic();

    /** Subclasses provide the Kafka message key (typically sys_id). */
    protected abstract String getItemKey(T item);

    /** Subclasses provide the human-readable identifier for logging. */
    protected abstract String getItemNumber(T item);

    /** Subclasses add type-specific headers. */
    protected abstract void addCustomHeaders(ProducerRecord<String, byte[]> record, T item);
}
