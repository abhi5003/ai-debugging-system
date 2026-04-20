package com.aidbg.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.incident-events}")      private String incidentEventsTopic;
    @Value("${kafka.topics.incident-events-dlt}")  private String incidentEventsDlt;
    @Value("${kafka.topics.incident-analysis}")    private String incidentAnalysisTopic;
    @Value("${kafka.topics.incident-analysis-dlt}") private String incidentAnalysisDlt;

    @Bean public NewTopic incidentEventsTopic() {
        return TopicBuilder.name(incidentEventsTopic)
            .partitions(3).replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG,
                String.valueOf(Duration.ofDays(7).toMillis()))
            .build();
    }

    @Bean public NewTopic incidentEventsDltTopic() {
        return TopicBuilder.name(incidentEventsDlt).partitions(1).replicas(1).build();
    }

    @Bean public NewTopic incidentAnalysisTopic() {
        return TopicBuilder.name(incidentAnalysisTopic)
            .partitions(3).replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG,
                String.valueOf(Duration.ofDays(30).toMillis()))
            .build();
    }

    @Bean public NewTopic incidentAnalysisDltTopic() {
        return TopicBuilder.name(incidentAnalysisDlt).partitions(1).replicas(1).build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]>
            kafkaListenerContainerFactory(ConsumerFactory<String, byte[]> cf,
                                          KafkaTemplate<String, byte[]> kt) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(
            new DefaultErrorHandler(new FixedBackOff(2000L, 3L)));
        return factory;
    }
}
