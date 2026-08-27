package com.sky.dto;

import com.sky.entity.DishFlavor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "菜品信息传递的数据模型")
public class DishDTO implements Serializable {

    @Schema(description = "菜品id")
    private Long id;

    @Schema(description = "菜品名称")
    private String name;

    @Schema(description = "菜品分类id")
    private Long categoryId;

    @Schema(description = "菜品价格")
    private BigDecimal price;

    @Schema(description = "图片")
    private String image;

    @Schema(description = "描述信息")
    private String description;

    @Schema(description = "0 停售 1 起售")
    private Integer status;

    @Schema(description = "口味")
    private List<DishFlavor> flavors = new ArrayList<>();

}
