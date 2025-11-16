package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
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

    /**
     * 查询商铺类型列表
     * @return 商铺类型列表
     */
    @Override
    public List<ShopType> queryList() {
        String key = RedisConstants.SHOP_TYPE_KEY;

        //判断是否在缓存中
        String shopTypeList = stringRedisTemplate.opsForValue().get(key);
        if(shopTypeList != null) {
            return JSONUtil.toList(shopTypeList, ShopType.class);
        }

        //不存在则从数据库中查询
        List<ShopType> typeList = query().orderByAsc("sort").list();

        //将查询结果存入缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));

        return typeList;
    }
}
