package com.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData<T> {

    private LocalDateTime expireTime;

    private T data;

    public RedisData(LocalDateTime expireTime, T data) {
        this.expireTime = expireTime;
        this.data = data;
    }
}
