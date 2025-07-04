package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;


    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);

    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));

    }

    public <R,ID> R queryWithPassThrough(
            String KeyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String Key = KeyPrefix+id;
        //从Redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(Key);

        //判断是否存在
        if (StrUtil.isNotBlank(json)){
            //3.Redis命中，直接返回
            return JSONUtil.toBean(json, type);
        }
        //判断不存在中，是否为空值"" 空值不等于 null 如果==null 则 到数据库查找
        if (json != null){
            return null;
        }
        //未命中,根据id从数据库查找
        R r = dbFallback.apply(id);
        // (1)不存在返回404 将null写入redis
        if(r == null)
        {
            stringRedisTemplate.opsForValue().set(Key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // (2).存在则存入Redis缓存 并设置超时时间
        this.set(Key,r,time,unit);

        //返回
        return r;
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //获取锁
    private boolean tryLock(String key)
    {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    private void unLock(String key)
    {
        stringRedisTemplate.delete(key);
    }

    public <R,ID> R queryWithLogicalExpire(
            String KeyPrefix,ID id,Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit) {
        String Key = KeyPrefix+id;
        //从Redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(Key);
        //判断Redis缓存是否命中
        //未命中返回null
        if (StrUtil.isBlank(json)){ //只有存在字符串才命中 只有"" null等能过掉
            return null;
        }
        //Redis命中，先转json序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //转为Shop类 因为redisData不符合Shop类 多了一个过期时间不符合项目要求
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        //摘出过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断缓存是否过期
        //未过期 返回店铺信息
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        //过期需要进行缓存重建 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //判断是否获取成功
        if (isLock){
            //获取成功
            //开启独立线程 重建缓存(根据id查数据库 并写入Redis) 设置逻辑过期时间
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //赋值给Redis
                    this.setWithLogicalExpire(Key,r1,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally{
                    //释放锁
                    unLock(lockKey);
                }
            });
        }

        //未获取成功 返回店铺信息
        return r;
    }
}
