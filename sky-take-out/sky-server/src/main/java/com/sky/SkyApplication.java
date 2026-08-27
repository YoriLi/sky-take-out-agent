package com.sky;
//声明当前Java类属于com.sky包
// 启动类放在com.sky下，可以扫描到这些组件

import lombok.extern.slf4j.Slf4j;
//导入Lombok提供的@Slf4j注解
//作用：自动帮当前类生成一个日志对象 log

import org.springframework.boot.SpringApplication;
// 导入Spring Boot启动核心启动类
// SpringApplication提供启动Spring Boot应用的方法

import org.springframework.boot.autoconfigure.SpringBootApplication;
// 导入Spring Boot启动核心注解
// 标记这是Spring Boot启动配置类 开启自动配置 扫描当前包下的Controller、Service、Mapper等组件

import org.springframework.cache.annotation.EnableCaching;
//开启Spring缓存功能
// 开启后： @Cacheable @CachePut @CacheEvict 等缓存注解才会生效

import org.springframework.scheduling.annotation.EnableScheduling;
// 导入定时任务开启注解
// @EnableScheduling开启Spring定时任务功能

import org.springframework.transaction.annotation.EnableTransactionManagement;
// 导入事务管理开启注解
// @EnableTransactionManagement开启Spring事务管理功能

@SpringBootApplication
// 开启：
// 1. Bean扫描
// 2. 自动配置
// 3. Spring Boot功能支持
@EnableTransactionManagement //开启注解方式的事务管理
// 开启事务管理，让Spring创建事务代理对象，之后Service层中：
// @Transactional
// public void submitOrder(){
// }
// 才可以控制事务
@Slf4j
// 开启Lombok日志功能，下面可以直接使用：log.info()
@EnableCaching
// 让Spring支持缓存相关注解
@EnableScheduling
// 开启定时任务功能，让Spring扫描@Scheduled，例如：每天凌晨自动取消超时订单
public class SkyApplication {
    // 里面必须有main方法作为Java程序入口
    public static void main(String[] args) {
        SpringApplication.run(SkyApplication.class, args);
        // 第一个参数：SkyApplication.class"，从这个启动类开始加载配置"
        // 第二个参数：args，接收启动时传入的命令行参数
        // 例如：java -jar sky.jar --server.port=8081 会进入Spring环境
        log.info("server started");
        //打印日志："server started"
    }
}
