package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //Shop shop= queryWithPassThrough(id);
        //互斥锁解决缓存击穿
        //Shop shop =queryWithMutex(id);
        //逻辑过期解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //Shop shop = queryWithLogicalExpire(id);
        if(shop == null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }



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

    //互斥锁解决缓存击穿
    public Shop queryWithMutex(Long id){
        String Key = CACHE_SHOP_KEY+id;
        //从Redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(Key);

        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)){ //只有存在字符串才命中 只有"" null等能过掉
            //3.Redis命中，直接返回
            //转为Shop类

            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断不存在中，是否为空值"" 空值不等于 null 如果==null 则 到数据库查找
        if (shopJson != null){
            return null;
        }
        //未命中,获取互斥锁 然后根据id从数据库查找
        //获取互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            boolean isLock =tryLock(lockKey);
            //是否获得
            //未获得，休眠一段时间重新从redis查询商铺缓存
            if (!isLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //获得
            //再次检测缓存是否命中 做双重检查再进行重建操作
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            // (1)不存在返回404 将null写入redis
            if(shop == null)
            {
                stringRedisTemplate.opsForValue().set(Key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // (2).存在则存入Redis缓存 并设置超时时间
            stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unLock(lockKey);
        }
        //返回
        return shop;
    }
    public Shop queryWithPassThrough(Long id){
        String Key = CACHE_SHOP_KEY+id;
        //从Redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(Key);

        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)){ //只有存在字符串才命中 只有"" null等能过掉
            //3.Redis命中，直接返回
            //转为Shop类

            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断不存在中，是否为空值"" 空值不等于 null 如果==null 则 到数据库查找
        if (shopJson != null){
            return null;
        }
        //未命中,根据id从数据库查找

        Shop shop = getById(id);
        // (1)不存在返回404 将null写入redis
        if(shop == null)
        {
            stringRedisTemplate.opsForValue().set(Key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // (2).存在则存入Redis缓存 并设置超时时间
        stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //返回
        return shop;
    }
    public void saveShop2Redis(Long id, Long expireSeconds){
        //查询店铺数据
        Shop shop = getById(id);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //逻辑过期解决缓存击穿
    private Shop queryWithLogicalExpire(Long id) {
        String Key = CACHE_SHOP_KEY+id;
        //从Redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(Key);
        //判断Redis缓存是否命中
        //未命中返回null
        if (StrUtil.isBlank(shopJson)){ //只有存在字符串才命中 只有"" null等能过掉
            return null;
        }
        //Redis命中，先转json序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //转为Shop类 因为redisData不符合Shop类 多了一个过期时间不符合项目要求
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        //摘出过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断缓存是否过期
        //未过期 返回店铺信息
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        //过期需要进行缓存重建 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //判断是否获取成功
        if (isLock){
                //获取成功
                //开启独立线程 根据id查数据库 并写入Redis 设置逻辑过期时间
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    try {
                        //重建缓存
                        this.saveShop2Redis(id,20L);
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }finally{
                        //释放锁
                        unLock(lockKey);
                    }
                });
        }

        //未获取成功 返回店铺信息
        return shop;
    }

    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空！");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要根据坐标查询
        if(x==null || y==null){
            //不需要查询坐标，按数据库查
           Page<Shop> page = query()
                    .eq("type_id",typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //2.计算分页参数
        int from = (current - 1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current*SystemConstants.DEFAULT_PAGE_SIZE;
        //3.查询redis，按照距离排序、分页。结果：shopId,distance
        String key = SHOP_GEO_KEY+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(key, new Circle(x, y, 5000), RedisGeoCommands.GeoRadiusCommandArgs
                        .newGeoRadiusArgs()
                        .includeDistance()
                        .limit(end)
                );
        //4.解析出id
        if(results==null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        //4.1.截取from-end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        if(list.size()<=from){
            return Result.ok(Collections.emptyList());
        }
        list.stream().skip(from).forEach(result->{ //跳过可能把所有数据跳过了
            //4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
        //5.根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD ( id," + idStr + ")").list();
        for(Shop shop : shops){
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //6、返回
        return Result.ok(shops);

    }


}
