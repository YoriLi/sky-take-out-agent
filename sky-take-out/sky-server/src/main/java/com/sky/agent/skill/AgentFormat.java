package com.sky.agent.skill;

import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.OrderVO;

import java.util.List;

/**
 * Agent 工具结果的统一「VO → 可读摘要」格式化，供 Skill 层与本地兜底路径复用，
 * 避免订单状态文案、列表拼接等展示逻辑在多处重复实现。
 */
public final class AgentFormat {

    private AgentFormat() {
    }

    public static String orderStatusText(Integer status) {
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

    public static String orders(PageResult page) {
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return "未查询到订单";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(page.getTotal()).append(" 条");
        int n = Math.min(page.getRecords().size(), 10);
        if (page.getTotal() > n) {
            sb.append("，仅显示前 ").append(n).append(" 条");
        }
        sb.append('\n');
        for (int i = 0; i < n; i++) {
            Object rec = page.getRecords().get(i);
            if (!(rec instanceof Orders)) {
                continue;
            }
            Orders o = (Orders) rec;
            sb.append(i + 1).append(". id=").append(o.getId())
                    .append(" | 订单号 ").append(o.getNumber())
                    .append(" | 状态 ").append(orderStatusText(o.getStatus()))
                    .append(" | 金额 ").append(o.getAmount())
                    .append(" | 手机 ").append(o.getPhone())
                    .append('\n');
        }
        return sb.toString().trim();
    }

    public static String orderDetail(OrderVO vo) {
        if (vo == null) {
            return "订单不存在";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("订单 id=").append(vo.getId())
                .append(" | 订单号 ").append(vo.getNumber())
                .append(" | 状态 ").append(orderStatusText(vo.getStatus()))
                .append(" | 金额 ").append(vo.getAmount())
                .append(" | 手机 ").append(vo.getPhone());
        List<OrderDetail> details = vo.getOrderDetailList();
        if (details != null && !details.isEmpty()) {
            sb.append("\n菜品：");
            int n = Math.min(details.size(), 20);
            for (int i = 0; i < n; i++) {
                OrderDetail d = details.get(i);
                sb.append(d.getName()).append('*').append(d.getNumber()).append("；");
            }
        }
        return sb.toString();
    }

    public static String businessData(BusinessDataVO vo) {
        if (vo == null) {
            return "暂无今日营业数据";
        }
        return "今日营业额：" + vo.getTurnover() + " 元\n"
                + "有效订单：" + vo.getValidOrderCount() + "\n"
                + "订单完成率：" + vo.getOrderCompletionRate() + "\n"
                + "平均客单价：" + vo.getUnitPrice() + "\n"
                + "新增用户：" + vo.getNewUsers();
    }

    public static String orderOverview(OrderOverViewVO vo) {
        if (vo == null) {
            return "暂无订单总览";
        }
        return "待接单：" + vo.getWaitingOrders() + "\n"
                + "待派送：" + vo.getDeliveredOrders() + "\n"
                + "已完成：" + vo.getCompletedOrders() + "\n"
                + "已取消：" + vo.getCancelledOrders() + "\n"
                + "全部订单：" + vo.getAllOrders();
    }
}
