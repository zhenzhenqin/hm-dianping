package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX="lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    //加载lua脚本
    static{
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate){
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获得锁
     * @param timeoutSec 超时时间，单位秒
     * @return true 成功 false 失败
     */
    @Override
    public boolean tryLock(long timeoutSec) {

        //获取线程id
        String threadId = ID_PREFIX + String.valueOf(Thread.currentThread().getId());

        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }


    /**
     * 释放锁
     */
    @Override
    public void unLock() {

        //获取当前线程id
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        //通过lua脚本释放锁
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + name), threadId);
    }



    /*@Override
    public void unLock() {
        //删除锁

        // 需要判断是都为当前线程所持有 只有当前线程所持有才能删除
        //获取当前线程id
        String theadId = KEY_PREFIX + Thread.currentThread().getId();

        //获取当前锁中的线程id
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        //判断是否为当前线程锁持有的锁 如果是 则允许被删除
        if (theadId.equals(id)){
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
