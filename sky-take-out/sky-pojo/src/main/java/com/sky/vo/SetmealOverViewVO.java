package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 套餐总览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "套餐总览返回的数据格式")
public class SetmealOverViewVO implements Serializable {

    @Schema(description = "已启售数量")
    private Integer sold;

    @Schema(description = "已停售数量")
    private Integer discontinued;
}
