package com.sky.agent.skill;

import com.sky.agent.sse.AgentEventSink;
import com.sky.service.WorkspaceService;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import dev.langchain4j.agent.tool.Tool;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 工作台相关工具（注意 Service 是 Workspace 小写 s）。
 */
public class WorkspaceSkill extends AbstractSkill {

    private final WorkspaceService workspaceService;

    public WorkspaceSkill(WorkspaceService workspaceService, AgentEventSink sink, Long empId) {
        super(sink, empId);
        this.workspaceService = workspaceService;
    }

    @Tool("查询今日营业数据：营业额、有效订单、完成率、客单价、新增用户。用户问今天生意/营业额怎么样时使用。")
    public String getTodayBusinessData() {
        return invoke("getTodayBusinessData", args(), () -> {
            LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);
            LocalDateTime end = LocalDateTime.now().with(LocalTime.MAX);
            return AgentFormat.businessData(workspaceService.getBusinessData(begin, end));
        });
    }

    @Tool("查询订单总览：待接单、待派送、已完成、已取消、全部订单数量。")
    public String getOrderOverview() {
        return invoke("getOrderOverview", args(), () -> AgentFormat.orderOverview(workspaceService.getOrderOverView()));
    }

    @Tool("查询菜品总览：已启售、已停售数量。")
    public String getDishOverview() {
        return invoke("getDishOverview", args(), () -> {
            DishOverViewVO vo = workspaceService.getDishOverView();
            return "已启售：" + vo.getSold() + "，已停售：" + vo.getDiscontinued();
        });
    }

    @Tool("查询套餐总览：已启售、已停售数量。")
    public String getSetmealOverview() {
        return invoke("getSetmealOverview", args(), () -> {
            SetmealOverViewVO vo = workspaceService.getSetmealOverView();
            return "已启售：" + vo.getSold() + "，已停售：" + vo.getDiscontinued();
        });
    }
}
