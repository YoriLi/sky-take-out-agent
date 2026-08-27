package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;

@Data
@Schema(description = "订单支付传递的数据模型")
public class OrdersPaymentDTO implements Serializable {

    @Schema(description = "订单号")
    private String orderNumber;

    @Schema(description = "付款方式")
    private Integer payMethod;

}
