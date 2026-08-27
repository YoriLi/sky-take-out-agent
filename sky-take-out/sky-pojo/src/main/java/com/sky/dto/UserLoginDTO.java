package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * C端用户登录
 */
@Data
@Schema(description = "C端用户登录时传递的数据模型")
public class UserLoginDTO implements Serializable {

    @Schema(description = "微信登录授权码")
    private String code;

}
