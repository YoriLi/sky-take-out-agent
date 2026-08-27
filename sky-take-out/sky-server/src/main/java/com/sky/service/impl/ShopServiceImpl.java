package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.service.ShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 店铺营业状态仅存 Redis；缓存未命中时默认打烊(0)并回写。
 */
@Service
@Slf4j
public class ShopServiceImpl implements ShopService {

    public static final String KEY = "SHOP_STATUS";

    /** 默认打烊 */
    public static final int DEFAULT_STATUS = 0;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Integer getStatus() {
        Integer status = readStatusFromRedis();
        if (status != null) {
            return status;
        }

        // 未取到再读一遍
        status = readStatusFromRedis();
        if (status != null) {
            return status;
        }

        // 仍没有：初始化为打烊并写入 Redis
        status = DEFAULT_STATUS;
        redisTemplate.opsForValue().set(KEY, status);
        log.info("店铺状态缓存未命中，已初始化为打烊并写入 Redis");
        return status;
    }

    @Override
    public void setStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BaseException(MessageConstant.UNKNOWN_ERROR);
        }
        redisTemplate.opsForValue().set(KEY, status);
        log.info("店铺状态已写入 Redis：{}", status);
    }

    /**
     * 兼容 JDK 序列化下 Integer/Long 等 Number，避免强转 ClassCastException
     */
    private Integer readStatusFromRedis() {
        Object value = redisTemplate.opsForValue().get(KEY);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        log.warn("店铺状态缓存类型异常: {}, 将按未命中处理", value.getClass().getName());
        return null;
    }
}
