package com.sky.agent.skill;

import com.sky.agent.sse.AgentEventSink;
import com.sky.context.BaseContext;
import com.sky.exception.BaseException;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Skill 基类：只放辅助逻辑，@Tool 方法必须声明在具体子类上（langchain4j 只扫描声明类的方法）。
 * Skill 由 AgentChatService 每请求 new 出来（非 Spring 代理），因此其调用的 Service 仍是被 AOP
 * 增强的代理对象——@Transactional / 缓存 / 未来的权限注解不会被绕过。
 */
@Slf4j
public abstract class AbstractSkill {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    protected final AgentEventSink sink;
    protected final Long empId;

    protected AbstractSkill(AgentEventSink sink, Long empId) {
        this.sink = sink;
        this.empId = empId;
    }

    /**
     * 统一工具入口：发 tool_call → 绑定 empId → 执行 → 异常转中文 → 截断 → 发 tool_result，并记审计日志（含结果摘要）。
     */
    protected String invoke(String toolName, Map<String, Object> args, Callable<String> body) {
        log.info("[agent] tool start name={} empId={} args={}", toolName, empId, args);
        sink.toolCall(toolName, args);
        BaseContext.setCurrentId(empId);
        String result;
        try {
            String value = body.call();
            result = (value == null || value.trim().isEmpty()) ? "操作成功" : value;
        } catch (BaseException e) {
            result = "操作失败：" + e.getMessage();
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof BaseException) {
                result = "操作失败：" + cause.getMessage();
            } else {
                log.error("[agent] tool {} 执行异常 empId={}", toolName, empId, e);
                result = "操作失败：系统异常，请稍后重试";
            }
        } finally {
            BaseContext.removeCurrentId();
        }
        result = truncate(result, 2000);
        sink.toolResult(toolName, result);
        log.info("[agent] tool end name={} empId={} result={}", toolName, empId, truncate(result, 200));
        return result;
    }

    protected Map<String, Object> args(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                map.put(String.valueOf(kv[i]), kv[i + 1]);
            }
        }
        return map;
    }

    protected Long parseId(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    protected Integer parseIntOrNull(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    protected int parseInt(String raw, int def) {
        Integer v = parseIntOrNull(raw);
        return v == null ? def : v;
    }

    /**
     * 解析订单状态：仅接受 1-6，非法/为空返回 null（表示不作为过滤条件）。
     */
    protected Integer parseStatus(String raw) {
        Integer v = parseIntOrNull(raw);
        if (v == null || v < 1 || v > 6) {
            return null;
        }
        return v;
    }

    protected String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    protected LocalDate parseDate(String raw, LocalDate def) {
        String t = blankToNull(raw);
        if (t == null) {
            return def;
        }
        try {
            return LocalDate.parse(t, DATE_FMT);
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * 解析日期区间；任一端缺省或非法时回退到「最近 7 天（含今天）」，保证不抛异常。
     */
    protected LocalDate[] parseRange(String begin, String end) {
        LocalDate today = LocalDate.now();
        LocalDate e = parseDate(end, today);
        LocalDate b = parseDate(begin, e.minusDays(6));
        if (b.isAfter(e)) {
            LocalDate tmp = b;
            b = e;
            e = tmp;
        }
        return new LocalDate[]{b, e};
    }

    protected String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…（已截断）";
    }
}
