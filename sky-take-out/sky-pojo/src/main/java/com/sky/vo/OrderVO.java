package com.sky.vo;

import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订单信息返回的数据格式")
public class OrderVO extends Orders implements Serializable {

    @Schema(description = "订单菜品信息")
    private String orderDishes;

    @Schema(description = "订单详情")
    private List<OrderDetail> orderDetailList;

}
