package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;

@Data
@Schema(description = "各状态订单数量统计返回的数据格式")
public class OrderStatisticsVO implements Serializable {

    @Schema(description = "待接单数量")
    private Integer toBeConfirmed;

    @Schema(description = "待派送数量")
    private Integer confirmed;

    @Schema(description = "派送中数量")
    private Integer deliveryInProgress;
}
