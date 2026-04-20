package com.aidbg.controller;

import com.aidbg.model.IncidentEvent;
import com.aidbg.service.enrichment.EnrichmentService;
import com.aidbg.service.kafka.IncidentKafkaProducer;
import com.aidbg.service.validation.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final ValidationService     validationService;
    private final EnrichmentService     enrichmentService;
    private final IncidentKafkaProducer kafkaProducer;

    @Value("${webhook.secret}")
    private String secret;

    @PostMapping("/incident")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Secret-Token", required = false) String token,
            @RequestBody IncidentEvent event) {

        if (!secret.equals(token)) {
            log.warn("Unauthorized webhook call — invalid token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Received incident webhook: {}", event.getNumber());

        // Return 202 immediately — pipeline runs async
        CompletableFuture.runAsync(() -> {
            try {
                validationService.validate(event)
                    .map(enrichmentService::enrich)
                    .ifPresent(kafkaProducer::publish);
            } catch (Exception e) {
                log.error("Pipeline error for {}: {}", event.getNumber(), e.getMessage());
            }
        });

        return ResponseEntity.accepted().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }
}
