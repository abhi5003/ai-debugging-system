package com.aidbg.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Template Method helper for Dead Letter Topic listeners.
 *
 * Both DLT listeners share identical structure. Callers invoke
 * {@code handle(record, ack, onPayload)} and only supply the
 * topic-specific onPayload callback.
 */
@Component
@Slf4j
public class DltHandler {

    /**
     * Process a DLT message with shared error handling.
     *
     * @param record    the Kafka consumer record
     * @param ack       the acknowledgment
     * @param onPayload callback for domain-specific payload handling
     */
    public void handle(ConsumerRecord<String, byte[]> record,
                       Acknowledgment ack,
                       PayloadHandler onPayload) {
        log.error("DLT received sysId={} partition={} offset={} -- message permanently failed processing",
            record.key(), record.partition(), record.offset());

        try {
            String payload = new String(record.value());
            log.error("Failed payload: {}", payload);
            onPayload.accept(record.key(), payload);
        } catch (Exception e) {
            log.error("DLT payload parse error: {}", e.getMessage());
        }

        ack.acknowledge();
    }

    @FunctionalInterface
    public interface PayloadHandler {
        void accept(String sysId, String payload);
    }
}
