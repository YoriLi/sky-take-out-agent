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
@Schema(description = "营业额统计返回的数据格式")
public class TurnoverReportVO implements Serializable {

    @Schema(description = "日期，以逗号分隔，例如：2022-10-01,2022-10-02,2022-10-03")
    private String dateList;

    @Schema(description = "营业额，以逗号分隔，例如：406.0,1520.0,75.0")
    private String turnoverList;

}
