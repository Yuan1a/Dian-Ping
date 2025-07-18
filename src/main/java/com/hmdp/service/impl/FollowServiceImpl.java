package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //0.获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follow:"+userId;
        //1.判断isFollow 是T 还是 F
        if (isFollow){
            //2.关注, 新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }
        else {
            //3.取关, 删除 delete tb_follow where userId = ？ and followUserId = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            //redis中移除关注用户
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        //1.查询是否关注 select count * from tb_follow where userId = ？ and followUserId = ?
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result commonFollow(Long id) {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follow:"+userId;
        //2.目标用户Key 求交集
        String key2 = "follow:"+id;
        Set<String> interset = stringRedisTemplate.opsForSet().intersect(key,key2);
        if (interset ==null || interset.isEmpty())
        {
            return Result.ok(Collections.emptyList());
        }
        //3.解析出id集合
        List<Long> ids = interset.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users=userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
