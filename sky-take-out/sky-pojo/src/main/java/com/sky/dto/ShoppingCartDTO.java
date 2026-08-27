package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;

@Data
@Schema(description = "购物车操作传递的数据模型")
public class ShoppingCartDTO implements Serializable {

    @Schema(description = "菜品id")
    private Long dishId;

    @Schema(description = "套餐id")
    private Long setmealId;

    @Schema(description = "菜品口味")
    private String dishFlavor;

}
