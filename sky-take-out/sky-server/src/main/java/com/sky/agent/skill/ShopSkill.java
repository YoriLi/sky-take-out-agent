package com.sky.agent.skill;

import com.sky.agent.sse.AgentEventSink;
import com.sky.service.ShopService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 店铺营业状态相关工具。
 */
public class ShopSkill extends AbstractSkill {

    private final ShopService shopService;

    public ShopSkill(ShopService shopService, AgentEventSink sink, Long empId) {
        super(sink, empId);
        this.shopService = shopService;
    }

    @Tool("查询店铺当前营业状态。用户问现在开店了吗、营业吗时使用。")
    public String getShopStatus() {
        return invoke("getShopStatus", args(), () -> {
            Integer status = shopService.getStatus();
            return (status != null && status == 1) ? "当前店铺营业中（status=1）" : "当前店铺已打烊（status=0）";
        });
    }

    @Tool("设置店铺营业状态。只接受 0（打烊）或 1（营业）。用户明确要求开业或打烊时使用。")
    public String setShopStatus(@P("营业状态，只能是 0 或 1") String status) {
        return invoke("setShopStatus", args("status", status), () -> {
            Integer v = parseIntOrNull(status);
            if (v == null || (v != 0 && v != 1)) {
                return "营业状态只能是 0（打烊）或 1（营业）";
            }
            shopService.setStatus(v);
            return v == 1 ? "已更新，当前店铺营业中（status=1）" : "已更新，当前店铺已打烊（status=0）";
        });
    }
}
