package com.aidbg.listener;

import com.aidbg.model.FeedbackEvent;
import com.aidbg.service.dispatcher.LearningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedbackListener {

    private final LearningService learningService;

    @KafkaListener(
        topics = "${kafka.topics.incident-feedback}",
        groupId = "feedback-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handle(FeedbackEvent event) {
    
        log.info("Processing feedback incident={} accepted={}",
            event.getIncidentNumber(), event.isAccepted());
    
        try {
            if (event.isAccepted()) {
                learningService.updateFeedback(event.getSysId(), true);
                learningService.storeAI(event);
            } else {
                learningService.updateFeedback(event.getSysId(), false);
                learningService.storeHuman(event);
            }
        } catch (Exception e) {
            log.error("Feedback processing failed: {}", e.getMessage());
            throw e; // ✅ ensures retry
        }
    }
    
}