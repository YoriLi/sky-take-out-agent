package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 该模型以 @ParameterObject 的形式展开成一组查询参数，
 * SpringDoc 在展开时只会读取 @Schema 上声明的信息，不会再推断字段类型，
 * 因此非 String 字段必须显式写出 implementation，否则文档里会退化成 string。
 */
@Data
@Schema(description = "分类分页查询的数据模型")
public class CategoryPageQueryDTO implements Serializable {

    @Schema(description = "页码", implementation = Integer.class)
    private int page;

    @Schema(description = "每页记录数", implementation = Integer.class)
    private int pageSize;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "分类类型 1菜品分类 2套餐分类", implementation = Integer.class)
    private Integer type;

}
