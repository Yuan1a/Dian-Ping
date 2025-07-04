package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeLists() {
        //获取redis中的商户
        String shopTypeJson = stringRedisTemplate.opsForValue().get("shop_type");
        //命中,直接返回
        if(StrUtil.isNotBlank(shopTypeJson))
        {
            //转为ShopType类
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        //未命中，从数据库中查找
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //不存在，返回错误
        if(shopTypes == null)
        {
            return Result.fail("店铺类别不存在！");
        }
        //查询到的信息传入Redis
        stringRedisTemplate.opsForValue().set("shop_type", JSONUtil.toJsonStr(shopTypes));
        //返回
        return Result.ok(shopTypes);


    }
}
