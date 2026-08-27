package com.sky.agent.service;

import com.sky.agent.config.AgentProperties;
import com.sky.agent.dto.AgentChatRequest;
import com.sky.agent.memory.ChatMemoryStore;
import com.sky.agent.skill.AgentFormat;
import com.sky.agent.skill.OrderSkill;
import com.sky.agent.skill.ReportSkill;
import com.sky.agent.skill.ShopSkill;
import com.sky.agent.skill.WorkspaceSkill;
import com.sky.agent.sse.AgentEventSink;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.exception.BaseException;
import com.sky.service.OrderService;
import com.sky.service.ReportService;
import com.sky.service.ShopService;
import com.sky.service.WorkspaceService;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 每请求构造一组 Skill（携带 sink + empId），用 AiServices.builder().tools(...) 现场装配。
 * Skill 用 new 构造、非 Spring 代理，因此其调用的 Service 仍是被 AOP 增强的代理对象。
 */
@Service
@Slf4j
public class AgentChatService {

    /** 单条用户输入长度上限，防止超长输入放大 Token 消耗 */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    @Autowired
    private AgentProperties properties;

    @Autowired
    private ChatMemoryStore chatMemoryStore;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private AgentRateLimiter rateLimiter;

    @Autowired
    private AgentPendingActionStore pendingActionStore;

    private volatile OpenAiStreamingChatModel streamingModel;

    public void chat(AgentChatRequest request, Long empId, AgentEventSink sink) {
        if (empId == null) {
            sink.error("未登录或登录已过期");
            return;
        }
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            sink.error("请输入要咨询的内容");
            return;
        }

        String message = request.getMessage().trim();
        if (message.length() > MAX_MESSAGE_LENGTH) {
            sink.error("输入过长，请精简后重试（上限 " + MAX_MESSAGE_LENGTH + " 字）");
            return;
        }

        int limitPerMinute = properties.getRateLimitPerMinute() == null ? 20 : properties.getRateLimitPerMinute();
        if (!rateLimiter.tryAcquire(empId, limitPerMinute)) {
            log.warn("[agent] rate limited empId={}", empId);
            sink.error("操作过于频繁，请稍后再试");
            return;
        }

        // 会话 id 与 empId 强绑定：外部传入只作为同一员工的子会话后缀，无法读取他人会话记忆
        String conversationId = scopedConversationId(empId, request.getConversationId());

        log.info("[agent] stream start empId={} conversationId={} message={}",
                empId, conversationId, abbreviate(message));

        if (!StringUtils.hasText(properties.getApiKey())) {
            log.info("[agent] SKY_AGENT_API_KEY empty, local route empId={}", empId);
            try {
                localChat(message, empId, sink);
                log.info("[agent] stream end empId={} mode=local", empId);
            } catch (Exception e) {
                log.error("[agent] local route failed empId={}", empId, e);
                sink.error("助手处理失败，请稍后重试");
            }
            return;
        }

        try {
            OrderSkill orderSkill = new OrderSkill(orderService, sink, empId);
            ReportSkill reportSkill = new ReportSkill(reportService, sink, empId);
            WorkspaceSkill workspaceSkill = new WorkspaceSkill(workspaceService, sink, empId);
            ShopSkill shopSkill = new ShopSkill(shopService, sink, empId);

            int maxMessages = properties.getMemoryMaxMessages() == null ? 20 : properties.getMemoryMaxMessages();
            AgentAssistant assistant = AiServices.builder(AgentAssistant.class)
                    .streamingChatLanguageModel(model())
                    .chatMemoryProvider(memoryId -> chatMemoryStore.getOrCreate(String.valueOf(memoryId), maxMessages))
                    .tools(orderSkill, reportSkill, workspaceSkill, shopSkill)
                    .build();

            TokenStream tokenStream = assistant.chat(conversationId, message);
            tokenStream
                    .onNext(new java.util.function.Consumer<String>() {
                        @Override
                        public void accept(String token) {
                            sink.text(token);
                        }
                    })
                    .onComplete(new java.util.function.Consumer<dev.langchain4j.model.output.Response<dev.langchain4j.data.message.AiMessage>>() {
                        @Override
                        public void accept(dev.langchain4j.model.output.Response<dev.langchain4j.data.message.AiMessage> response) {
                            log.info("[agent] stream end empId={} mode=llm", empId);
                            sink.end();
                        }
                    })
                    .onError(new java.util.function.Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable error) {
                            log.error("[agent] llm error empId={}", empId, error);
                            sink.error("助手调用失败，请稍后重试");
                        }
                    })
                    .start();
        } catch (Exception e) {
            log.error("[agent] start llm stream failed empId={}", empId, e);
            sink.error("助手暂时不可用，请稍后重试");
        }
    }

    /**
     * 未配置模型密钥时的本地兜底：查询直接执行；写操作（接单/拒单/取消/派送/完成/开关店）
     * 采用二次确认——第一次只登记待确认动作并追问，收到「确认」后才真正调用 Service，
     * 从根本上避免「能不能别打烊」这类关键词误触发写操作。
     */
    private void localChat(String text, Long empId, AgentEventSink sink) {
        BaseContext.setCurrentId(empId);
        try {
            // 1. 若存在待确认动作，优先解释为对上一步的确认/放弃
            AgentPendingActionStore.Pending pending = pendingActionStore.peek(empId);
            if (pending != null) {
                if (isConfirm(text)) {
                    String result = executePending(sink, empId, pendingActionStore.take(empId));
                    emitText(sink, result);
                    sink.end();
                    return;
                }
                if (isAbort(text)) {
                    pendingActionStore.clear(empId);
                    emitText(sink, "已放弃该操作。");
                    sink.end();
                    return;
                }
                // 既非确认也非放弃：视为改变主意，清掉旧的待确认，继续解析新输入
                pendingActionStore.clear(empId);
            }

            String reply;
            if (containsAny(text, "你好", "您好", "你是谁", "你能做什么", "hi", "hello")) {
                reply = "你好，我是苍穹外卖老板助手（本地模式）。可直接查询：今日营业额、店铺营业状态、订单总览、按手机号或状态查订单；"
                        + "接单、拒单、取消、派送、完成、开关店等修改类操作会先向你二次确认再执行。";
            } else if (containsAny(text, "营业额", "营收", "生意怎么样", "今日数据", "今天数据")) {
                reply = invokeLocalRead("getTodayBusinessData", sink, empId, () -> {
                    LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);
                    LocalDateTime end = LocalDateTime.now().with(LocalTime.MAX);
                    return AgentFormat.businessData(workspaceService.getBusinessData(begin, end));
                });
            } else if (containsAny(text, "营业吗", "营业中", "开店了", "店铺状态", "现在营业")) {
                reply = invokeLocalRead("getShopStatus", sink, empId, () -> {
                    Integer status = shopService.getStatus();
                    return (status != null && status == 1) ? "当前店铺营业中（status=1）" : "当前店铺已打烊（status=0）";
                });
            } else if (containsAny(text, "订单总览", "待处理", "积压")) {
                reply = invokeLocalRead("getOrderOverview", sink, empId,
                        () -> AgentFormat.orderOverview(workspaceService.getOrderOverView()));
            } else if (containsAny(text, "待接单", "查订单", "查一下订单", "查找订单")
                    || text.matches(".*1[3-9]\\d{9}.*")) {
                final String phone = extractPhone(text);
                final Integer status = text.contains("待接单") ? 2 : null;
                reply = invokeLocalRead("searchOrders", sink, empId, () -> {
                    OrdersPageQueryDTO dto = new OrdersPageQueryDTO();
                    dto.setPage(1);
                    dto.setPageSize(10);
                    dto.setPhone(phone);
                    dto.setStatus(status);
                    return AgentFormat.orders(orderService.conditionSearch(dto));
                });
            } else {
                // 写意图 → 登记待确认；非写意图 → 帮助文案
                String confirmPrompt = prepareWriteConfirmation(text, empId);
                reply = confirmPrompt != null ? confirmPrompt
                        : "本地模式支持：查询（营业额/店铺状态/订单总览/查订单）以及需二次确认的修改（接单/拒单/取消/派送/完成/开关店）。"
                        + "试试「今天营业额怎么样」或「接单 1001」。";
            }
            emitText(sink, reply);
            sink.end();
        } finally {
            BaseContext.removeCurrentId();
        }
    }

    /**
     * 解析写意图并登记待确认动作，返回给用户的确认追问；若缺少必要参数则返回澄清提示（不登记）；
     * 非写意图返回 null。所有分支都不会立即执行写操作。
     */
    private String prepareWriteConfirmation(String text, Long empId) {
        if (containsAny(text, "打烊", "关店", "停止营业")) {
            pendingActionStore.put(empId, "setShopStatus", mapOf("status", 0), () -> {
                shopService.setStatus(0);
                return "已更新，店铺已打烊（status=0）";
            });
            return "⚠️ 即将将店铺设为【打烊】。确认请回复「确认」，放弃请回复「取消」。";
        }
        if (containsAny(text, "开始营业", "开门", "开业", "设为营业")) {
            pendingActionStore.put(empId, "setShopStatus", mapOf("status", 1), () -> {
                shopService.setStatus(1);
                return "已更新，店铺营业中（status=1）";
            });
            return "⚠️ 即将将店铺设为【营业】。确认请回复「确认」，放弃请回复「取消」。";
        }

        Long id = extractId(text);
        if (containsAny(text, "接单")) {
            if (id == null) {
                return "接单需要订单数字 id，例如「接单 1001」。";
            }
            final Long orderId = id;
            pendingActionStore.put(empId, "confirmOrder", mapOf("id", orderId), () -> {
                OrdersConfirmDTO dto = new OrdersConfirmDTO();
                dto.setId(orderId);
                orderService.confirm(dto);
                return "已接单，订单 id=" + orderId;
            });
            return "⚠️ 即将【接单】订单 id=" + orderId + "。确认请回复「确认」，放弃请回复「取消」。";
        }
        if (containsAny(text, "派送")) {
            if (id == null) {
                return "派送需要订单数字 id，例如「派送 1001」。";
            }
            final Long orderId = id;
            pendingActionStore.put(empId, "deliverOrder", mapOf("id", orderId), () -> {
                orderService.delivery(orderId);
                return "已开始派送，订单 id=" + orderId;
            });
            return "⚠️ 即将将订单 id=" + orderId + " 置为【派送中】。确认请回复「确认」，放弃请回复「取消」。";
        }
        if (containsAny(text, "完成")) {
            if (id == null) {
                return "完成订单需要订单数字 id，例如「完成 1001」。";
            }
            final Long orderId = id;
            pendingActionStore.put(empId, "completeOrder", mapOf("id", orderId), () -> {
                orderService.complete(orderId);
                return "订单已完成，id=" + orderId;
            });
            return "⚠️ 即将将订单 id=" + orderId + " 置为【已完成】。确认请回复「确认」，放弃请回复「取消」。";
        }
        if (containsAny(text, "拒单")) {
            if (id == null) {
                return "拒单需要订单 id 和原因，例如「拒单 1001 商品售罄」。";
            }
            final String reason = extractReason(text, id);
            if (reason == null) {
                return "拒单必须填写原因，例如「拒单 " + id + " 商品售罄」。";
            }
            final Long orderId = id;
            pendingActionStore.put(empId, "rejectOrder", mapOf("id", orderId, "reason", reason), () -> {
                OrdersRejectionDTO dto = new OrdersRejectionDTO();
                dto.setId(orderId);
                dto.setRejectionReason(reason);
                orderService.rejection(dto);
                return "已拒单，订单 id=" + orderId;
            });
            return "⚠️ 即将【拒单】订单 id=" + orderId + "，原因：" + reason + "。确认请回复「确认」，放弃请回复「取消」。";
        }
        if (containsAny(text, "取消")) {
            if (id == null) {
                return "取消订单需要订单 id 和原因，例如「取消 1001 顾客要求」。";
            }
            final String reason = extractReason(text, id);
            if (reason == null) {
                return "取消订单必须填写原因，例如「取消 " + id + " 顾客要求」。";
            }
            final Long orderId = id;
            pendingActionStore.put(empId, "cancelOrder", mapOf("id", orderId, "reason", reason), () -> {
                OrdersCancelDTO dto = new OrdersCancelDTO();
                dto.setId(orderId);
                dto.setCancelReason(reason);
                orderService.cancel(dto);
                return "已取消，订单 id=" + orderId;
            });
            return "⚠️ 即将【取消】订单 id=" + orderId + "，原因：" + reason + "。确认请回复「确认」，放弃请回复「取消」。";
        }
        return null;
    }

    private String executePending(AgentEventSink sink, Long empId, AgentPendingActionStore.Pending pending) {
        if (pending == null) {
            return "没有待确认的操作，或已超时失效，请重新发起。";
        }
        sink.toolCall(pending.toolName, pending.args);
        log.info("[agent] tool start name={} empId={} mode=local-confirmed args={}", pending.toolName, empId, pending.args);
        String result;
        try {
            String value = pending.action.run();
            result = (value == null || value.trim().isEmpty()) ? "操作成功" : value;
        } catch (BaseException e) {
            result = "操作失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[agent] local tool {} 执行异常 empId={}", pending.toolName, empId, e);
            result = "操作失败：系统异常，请稍后重试";
        }
        result = truncate(result, 2000);
        sink.toolResult(pending.toolName, result);
        log.info("[agent] tool end name={} empId={} mode=local-confirmed result={}", pending.toolName, empId, truncate(result, 200));
        return result;
    }

    private boolean isConfirm(String text) {
        String t = text.trim();
        return containsAny(t, "确认", "确定", "执行", "是的", "没错")
                || t.equalsIgnoreCase("yes") || t.equalsIgnoreCase("y") || t.equalsIgnoreCase("ok");
    }

    private boolean isAbort(String text) {
        return containsAny(text, "取消", "放弃", "算了", "不用", "不了", "先不", "别");
    }

    private String invokeLocalRead(String name, AgentEventSink sink, Long empId, CallableResult body) {
        sink.toolCall(name, new LinkedHashMap<String, Object>());
        log.info("[agent] tool start name={} empId={} mode=local", name, empId);
        String result;
        try {
            Object value = body.call();
            result = value == null ? "操作成功" : String.valueOf(value);
        } catch (BaseException e) {
            result = "操作失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[agent] local tool {} 执行异常", name, e);
            result = "操作失败：系统异常，请稍后重试";
        }
        result = truncate(result, 2000);
        sink.toolResult(name, result);
        log.info("[agent] tool end name={} empId={} mode=local", name, empId);
        return result;
    }

    private interface CallableResult {
        Object call() throws Exception;
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        if (kv == null) {
            return map;
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private Long extractId(String text) {
        Matcher m = Pattern.compile("(?:接单|拒单|取消|派送|完成|订单\\s*id|id)\\s*[:：#=]?\\s*(\\d{1,12})",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            return Long.valueOf(m.group(1));
        }
        Matcher only = Pattern.compile("(\\d{1,12})").matcher(text);
        if (only.find()) {
            return Long.valueOf(only.group(1));
        }
        return null;
    }

    /**
     * 提取订单 id 之后的文字作为原因，去掉分隔符与「原因」前缀。
     */
    private String extractReason(String text, Long id) {
        String idStr = String.valueOf(id);
        int idx = text.indexOf(idStr);
        String tail = idx >= 0 ? text.substring(idx + idStr.length()) : text;
        tail = tail.replaceFirst("^[\\s，,。.:：#、]+", "").trim();
        tail = tail.replaceFirst("^原因[:：]?\\s*", "").trim();
        return tail.isEmpty() ? null : tail;
    }

    /**
     * 生成与 empId 绑定的会话 id：客户端传入值仅作为该员工名下的子会话标识，无法跨员工命中记忆。
     */
    private String scopedConversationId(Long empId, String rawConversationId) {
        if (!StringUtils.hasText(rawConversationId)) {
            return "emp:" + empId;
        }
        String sub = rawConversationId.trim();
        if (sub.length() > 64) {
            sub = sub.substring(0, 64);
        }
        return "emp:" + empId + ":" + sub;
    }

    private boolean containsAny(String text, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            if (text.contains(keys[i])) {
                return true;
            }
        }
        return false;
    }

    private String extractPhone(String text) {
        Matcher m = Pattern.compile("(1[3-9]\\d{9})").matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private void emitText(AgentEventSink sink, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + 16, text.length());
            sink.text(text.substring(i, end));
            i = end;
        }
    }

    private OpenAiStreamingChatModel model() {
        if (streamingModel != null) {
            return streamingModel;
        }
        synchronized (this) {
            if (streamingModel == null) {
                int timeout = properties.getTimeoutSeconds() == null ? 120 : properties.getTimeoutSeconds();
                streamingModel = OpenAiStreamingChatModel.builder()
                        .baseUrl(properties.getBaseUrl())
                        .apiKey(properties.getApiKey())
                        .modelName(properties.getModel())
                        .timeout(Duration.ofSeconds(timeout))
                        .build();
            }
            return streamingModel;
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…（已截断）";
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }
}
