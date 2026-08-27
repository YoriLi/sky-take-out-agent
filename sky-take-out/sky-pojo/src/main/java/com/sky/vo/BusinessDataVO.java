package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 数据概览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据概览返回的数据格式")
public class BusinessDataVO implements Serializable {

    @Schema(description = "营业额")
    private Double turnover;

    @Schema(description = "有效订单数")
    private Integer validOrderCount;

    @Schema(description = "订单完成率")
    private Double orderCompletionRate;

    @Schema(description = "平均客单价")
    private Double unitPrice;

    @Schema(description = "新增用户数")
    private Integer newUsers;

}
