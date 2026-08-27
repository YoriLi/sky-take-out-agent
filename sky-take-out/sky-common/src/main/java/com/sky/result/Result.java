package com.sky.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果
 * @param <T>
 */
@Data
@Schema(description = "后端统一返回结果")
public class Result<T> implements Serializable {

    @Schema(description = "编码：1成功，0和其它数字为失败")
    private Integer code;

    @Schema(description = "错误信息")
    private String msg;

    @Schema(description = "数据")
    private T data;

    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = 1;
        return result;
    }

    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<T>();
        result.data = object;
        result.code = 1;
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result result = new Result();
        result.msg = msg;
        result.code = 0;
        return result;
    }

}
