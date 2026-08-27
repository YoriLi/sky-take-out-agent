package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "取消订单传递的数据模型")
public class OrdersCancelDTO implements Serializable {

    @Schema(description = "订单id")
    private Long id;

    @Schema(description = "订单取消原因")
    private String cancelReason;

}
