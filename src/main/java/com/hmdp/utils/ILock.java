package com.hmdp.utils;

/**
 * 分布式锁的接口
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 超时时间，单位秒
     * @return true 成功 false 失败
     */
    boolean tryLock(long timeoutSec);


    /**
     * 释放锁
     */
    void unLock();
}
