package com.sky.agent.sse;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 串行化写 SSE，end/error 之后幂等关闭，禁止再发 text。
 */
@Slf4j
public class AgentEventSink {

    private final SseEmitter emitter;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public AgentEventSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public synchronized void text(String delta) {
        if (closed.get() || delta == null || delta.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("type", "text");
        payload.put("content", delta);
        send(payload);
    }

    public synchronized void toolCall(String name, Map<String, Object> args) {
        if (closed.get()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("type", "tool_call");
        payload.put("name", name);
        payload.put("arguments", args == null ? new LinkedHashMap<String, Object>() : args);
        send(payload);
    }

    public synchronized void toolResult(String name, String result) {
        if (closed.get()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("type", "tool_result");
        payload.put("name", name);
        payload.put("result", result == null ? "" : result);
        send(payload);
    }

    public synchronized void end() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("type", "end");
        sendUnlocked(payload);
        completeQuietly();
    }

    public synchronized void error(String userMessage) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("type", "error");
        payload.put("message", userMessage == null ? "助手暂时不可用，请稍后重试" : userMessage);
        sendUnlocked(payload);
        completeQuietly();
    }

    private void send(Map<String, Object> payload) {
        if (closed.get()) {
            return;
        }
        sendUnlocked(payload);
    }

    private void sendUnlocked(Map<String, Object> payload) {
        String json = JSON.toJSONString(payload,
                SerializerFeature.DisableCircularReferenceDetect,
                SerializerFeature.BrowserCompatible);
        try {
            MediaType jsonUtf8 = new MediaType("application", "json", StandardCharsets.UTF_8);
            emitter.send(SseEmitter.event().data(json, jsonUtf8));
        } catch (IOException e) {
            log.warn("[agent] sse send failed, closing. type={}", payload.get("type"));
            closed.set(true);
            completeQuietly();
        } catch (IllegalStateException e) {
            log.warn("[agent] sse already completed, type={}", payload.get("type"));
            closed.set(true);
        }
    }

    private void completeQuietly() {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("[agent] emitter complete ignored: {}", e.getMessage());
        }
    }
}
