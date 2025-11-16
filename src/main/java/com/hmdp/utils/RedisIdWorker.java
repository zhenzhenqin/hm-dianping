package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 基于redis生成全局唯一id
 */
@Component
public class RedisIdWorker {

    //开始时间戳
    private static final long BEGIN_TIMESTAMP = 1742976000L;

    //序列号位数
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public Long nextId(String keyPrefix){

        //1. 生成时间戳
        LocalDateTime now = LocalDateTime.now(); //当前时间
        long nowTimestamp = now.toEpochSecond(ZoneOffset.UTC); //当前时间戳
        long timestamp = nowTimestamp - BEGIN_TIMESTAMP; //获取时间戳

        //2. 生成序列号
         //获取当前时间
        String data = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + data);

        //拼接生成全局唯一id
        return timestamp << COUNT_BITS | count;
    }
}
