package com.aidbg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiDebuggingApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiDebuggingApplication.class, args);
    }
}
