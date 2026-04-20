package com.aidbg.listener;

import com.aidbg.model.EnrichedIncident;
import com.aidbg.model.Severity;
import com.aidbg.service.dispatcher.RagDispatcher;
import com.aidbg.service.processor.DeduplicationService;
import com.aidbg.service.processor.PriorityScorer;
import com.aidbg.service.processor.SeverityRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncidentEventListener {

    private final ObjectMapper         objectMapper;
    private final DeduplicationService dedupService;
    private final PriorityScorer       scorer;
    private final SeverityRouter       router;
    private final RagDispatcher        ragDispatcher;

    @KafkaListener(
        topics           = "${kafka.topics.incident-events}",
        groupId          = "processor-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEvent(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
        String sysId = record.key();
        log.info("Consumed event sysId={} partition={} offset={}",
            sysId, record.partition(), record.offset());

        try {
            EnrichedIncident incident =
                objectMapper.readValue(record.value(), EnrichedIncident.class);

            // Dedup via Redis
            if (!dedupService.isNew(sysId)) {
                ack.acknowledge();
                return;
            }

            // Score + route
            int      score    = scorer.score(incident);
            Severity severity = router.route(score);
            log.info("Score={} severity={} for {}", score, severity, incident.getNumber());

            // Dispatch to Python RAG
            ragDispatcher.dispatch(incident, severity);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Listener error for sysId={}: {}", sysId, e.getMessage());
            // No ack — Kafka redelivers
        }
    }
}
