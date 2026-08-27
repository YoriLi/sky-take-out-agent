package com.sky.agent.skill;

import com.sky.agent.sse.AgentEventSink;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.LocalDate;

/**
 * 报表相关工具。日期缺省为最近 7 天。exportBusinessData 不暴露给 LLM。
 */
public class ReportSkill extends AbstractSkill {

    private final ReportService reportService;

    public ReportSkill(ReportService reportService, AgentEventSink sink, Long empId) {
        super(sink, empId);
        this.reportService = reportService;
    }

    @Tool("按日期区间查询营业额报表。日期格式 yyyy-MM-dd，可空，缺省为最近7天。")
    public String getTurnoverReport(@P("开始日期 yyyy-MM-dd，可空") String begin, @P("结束日期 yyyy-MM-dd，可空") String end) {
        return invoke("getTurnoverReport", args("begin", begin, "end", end), () -> {
            LocalDate[] r = parseRange(begin, end);
            TurnoverReportVO vo = reportService.getTurnover(r[0], r[1]);
            return "日期：" + vo.getDateList() + "\n营业额：" + vo.getTurnoverList();
        });
    }

    @Tool("按日期区间查询用户统计（总量与新增）。日期格式 yyyy-MM-dd，可空，缺省为最近7天。")
    public String getUserReport(@P("开始日期 yyyy-MM-dd，可空") String begin, @P("结束日期 yyyy-MM-dd，可空") String end) {
        return invoke("getUserReport", args("begin", begin, "end", end), () -> {
            LocalDate[] r = parseRange(begin, end);
            UserReportVO vo = reportService.getUserStatistics(r[0], r[1]);
            return "日期：" + vo.getDateList() + "\n总用户：" + vo.getTotalUserList() + "\n新增用户：" + vo.getNewUserList();
        });
    }

    @Tool("按日期区间查询订单统计（总数、有效订单、完成率）。日期格式 yyyy-MM-dd，可空，缺省为最近7天。")
    public String getOrderReport(@P("开始日期 yyyy-MM-dd，可空") String begin, @P("结束日期 yyyy-MM-dd，可空") String end) {
        return invoke("getOrderReport", args("begin", begin, "end", end), () -> {
            LocalDate[] r = parseRange(begin, end);
            OrderReportVO vo = reportService.getOrderStatistics(r[0], r[1]);
            return "日期：" + vo.getDateList()
                    + "\n每日订单数：" + vo.getOrderCountList()
                    + "\n每日有效订单：" + vo.getValidOrderCountList()
                    + "\n区间总订单：" + vo.getTotalOrderCount()
                    + "，有效订单：" + vo.getValidOrderCount()
                    + "，完成率：" + vo.getOrderCompletionRate();
        });
    }

    @Tool("查询指定区间销量 Top10 菜品/套餐。日期格式 yyyy-MM-dd，可空，缺省为最近7天。")
    public String getSalesTop10(@P("开始日期 yyyy-MM-dd，可空") String begin, @P("结束日期 yyyy-MM-dd，可空") String end) {
        return invoke("getSalesTop10", args("begin", begin, "end", end), () -> {
            LocalDate[] r = parseRange(begin, end);
            SalesTop10ReportVO vo = reportService.getSalesTop10(r[0], r[1]);
            return "商品：" + vo.getNameList() + "\n销量：" + vo.getNumberList();
        });
    }
}
