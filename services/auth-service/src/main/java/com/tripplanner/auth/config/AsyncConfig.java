// Source: 02-RESEARCH.md §Pattern 5 lines 567-581 + 02-CONTEXT.md D-01 (pool sizes), D-22 (MDC propagation).
// MdcCopyingTaskDecorator stays inline (NOT extracted to libs/observability) — only auth-service uses
// @Async in v1; Phase 3+ trigger may extract. See 02-PATTERNS.md commentary §AsyncConfig.
package com.tripplanner.auth.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "authAsyncExecutor")
    public ThreadPoolTaskExecutor authAsyncExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);                                  // D-01
        ex.setMaxPoolSize(4);                                   // D-01
        ex.setQueueCapacity(50);                                // D-01
        ex.setThreadNamePrefix("auth-async-");
        ex.setTaskDecorator(new MdcCopyingTaskDecorator());     // D-22
        ex.initialize();
        return ex;
    }

    /** Copy MDC from submitting thread into worker; restore on completion. Pitfall 7 servlet-side answer. */
    static class MdcCopyingTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> ctx = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (ctx != null) MDC.setContextMap(ctx);
                    runnable.run();
                } finally {
                    if (previous != null) MDC.setContextMap(previous);
                    else MDC.clear();
                }
            };
        }
    }
}
