package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import io.lettuce.core.RedisClient;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;


    /**
     * 创建代金券订单
     * @param voucherId
     * @return 返回创建的订单id
     */
    @Override
    public Result createVoucherOrder(Long voucherId) {
        //1. 查询代金券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2. 判断秒杀是否开始 或者 是否结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }

        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束");
        }

        //3. 判断库存是否充足
        if (voucher.getStock() < 1){
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();

        //4. 一人一单  加悲观锁  解决并发问题
        //创建锁对象
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);

        RLock lock = redissonClient.getLock("lock" + "order:" + userId);


        //获取锁
        //boolean isLock = simpleRedisLock.tryLock(1200);

        boolean isLock = lock.tryLock();

        //判断是否获取锁成功
        if (!isLock){
            return Result.fail("请勿重复下单");
        }

        //获取当前对象的代理对象 从而开启事务
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder2(voucherId);
        } finally {
            //simpleRedisLock.unLock();
            lock.unlock();
        }

    }

    @Transactional
    public Result createVoucherOrder2(Long voucherId) {
        //一人一单 判断用户是否重复下单  根据用户id去查询订单是否存在
        Long userId = UserHolder.getUser().getId();

        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0){
            return Result.fail("一个用户只能购买一张哦");
        }

        //4. 减少库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) //乐观锁判断库存是否充足
                .update();

        //如果更新数据库失败返回库存不足
        if (!success){
            return Result.fail("库存不足");
        }

        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id 使用redis生成器生成全局唯一id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);

        //6. 保存订单
        save(voucherOrder);

        //7. 返回订单id
        return Result.ok(orderId);
    }
}