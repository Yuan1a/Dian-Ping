package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.management.loading.PrivateClassLoader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1577836800L;
    private static final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix)
    {
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSeconds - BEGIN_TIMESTAMP;

        //生成序列号 redis自增长有上限 2^64
        //获取当前日期精确到天
        String nowTime = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增 使用当前天数自增避免溢出
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":"+nowTime);
        //拼接并返回
        return timestamp<< COUNT_BITS | count;
    }
    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2020, 1, 1,0,0,0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = "+ second);
    }
}
