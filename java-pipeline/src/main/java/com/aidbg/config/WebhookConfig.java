package com.aidbg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a dedicated executor for the async webhook pipeline.
 *
 * Previously WebhookController used CompletableFuture.runAsync() without
 * an executor, falling back to ForkJoinPool.commonPool(). Under load
 * this starves other async operations in the JVM. This dedicated
 * ThreadPoolExecutor isolates webhook processing.
 */
@Configuration
public class WebhookConfig {

    @Bean("webhookPipelineExecutor")
    public Executor webhookPipelineExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "webhook-pipeline-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
