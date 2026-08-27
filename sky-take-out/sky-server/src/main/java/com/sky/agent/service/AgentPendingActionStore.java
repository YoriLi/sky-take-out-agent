package com.sky.agent.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地兜底路径的「待确认写操作」暂存区，按员工保存一条待确认动作，带 TTL。
 * 用于实现二次确认：第一次收到写意图只登记不执行，收到确认后才真正调用 Service。
 */
@Component
public class AgentPendingActionStore {

    /** 待确认动作的有效期，超时自动失效 */
    private static final long TTL_MILLIS = 120_000L;

    /** 可抛受检异常的动作体（cancel/rejection 声明了 throws Exception） */
    public interface Action {
        String run() throws Exception;
    }

    public static final class Pending {
        public final String toolName;
        public final Map<String, Object> args;
        public final Action action;
        public final long createdAt;

        Pending(String toolName, Map<String, Object> args, Action action) {
            this.toolName = toolName;
            this.args = args;
            this.action = action;
            this.createdAt = System.currentTimeMillis();
        }

        boolean expired() {
            return System.currentTimeMillis() - createdAt > TTL_MILLIS;
        }
    }

    private final ConcurrentHashMap<Long, Pending> pendings = new ConcurrentHashMap<>();

    public void put(Long empId, String toolName, Map<String, Object> args, Action action) {
        if (empId == null) {
            return;
        }
        pendings.put(empId, new Pending(toolName, args, action));
    }

    /**
     * 查看但不移除；过期则清除并返回 null。
     */
    public Pending peek(Long empId) {
        if (empId == null) {
            return null;
        }
        Pending p = pendings.get(empId);
        if (p == null) {
            return null;
        }
        if (p.expired()) {
            pendings.remove(empId, p);
            return null;
        }
        return p;
    }

    /**
     * 取出并移除；过期则返回 null。
     */
    public Pending take(Long empId) {
        Pending p = peek(empId);
        if (p != null) {
            pendings.remove(empId, p);
        }
        return p;
    }

    public void clear(Long empId) {
        if (empId != null) {
            pendings.remove(empId);
        }
    }
}
