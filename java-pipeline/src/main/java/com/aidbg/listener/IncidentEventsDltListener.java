package com.aidbg.listener;

import com.aidbg.listener.DltHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncidentEventsDltListener {

    private final DltHandler dltHandler;

    @KafkaListener(
        topics = "${kafka.topics.incident-events-dlt}",
        groupId = "incident-events-dlt-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDltEvent(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
        dltHandler.handle(record, ack, (sysId, payload) -> {
            log.error("Incident event permanently failed sysId={}", sysId);
        });
    }
}
