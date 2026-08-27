package com.sky.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 线程池：SSE 必须先返回 SseEmitter，再在独立线程里跑模型/Skill，否则无法流式写出。
 */
@Configuration
public class AgentConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService agentExecutor() {
        final AtomicInteger seq = new AtomicInteger(1);
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "agent-sse-" + seq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        return Executors.newCachedThreadPool(factory);
    }
}
