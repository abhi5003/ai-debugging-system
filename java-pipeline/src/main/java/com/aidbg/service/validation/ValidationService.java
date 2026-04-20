package com.aidbg.service.validation;

import com.aidbg.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class ValidationService {

    private static final Set<String> SKIP_PRIORITIES = Set.of("4", "5");
    private static final DateTimeFormatter SN_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Optional<NormalizedIncident> validate(IncidentEvent raw) {

        // Stage 1 — required field checks
        if (!StringUtils.hasText(raw.getSysId())) {
            log.warn("Dropping incident — missing sysId");
            return Optional.empty();
        }
        if (!StringUtils.hasText(raw.getShortDescription())) {
            log.warn("Dropping {} — missing shortDescription", raw.getNumber());
            return Optional.empty();
        }

        // Stage 2 — business rule: skip low-priority
        if (SKIP_PRIORITIES.contains(raw.getPriority())) {
            log.info("Skipping {} — priority {} below threshold",
                raw.getNumber(), raw.getPriority());
            return Optional.empty();
        }

        // Stage 3 — normalize
        try {
            NormalizedIncident incident = NormalizedIncident.builder()
                .sysId(raw.getSysId().trim())
                .number(StringUtils.hasText(raw.getNumber()) ? raw.getNumber().trim() : "UNKNOWN")
                .shortDescription(raw.getShortDescription().trim())
                .priority(Priority.fromServiceNow(raw.getPriority()))
                .state(IncidentState.fromServiceNow(raw.getState()))
                .assignedTo(raw.getAssignedTo())
                .configurationItem(raw.getCmdbCi())
                .updatedAt(parseTimestamp(raw.getUpdatedAt()))
                .build();

            log.info("Validated {} → priority={}", incident.getNumber(), incident.getPriority());
            return Optional.of(incident);

        } catch (Exception e) {
            log.error("Normalization failed for {}: {}", raw.getNumber(), e.getMessage());
            return Optional.empty();
        }
    }

    private Instant parseTimestamp(String ts) {
        if (!StringUtils.hasText(ts)) return Instant.now();
        try {
            return LocalDateTime.parse(ts, SN_FMT).toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
