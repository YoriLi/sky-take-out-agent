package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "销量排名Top10返回的数据格式")
public class SalesTop10ReportVO implements Serializable {

    @Schema(description = "商品名称列表，以逗号分隔，例如：鱼香肉丝,宫保鸡丁,水煮鱼")
    private String nameList;

    @Schema(description = "销量列表，以逗号分隔，例如：260,215,200")
    private String numberList;

}
