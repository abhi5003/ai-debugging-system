package com.aidbg.controller;

import com.aidbg.model.FeedbackEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook/feedback")
@RequiredArgsConstructor
@Slf4j
public class FeedbackController {

    @Value("${kafka.topics.incident-feedback}")
    private String feedbackTopic;
    private final KafkaTemplate<String, FeedbackEvent> kafkaTemplate;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody FeedbackEvent event) {

        log.info("Feedback received incident={} accepted={}",
                event.getIncidentNumber(), event.isAccepted());

        kafkaTemplate.send(feedbackTopic, event.getSysId(), event);

        return ResponseEntity.ok().build();
    }
}