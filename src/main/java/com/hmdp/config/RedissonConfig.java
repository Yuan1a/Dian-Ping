package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();// 创建一个新的 Config 对象，用于配置 Redisson。
        // 根据配置创建并返回 RedissonClient 实例。
        //        config.useSingleServer()：指定使用单节点模式连接 Redis。
        //        Redisson 支持多种模式，包括单节点、集群、哨兵等模式。这里选择单节点模式。
        config.useSingleServer()// 说明当前用的是单节点的redis
                .setAddress("redis://localhost:6377").setPassword("221221");
        // 根据配置创建并返回 RedissonClient 实例。
        return Redisson.create(config);
    }
}

