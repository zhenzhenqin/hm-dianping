package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 创建代金券订单
     * @param voucherId
     * @return 代金券订单id
     */
    Result createVoucherOrder(Long voucherId);

    /**
     * 创建代金券订单（悲观锁）
     * @param voucherId
     * @return
     */
    Result createVoucherOrder2(Long voucherId);
}
