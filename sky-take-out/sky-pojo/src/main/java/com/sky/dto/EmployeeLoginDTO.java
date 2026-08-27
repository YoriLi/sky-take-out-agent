package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;// 导入 OpenAPI 3 中用于描述"模型"和"字段"的注解
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "员工登录时传递的数据模型")
// @Schema 是 OpenAPI 3（SpringDoc）注解，作用在"类"上时描述整个模型。
// 它的作用是告诉 SpringDoc 生成的 API 文档："这个类是前端传入的 JSON 数据模型"。
// description 属性就是给这个模型起个中文名字，显示在文档页面上，方便前端开发人员看懂。
// 注意：这个注解只影响文档显示，代码运行时完全不起作用，删掉它登录功能照样正常运行。
public class EmployeeLoginDTO implements Serializable {

    @Schema(description = "用户名")
    // @Schema 作用在"字段"上时描述单个字段。
    // 前端开发人员看到文档就知道这个字段填的是登录用户名。
    private String username;

    @Schema(description = "密码")
    private String password;

}
