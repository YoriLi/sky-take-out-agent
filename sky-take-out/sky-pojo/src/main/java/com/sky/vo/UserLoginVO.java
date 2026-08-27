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
@Schema(description = "C端用户登录返回的数据格式")
public class UserLoginVO implements Serializable {

    @Schema(description = "主键值")
    private Long id;

    @Schema(description = "微信用户唯一标识")
    private String openid;

    @Schema(description = "jwt令牌")
    private String token;

}
