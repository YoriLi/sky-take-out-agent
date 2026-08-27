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
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody
// 1. @ControllerAdvice：表示这是一个“控制器增强器”，可以拦截所有 @Controller 的异常。
// 2. @ResponseBody：表示返回结果会自动序列化为 JSON（相当于在类上加了 @ResponseBody）。
// 因此，此异常处理器捕获到异常后，直接返回 JSON 格式的 Result 对象，而不会跳转到错误页面。
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    // @ExceptionHandler 注解：标记该方法用于处理特定类型的异常。
    // 默认情况下，如果参数中指定了异常类型（如 BaseException.class），则精准捕获该异常及其子类。
    // 这里写的是 @ExceptionHandler（没有显式指定类型），Spring 会根据方法参数的类型自动匹配，
    // 即自动捕获 BaseException 类型的异常。
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 处理SQL异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        //Duplicate entry 'zhangsan' for key 'employee.idx_username'
        String message = ex.getMessage();
        // 获取异常的原始错误信息（由 MySQL 驱动返回）
        if(message.contains("Duplicate entry")){
            // 判断错误信息中是否包含 "Duplicate entry"（重复条目）
            // 这是 MySQL 唯一键冲突的标准错误文本
            String[] split = message.split(" ");
            // 按空格拆分字符串，提取被重复的值
            // 例如："Duplicate entry 'zhangsan' for key 'employee.idx_username'"
            //   split[0] = "Duplicate"
            //   split[1] = "entry"
            //   split[2] = "'zhangsan'"
            String username = split[2];
            String msg = username + MessageConstant.ALREADY_EXISTS;
            //拼接提示
            return Result.error(msg);
        }else{
            return Result.error(MessageConstant.UNKNOWN_ERROR);
            // 如果不是唯一键冲突（比如字段超长、类型错误等），返回通用错误提示
        }
    }


}
