package com.sky.agent.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 按员工的固定窗口限流器：每分钟窗口内计数，超过上限则拒绝。
 * 用于防止恶意/异常客户端刷 /admin/agent/stream 耗尽模型 Token 配额。
 */
@Component
public class AgentRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private static final class Window {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);
    }

    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    /**
     * @return true 表示允许本次请求；false 表示已超过每分钟上限
     */
    public boolean tryAcquire(Long empId, int limitPerMinute) {
        if (empId == null) {
            return false;
        }
        if (limitPerMinute <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Window w = windows.computeIfAbsent(empId, k -> {
            Window nw = new Window();
            nw.windowStart = now;
            return nw;
        });
        synchronized (w) {
            if (now - w.windowStart >= WINDOW_MILLIS) {
                w.windowStart = now;
                w.count.set(0);
            }
            if (w.count.get() >= limitPerMinute) {
                return false;
            }
            w.count.incrementAndGet();
            return true;
        }
    }
}
