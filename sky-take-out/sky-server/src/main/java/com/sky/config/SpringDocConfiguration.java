package com.sky.config;

import com.sky.properties.JwtProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置类，替代原先基于 Springfox 的 Docket 配置。
 *
 * 文档地址：
 * Swagger UI  -> /swagger-ui/index.html
 * OpenAPI JSON -> /v3/api-docs（全量）、/v3/api-docs/{group}（分组）
 */
@Configuration
@Slf4j
public class SpringDocConfiguration {

    /**
     * 管理端令牌的安全方案名，仅用于文档内引用，与业务无关
     */
    private static final String ADMIN_TOKEN_SCHEME = "adminToken";

    /**
     * 用户端令牌的安全方案名，仅用于文档内引用，与业务无关
     */
    private static final String USER_TOKEN_SCHEME = "userToken";

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 文档的标题、描述、版本等基础信息
     */
    @Bean
    public OpenAPI skyOpenAPI() {
        log.info("准备生成接口文档...");
        return new OpenAPI()
                .info(new Info()
                        .title("苍穹外卖项目接口文档")
                        .description("苍穹外卖项目接口文档")
                        .version("2.0"))
                .components(new Components()
                        .addSecuritySchemes(ADMIN_TOKEN_SCHEME, tokenScheme(jwtProperties.getAdminTokenName()))
                        .addSecuritySchemes(USER_TOKEN_SCHEME, tokenScheme(jwtProperties.getUserTokenName())));
    }

    /**
     * 管理端接口分组，对应原 Docket 的 groupName("管理端接口")
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("管理端接口")
                .packagesToScan("com.sky.controller.admin")
                .addOpenApiCustomiser(openApi -> openApi.addSecurityItem(
                        new SecurityRequirement().addList(ADMIN_TOKEN_SCHEME)))
                .build();
    }

    /**
     * 用户端接口分组，对应原 Docket 的 groupName("用户端接口")
     */
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("用户端接口")
                .packagesToScan("com.sky.controller.user")
                .addOpenApiCustomiser(openApi -> openApi.addSecurityItem(
                        new SecurityRequirement().addList(USER_TOKEN_SCHEME)))
                .build();
    }

    /**
     * 令牌通过请求头传递，头名称取自 sky.jwt 配置，避免与实际拦截器校验的头名称不一致
     */
    private SecurityScheme tokenScheme(String headerName) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName);
    }
}
