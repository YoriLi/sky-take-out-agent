package com.sky.config;

import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;
import com.sky.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webmvc.ui.SwaggerWebMvcConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.util.List;
import java.util.Optional;

/**
 * 配置类，注册web层相关组件
 */
// @Configuration : 标记这是一个“配置类”，相当于 Spring 的 XML 配置文件。
// Spring 启动时会扫描该类，并执行其中的 @Bean 方法来初始化容器。
@Configuration
@Slf4j
// 继承 WebMvcConfigurationSupport ：表示“我要自定义 Spring MVC 的配置”。
// 一旦继承此类，Spring Boot 的自动配置部分会失效，全面交由本类管控。
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    /**
     * SpringDoc 通过实现 WebMvcConfigurer 来注册 swagger-ui 的静态资源，
     * 但本类是直接继承 WebMvcConfigurationSupport 的，容器里的 WebMvcConfigurer 不会被自动应用，
     * 所以这里显式取出并在 addResourceHandlers 中手动委派，否则 /swagger-ui/index.html 会 404。
     * 用 Optional 是为了在 springdoc.swagger-ui.enabled=false 时仍能正常启动。
     */
    @Autowired
    private Optional<SwaggerWebMvcConfigurer> swaggerWebMvcConfigurer;

    /**
     * 注册自定义拦截器
     *
     * @param registry
     */
    protected void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");

        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                // .addPathPatterns : 定义巡逻范围（/** 表示该路径下所有子路径都要拦截）
                // 例如：/admin/employee/page、/admin/dish/save 都会触发拦截器校验 Token
                .excludePathPatterns("/admin/employee/login");
                // .excludePathPatterns : 定义免检区域（不需要登录就能访问的接口）
                // 例如：登录接口如果也拦截，用户永远无法登录，所以必须放行！

        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns("/user/user/login")
                // 用户端的登录接口放行
                .excludePathPatterns("/user/shop/status");
                // 用户端查看店铺营业状态也放行（不需要登录就能看）
    }

    /**
     * 设置静态资源映射
     * @param registry
     */
    // 设置静态资源映射（让 Spring MVC 能找到 swagger-ui 的页面和 js/css）
    // 如果 Spring MVC 找不到资源，访问文档页面就会报 404。
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {

        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
        // 当访问 /webjars/** （前端静态文件）时，去对应目录找

        // 把 /swagger-ui/** 的映射交给 SpringDoc 自己注册，
        // 这样 swagger-ui 的版本号、资源路径都由 SpringDoc 维护，升级依赖时无需改动本类。
        swaggerWebMvcConfigurer.ifPresent(configurer -> configurer.addResourceHandlers(registry));
    }

    /**
     * 扩展Spring MVC框架的消息转化器
     * @param converters
     */
    /**
     * 扩展 Spring MVC 框架的消息转换器（HttpMessageConverter）
     * 作用：自定义 Java 对象 转 JSON 的规则。
     * 如果不配置，LocalDateTime 会转成数组 [2024,12,11,10,30,15]。
     * 配置后，会自动转为 "yyyy-MM-dd HH:mm:ss" 格式。
     */
    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("扩展消息转换器...");
        //创建一个消息转换器对象
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        //需要为消息转换器设置一个对象转换器，对象转换器可以将Java对象序列化为json数据
        converter.setObjectMapper(new JacksonObjectMapper());

        //将自己的转换器插到默认的 Jackson 转换器之前，这样处理 JSON 时会优先用我们的转换器。
        //注意不能直接放到索引 0：那样会排在 ByteArray/String 转换器前面，
        //SpringDoc 的 /v3/api-docs 返回的是 byte[]，会被 Jackson 当普通对象序列化成 Base64 字符串，
        //导致接口文档无法被 Swagger UI 解析。
        int index = converters.size();
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof MappingJackson2HttpMessageConverter) {
                index = i;
                break;
            }
        }
        converters.add(index, converter);
    }
}
