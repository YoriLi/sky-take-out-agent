package com.sky.service;

public interface ShopService {

    /**
     * 获取营业状态（Redis）；未命中则初始化为打烊
     */
    Integer getStatus();

    /**
     * 设置营业状态（写 Redis）
     */
    void setStatus(Integer status);
}
