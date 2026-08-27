package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "商品销量统计的数据模型")
public class GoodsSalesDTO implements Serializable {

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "销量")
    private Integer number;

}
