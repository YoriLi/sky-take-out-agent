package com.sky.agent.controller;

import com.sky.agent.dto.AgentChatRequest;
import com.sky.agent.service.AgentChatService;
import com.sky.agent.sse.AgentEventSink;
import com.sky.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;

/**
 * 老板 AI 助手 SSE 入口。只做参数校验与建连，零 AI 逻辑。
 * 鉴权完全复用 /admin/** 的 JWT 拦截器。
 */
@RestController
@RequestMapping("/admin/agent")
@Slf4j
public class AgentController {

    @Autowired
    private AgentChatService agentChatService;

    @Autowired
    private ExecutorService agentExecutor;

    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream(@RequestBody(required = false) AgentChatRequest request) {
        long timeoutMs = 180000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);
        final AgentEventSink sink = new AgentEventSink(emitter);
        final Long empId = BaseContext.getCurrentId();
        final AgentChatRequest body = request;

        emitter.onTimeout(new Runnable() {
            @Override
            public void run() {
                log.warn("[agent] sse timeout empId={}", empId);
                sink.error("请求超时，请稍后重试");
            }
        });
        emitter.onError(new java.util.function.Consumer<Throwable>() {
            @Override
            public void accept(Throwable ex) {
                log.warn("[agent] sse connection error empId={}", empId);
                sink.error("连接异常，请稍后重试");
            }
        });

        if (empId == null) {
            sink.error("未登录或登录已过期");
            return emitter;
        }
        if (body == null || !StringUtils.hasText(body.getMessage())) {
            sink.error("请输入要咨询的内容");
            return emitter;
        }

        agentExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    agentChatService.chat(body, empId, sink);
                } catch (Exception e) {
                    log.error("[agent] stream worker failed empId={}", empId, e);
                    sink.error("助手暂时不可用，请稍后重试");
                }
            }
        });
        return emitter;
    }
}
