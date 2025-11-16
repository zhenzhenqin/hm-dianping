package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryById(Long id) {

        //解决缓存击穿问题
        Shop shop = queryWithMutex(id);

        //5. 返回详细信息
        return Result.ok(shop);
    }

    //通过互斥锁解决缓存击穿问题
    public Shop queryWithMutex(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        //1. 先从缓存中查询商铺
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2. 如果缓存中存在有效数据，则返回
        if(shopJson != null && !shopJson.isEmpty()){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //3. 如果缓存中存储的是空值标记，则返回错误
        if(shopJson != null && shopJson.isEmpty()){
            return null;
        }

        //4. 不存在 查询数据库
        Shop shop = null;

        //进行缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            //4.1 尝试获取互斥锁

            boolean isLock = tryLock(lockKey);

            //4.2 如果获取互斥锁失败 则睡眠后重新尝试
            if(!isLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.3 如果获取互斥锁成功 则重建缓存
            shop = getById(id);
            if(shop == null){
                //不存在则写入空值到缓存
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;
            }

            //5. 数据库中存在 则将数据写如缓存中
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            //7. 释放互斥锁
            unlock(lockKey);
        }

        //6. 返回商铺
        return shop;
    }

    //通过逻辑过期时间解决缓存击穿问题
    public Shop queryWithLogicExpire(Long id){
        // 1. 先从缓存中查询数据
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3.存在，直接返回
            return null;
        }

        //将商铺数据反序列化为对象信息
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime(); //获取逻辑过期时间
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);  //获取商铺信息

        //2.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            // 4. 如果未过期 则直接返回旧的店铺信息
            return shop;
        }

        //3. 如果过期 则判断是否能够获得锁、
        boolean isLock = tryLock(key);
        if (isLock){
            //4. 获得锁 则交给一个新的线程去重建缓存 同时返回旧的缓存信息
            CACHE_REBUILD_EXECUTOR.submit( () -> {
                //缓存重建
                try {
                    saveShop2Redis(id, RedisConstants.CACHE_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(key);
                }
            });
        }

        //5. 如果没有获得锁 则返回一个旧的缓存信息
        return shop;
    }



    //添加逻辑过期商铺信息到缓存中 需要传入id以及逻辑过期的时间
    private void saveShop2Redis(Long id, Long expireTime){
        //根据id查询数据库
        Shop shop = getById(id);

        //封装到RedisData中
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime)); //逻辑过期时间

        //将数据写入缓存中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    //创建互斥锁
    public boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放互斥锁
    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return ok
     */
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        //判断商铺是否存在
        if(id == null){
            return Result.fail("商铺不存在");
        }
        //先修改数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
