package com.aidbg.service.processor;

import com.aidbg.model.EnrichedIncident;
import com.aidbg.model.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeduplicationService {

    private final StringRedisTemplate redisTemplate;

    @Value("${processor.dedup-ttl-minutes:10}")
    private long ttlMinutes;

    private static final String PREFIX = "incident:seen:";

    public boolean isNew(String sysId) {
        String key = PREFIX + sysId;
        Boolean isNew = redisTemplate.opsForValue()
            .setIfAbsent(key, "1", Duration.ofMinutes(ttlMinutes));
        if (Boolean.FALSE.equals(isNew)) {
            log.debug("Duplicate suppressed: {}", sysId);
            return false;
        }
        return true;
    }
}
