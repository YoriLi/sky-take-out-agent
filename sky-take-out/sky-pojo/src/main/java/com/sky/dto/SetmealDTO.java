package com.sky.dto;

import com.sky.entity.SetmealDish;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "套餐信息传递的数据模型")
public class SetmealDTO implements Serializable {

    @Schema(description = "套餐id")
    private Long id;

    @Schema(description = "分类id")
    private Long categoryId;

    @Schema(description = "套餐名称")
    private String name;

    @Schema(description = "套餐价格")
    private BigDecimal price;

    @Schema(description = "状态 0:停用 1:启用")
    private Integer status;

    @Schema(description = "描述信息")
    private String description;

    @Schema(description = "图片")
    private String image;

    @Schema(description = "套餐菜品关系")
    private List<SetmealDish> setmealDishes = new ArrayList<>();

}
