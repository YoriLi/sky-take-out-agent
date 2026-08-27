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
@Schema(description = "用户统计返回的数据格式")
public class UserReportVO implements Serializable {

    @Schema(description = "日期，以逗号分隔，例如：2022-10-01,2022-10-02,2022-10-03")
    private String dateList;

    @Schema(description = "用户总量，以逗号分隔，例如：200,210,220")
    private String totalUserList;

    @Schema(description = "新增用户，以逗号分隔，例如：20,21,10")
    private String newUserList;

}
