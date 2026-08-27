package com.sky.agent.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.agent.config.AgentProperties;
import com.sky.agent.dto.AgentChatRequest;
import com.sky.agent.memory.ChatMemoryStore;
import com.sky.agent.sse.AgentEventSink;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.exception.BaseException;
import com.sky.service.OrderService;
import com.sky.service.ReportService;
import com.sky.service.ShopService;
import com.sky.service.WorkspaceService;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.OrderOverViewVO;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 每请求扫描现有 Service 上的 @Tool，用 DefaultToolExecutor 直调原方法。
 */
@Service
@Slf4j
public class AgentChatService {

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
        String conversationId = StringUtils.hasText(request.getConversationId())
                ? request.getConversationId().trim()
                : String.valueOf(empId);

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
            Map<ToolSpecification, ToolExecutor> tools = collectTools(sink, empId);
            AgentAssistant assistant = AiServices.builder(AgentAssistant.class)
                    .streamingChatLanguageModel(model())
                    .chatMemoryProvider(memoryId -> chatMemoryStore.getOrCreate(
                            String.valueOf(memoryId),
                            properties.getMemoryMaxMessages() == null ? 20 : properties.getMemoryMaxMessages()))
                    .tools(tools)
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

    private Map<ToolSpecification, ToolExecutor> collectTools(AgentEventSink sink, Long empId) {
        Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<ToolSpecification, ToolExecutor>();
        registerTools(orderService, sink, empId, map);
        registerTools(reportService, sink, empId, map);
        registerTools(workspaceService, sink, empId, map);
        registerTools(shopService, sink, empId, map);
        return map;
    }

    private void registerTools(Object bean, AgentEventSink sink, Long empId,
                               Map<ToolSpecification, ToolExecutor> map) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        Object target = AopProxyUtils.getSingletonTarget(bean);
        if (target == null) {
            target = bean;
        }
        Method[] methods = targetClass.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.getAnnotation(Tool.class) == null) {
                continue;
            }
            method.setAccessible(true);
            ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
            final DefaultToolExecutor delegate = new DefaultToolExecutor(target, method);
            map.put(spec, wrap(spec.name(), delegate, sink, empId));
        }
    }

    private ToolExecutor wrap(final String name, final DefaultToolExecutor delegate,
                              final AgentEventSink sink, final Long empId) {
        return new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                Map<String, Object> args = parseArgs(request.arguments());
                log.info("[agent] tool start name={} empId={} args={}", name, empId, args);
                sink.toolCall(name, args);
                BaseContext.setCurrentId(empId);
                String result;
                try {
                    result = delegate.execute(request, memoryId);
                    if (result == null || result.trim().isEmpty() || "null".equals(result)) {
                        result = "操作成功";
                    }
                } catch (BaseException e) {
                    result = "操作失败：" + e.getMessage();
                } catch (RuntimeException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    if (cause instanceof BaseException) {
                        result = "操作失败：" + cause.getMessage();
                    } else {
                        log.error("[agent] tool {} 执行异常", name, e);
                        result = "操作失败：系统异常，请稍后重试";
                    }
                } finally {
                    BaseContext.removeCurrentId();
                }
                result = truncate(result, 2000);
                sink.toolResult(name, result);
                log.info("[agent] tool end name={} empId={}", name, empId);
                return result;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            JSONObject obj = JSON.parseObject(raw);
            if (obj == null) {
                return new LinkedHashMap<String, Object>();
            }
            return new LinkedHashMap<String, Object>(obj);
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<String, Object>();
            fallback.put("raw", raw);
            return fallback;
        }
    }

    /**
     * 未配置模型密钥时，仍走现有 Service（同一套 @Tool 业务），方便管理端演示。
     */
    private void localChat(String text, Long empId, AgentEventSink sink) {
        BaseContext.setCurrentId(empId);
        try {
            String reply;
            if (containsAny(text, "你好", "您好", "你是谁", "你能做什么", "hi", "hello")) {
                reply = "你好，我是苍穹外卖老板助手。可以帮你查订单、接单/派送、看今日营业额和报表、查看或修改店铺营业状态。";
            } else if (containsAny(text, "打烊", "关店", "停止营业")) {
                reply = invokeLocal("setShopStatus", mapOf("status", 0), sink, empId, new CallableResult() {
                    public Object call() {
                        shopService.setStatus(0);
                        return "已更新。当前店铺已打烊（status=0）";
                    }
                });
            } else if (containsAny(text, "开始营业", "开门营业", "设为营业", "开业")) {
                reply = invokeLocal("setShopStatus", mapOf("status", 1), sink, empId, new CallableResult() {
                    public Object call() {
                        shopService.setStatus(1);
                        return "已更新。当前店铺营业中（status=1）";
                    }
                });
            } else if (containsAny(text, "营业吗", "营业中", "开店了", "店铺状态", "现在营业")) {
                reply = invokeLocal("getShopStatus", mapOf(), sink, empId, new CallableResult() {
                    public Object call() {
                        Integer status = shopService.getStatus();
                        if (status != null && status == 1) {
                            return "当前店铺营业中（status=1）";
                        }
                        return "当前店铺已打烊（status=0）";
                    }
                });
            } else if (containsAny(text, "营业额", "营收", "生意怎么样", "今日数据", "今天数据")) {
                reply = invokeLocal("getTodayBusinessData", mapOf(), sink, empId, new CallableResult() {
                    public Object call() {
                        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);
                        LocalDateTime end = LocalDateTime.now().with(LocalTime.MAX);
                        BusinessDataVO vo = workspaceService.getBusinessData(begin, end);
                        return formatBusiness(vo);
                    }
                });
            } else if (containsAny(text, "接单")) {
                String id = extractId(text);
                if (id == null) {
                    reply = invokeLocal("searchOrders", mapOf("status", 2), sink, empId, new CallableResult() {
                        public Object call() {
                            OrdersPageQueryDTO dto = new OrdersPageQueryDTO();
                            dto.setPage(1);
                            dto.setPageSize(10);
                            dto.setStatus(2);
                            return formatOrders(orderService.conditionSearch(dto));
                        }
                    });
                    reply = "接单需要明确的订单 id，我先查了待接单列表：\n" + reply + "\n请回复「接单 订单id」，我不会臆造单号。";
                } else {
                    final Long orderId = Long.valueOf(id);
                    reply = invokeLocal("confirmOrder", mapOf("id", orderId), sink, empId, new CallableResult() {
                        public Object call() {
                            OrdersConfirmDTO dto = new OrdersConfirmDTO();
                            dto.setId(orderId);
                            orderService.confirm(dto);
                            return "已接单，订单 id=" + orderId;
                        }
                    });
                }
            } else if (containsAny(text, "待接单", "查订单", "查一下订单", "查找订单")
                    || text.matches(".*1[3-9]\\d{9}.*")) {
                final String phone = extractPhone(text);
                final Integer status = text.contains("待接单") ? 2 : null;
                reply = invokeLocal("searchOrders", mapOf("phone", phone, "status", status), sink, empId, new CallableResult() {
                    public Object call() {
                        OrdersPageQueryDTO dto = new OrdersPageQueryDTO();
                        dto.setPage(1);
                        dto.setPageSize(10);
                        dto.setPhone(phone);
                        dto.setStatus(status);
                        return formatOrders(orderService.conditionSearch(dto));
                    }
                });
            } else if (containsAny(text, "订单总览", "待处理", "积压")) {
                reply = invokeLocal("getOrderOverview", mapOf(), sink, empId, new CallableResult() {
                    public Object call() {
                        return formatOverview(workspaceService.getOrderOverView());
                    }
                });
            } else {
                reply = "我可以帮你：今日营业额、店铺营业状态、查待接单、接单。试试「今天营业额怎么样」或「现在营业吗」。";
            }
            emitText(sink, reply);
            sink.end();
        } finally {
            BaseContext.removeCurrentId();
        }
    }

    private String invokeLocal(String name, Map<String, Object> args, AgentEventSink sink, Long empId,
                               CallableResult body) {
        sink.toolCall(name, args);
        log.info("[agent] tool start name={} empId={} args={}", name, empId, args);
        String result;
        try {
            Object value = body.call();
            result = value == null ? "操作成功" : (value instanceof String ? (String) value : JSON.toJSONString(value));
        } catch (BaseException e) {
            result = "操作失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[agent] tool {} 执行异常", name, e);
            result = "操作失败：系统异常，请稍后重试";
        }
        result = truncate(result, 2000);
        sink.toolResult(name, result);
        log.info("[agent] tool end name={} empId={}", name, empId);
        if (result.startsWith("操作失败")) {
            return result;
        }
        return result;
    }

    private interface CallableResult {
        Object call() throws Exception;
    }

    private String formatBusiness(BusinessDataVO vo) {
        if (vo == null) {
            return "暂无今日营业数据";
        }
        return "今日营业额：" + vo.getTurnover() + " 元\n"
                + "有效订单：" + vo.getValidOrderCount() + "\n"
                + "订单完成率：" + vo.getOrderCompletionRate() + "\n"
                + "平均客单价：" + vo.getUnitPrice() + "\n"
                + "新增用户：" + vo.getNewUsers();
    }

    private String formatOverview(OrderOverViewVO vo) {
        if (vo == null) {
            return "暂无订单总览";
        }
        return "待接单：" + vo.getWaitingOrders() + "\n"
                + "待派送：" + vo.getDeliveredOrders() + "\n"
                + "已完成：" + vo.getCompletedOrders() + "\n"
                + "已取消：" + vo.getCancelledOrders() + "\n"
                + "全部订单：" + vo.getAllOrders();
    }

    private String formatOrders(PageResult page) {
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return "未查询到订单";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(page.getTotal()).append(" 条\n");
        int n = Math.min(page.getRecords().size(), 10);
        for (int i = 0; i < n; i++) {
            Object rec = page.getRecords().get(i);
            if (!(rec instanceof Orders)) {
                continue;
            }
            Orders o = (Orders) rec;
            sb.append(i + 1).append(". id=").append(o.getId())
                    .append(" | 订单号 ").append(o.getNumber())
                    .append(" | 状态 ").append(statusText(o.getStatus()))
                    .append(" | 金额 ").append(o.getAmount())
                    .append(" | 手机 ").append(o.getPhone())
                    .append('\n');
        }
        return sb.toString().trim();
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 1:
                return "待付款";
            case 2:
                return "待接单";
            case 3:
                return "已接单";
            case 4:
                return "派送中";
            case 5:
                return "已完成";
            case 6:
                return "已取消";
            default:
                return "未知(" + status + ")";
        }
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

    private boolean containsAny(String text, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            if (text.contains(keys[i])) {
                return true;
            }
        }
        return false;
    }

    private String extractId(String text) {
        Matcher m = Pattern.compile("(?:接单|订单\\s*id|id)\\s*[:：#=]?\\s*(\\d{1,12})", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        Matcher only = Pattern.compile("^\\s*(\\d{1,12})\\s*$").matcher(text);
        if (only.find()) {
            return only.group(1);
        }
        return null;
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
