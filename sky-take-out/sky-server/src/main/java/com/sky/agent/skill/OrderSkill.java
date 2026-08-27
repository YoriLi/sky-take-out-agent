package com.sky.agent.skill;

import com.sky.agent.sse.AgentEventSink;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 订单相关工具。参数一律用 String，内部解析并做防呆；写操作的合法性由 OrderService 状态机兜底。
 */
public class OrderSkill extends AbstractSkill {

    private final OrderService orderService;

    public OrderSkill(OrderService orderService, AgentEventSink sink, Long empId) {
        super(sink, empId);
        this.orderService = orderService;
    }

    @Tool("按订单号/手机号/状态分页查询订单。用户想找某笔或某类订单时使用，不要编造订单号。status：1待付款 2待接单 3已接单 4派送中 5已完成 6已取消。")
    public String searchOrders(@P("订单号，可空") String number,
                               @P("手机号，可空") String phone,
                               @P("订单状态 1-6，可空") String status,
                               @P("页码，默认1") String page,
                               @P("每页条数，默认10，最大10") String pageSize) {
        return invoke("searchOrders", args("number", number, "phone", phone, "status", status,
                "page", page, "pageSize", pageSize), () -> {
            OrdersPageQueryDTO dto = new OrdersPageQueryDTO();
            dto.setPage(Math.max(parseInt(page, 1), 1));
            // 硬上限 10 条，避免模型传入超大 pageSize 把整表顾客 PII 拉出并回灌模型
            dto.setPageSize(Math.min(Math.max(parseInt(pageSize, 10), 1), 10));
            dto.setNumber(blankToNull(number));
            dto.setPhone(blankToNull(phone));
            dto.setStatus(parseStatus(status));
            return AgentFormat.orders(orderService.conditionSearch(dto));
        });
    }

    @Tool("按订单内部数字 id 查询订单详情，含菜品明细。")
    public String getOrderDetail(@P("订单内部数字 id，必填") String orderId) {
        return invoke("getOrderDetail", args("orderId", orderId), () -> {
            Long id = parseId(orderId);
            if (id == null) {
                return "请提供有效的订单数字 id";
            }
            return AgentFormat.orderDetail(orderService.details(id));
        });
    }

    @Tool("统计当前待接单、已接单、派送中的订单数量。用户问积压、待处理订单时使用。")
    public String countOrdersByStatus() {
        return invoke("countOrdersByStatus", args(), () -> {
            OrderStatisticsVO vo = orderService.statistics();
            return "待接单：" + vo.getToBeConfirmed()
                    + "，已接单：" + vo.getConfirmed()
                    + "，派送中：" + vo.getDeliveryInProgress();
        });
    }

    @Tool("商家接单。仅在用户明确给出订单数字 id 时调用，禁止臆造 id。")
    public String confirmOrder(@P("订单内部数字 id，必填") String orderId) {
        return invoke("confirmOrder", args("orderId", orderId), () -> {
            Long id = parseId(orderId);
            if (id == null) {
                return "请提供有效的订单数字 id";
            }
            OrdersConfirmDTO dto = new OrdersConfirmDTO();
            dto.setId(id);
            orderService.confirm(dto);
            return "已接单，订单 id=" + id;
        });
    }

    @Tool("商家拒单。必须同时提供订单数字 id 和拒绝原因，缺一不可。")
    public String rejectOrder(@P("订单内部数字 id，必填") String orderId, @P("拒单原因，必填") String reason) {
        return invoke("rejectOrder", args("orderId", orderId, "reason", reason), () -> {
            Long id = parseId(orderId);
            if (id == null) {
                return "请提供有效的订单数字 id";
            }
            if (blankToNull(reason) == null) {
                return "拒单必须填写原因";
            }
            OrdersRejectionDTO dto = new OrdersRejectionDTO();
            dto.setId(id);
            dto.setRejectionReason(reason.trim());
            orderService.rejection(dto);
            return "已拒单，订单 id=" + id;
        });
    }

    @Tool("商家取消订单。必须同时提供订单数字 id 和取消原因，缺一不可。")
    public String cancelOrder(@P("订单内部数字 id，必填") String orderId, @P("取消原因，必填") String reason) {
        return invoke("cancelOrder", args("orderId", orderId, "reason", reason), () -> {
            Long id = parseId(orderId);
            if (id == null) {
                return "请提供有效的订单数字 id";
            }
            if (blankToNull(reason) == null) {
                return "取消必须填写原因";
            }
            OrdersCancelDTO dto = new OrdersCancelDTO();
            dto.setId(id);
            dto.setCancelReason(reason.trim());
            orderService.cancel(dto);
            return "已取消，订单 id=" + id;
        });
    }

    @Tool("将已接单的订单改为派送中。需要订单内部数字 id。")
    public String deliverOrder(@P("订单内部数字 id，必填") String orderId) {
        return invoke("deliverOrder", args("orderId", orderId), () -> {
            Long id = parseId(orderId);
            if (id == null) {
                return "请提供有效的订单数字 id";
            }
            orderService.delivery(id);
            return "已开始派送，订单 id=" + id;
        });
    }

    @Tool("将派送中的订单标记为已完成。需要订单内部数字 id。")
    public String completeOrder(@P("订单内部数字 id，必填") String orderId) {
        return invoke("completeOrder", args("orderId", orderId), () -> {
            Long id = parseId(orderId);
            if (id == null) {
                return "请提供有效的订单数字 id";
            }
            orderService.complete(id);
            return "订单已完成，id=" + id;
        });
    }
}
