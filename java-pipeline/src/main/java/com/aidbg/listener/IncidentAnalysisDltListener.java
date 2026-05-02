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
public class IncidentAnalysisDltListener {

    private final DltHandler dltHandler;

    @KafkaListener(
        topics = "${kafka.topics.incident-analysis-dlt}",
        groupId = "incident-analysis-dlt-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDltAnalysis(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
        dltHandler.handle(record, ack, (sysId, payload) -> {
            log.error("Analysis writeback permanently failed sysId={}", sysId);
        });
    }
}
