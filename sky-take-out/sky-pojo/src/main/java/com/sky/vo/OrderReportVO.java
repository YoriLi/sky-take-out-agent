package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订单统计返回的数据格式")
public class OrderReportVO implements Serializable {

    @Schema(description = "日期，以逗号分隔，例如：2022-10-01,2022-10-02,2022-10-03")
    private String dateList;

    @Schema(description = "每日订单数，以逗号分隔，例如：260,210,215")
    private String orderCountList;

    @Schema(description = "每日有效订单数，以逗号分隔，例如：20,21,10")
    private String validOrderCountList;

    @Schema(description = "订单总数")
    private Integer totalOrderCount;

    @Schema(description = "有效订单数")
    private Integer validOrderCount;

    @Schema(description = "订单完成率")
    private Double orderCompletionRate;

}
