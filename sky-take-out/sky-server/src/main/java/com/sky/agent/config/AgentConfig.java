package com.sky.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 线程池：SSE 必须先返回 SseEmitter，再在独立线程里跑模型/Skill，否则无法流式写出。
 * 使用有界线程池 + 有界队列，避免无界 newCachedThreadPool 在高并发下耗尽线程；
 * 满载时抛 RejectedExecutionException，由 AgentController 捕获并回一条 error 事件。
 */
@Configuration
public class AgentConfig {

    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 32;
    private static final int QUEUE_CAPACITY = 100;
    private static final long KEEP_ALIVE_SECONDS = 60L;

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
        return new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(QUEUE_CAPACITY),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
