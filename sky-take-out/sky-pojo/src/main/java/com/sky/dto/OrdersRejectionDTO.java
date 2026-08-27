package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "拒单传递的数据模型")
public class OrdersRejectionDTO implements Serializable {

    @Schema(description = "订单id")
    private Long id;

    @Schema(description = "订单拒绝原因")
    private String rejectionReason;

}
