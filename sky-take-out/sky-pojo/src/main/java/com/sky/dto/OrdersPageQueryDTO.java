package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 该模型以 @ParameterObject 的形式展开成一组查询参数，
 * SpringDoc 在展开时只会读取 @Schema 上声明的信息，不会再推断字段类型，
 * 因此非 String 字段必须显式写出 implementation，否则文档里会退化成 string。
 */
@Data
@Schema(description = "订单分页查询的数据模型")
public class OrdersPageQueryDTO implements Serializable {

    @Schema(description = "页码", implementation = Integer.class)
    private int page;

    @Schema(description = "每页记录数", implementation = Integer.class)
    private int pageSize;

    @Schema(description = "订单号")
    private String number;

    @Schema(description = "手机号")
    private  String phone;

    @Schema(description = "订单状态", implementation = Integer.class)
    private Integer status;

    @Schema(description = "查询开始时间", implementation = LocalDateTime.class)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    @Schema(description = "查询结束时间", implementation = LocalDateTime.class)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "下单用户id", implementation = Long.class)
    private Long userId;

}
