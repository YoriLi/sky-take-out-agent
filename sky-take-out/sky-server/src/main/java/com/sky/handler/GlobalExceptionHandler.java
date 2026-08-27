package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 处理SQL唯一约束异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        // 记录完整错误供排查，但不把 MySQL 原始报文（含具体重复值/约束名/表名）返回给前端，
        // 避免用户名枚举与内部结构泄露。
        log.error("数据库约束异常：{}", ex.getMessage());
        String message = ex.getMessage();
        if (message != null && message.contains("Duplicate entry")) {
            return Result.error("记录已存在，请勿重复添加");
        }
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /**
     * 兜底异常处理：防止 NPE、解析异常等未受检异常直接以 500 堆栈暴露给前端。
     * @param ex
     * @return
     */
    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception ex){
        log.error("系统未捕获异常", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

}
