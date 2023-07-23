package com.hzx.distributedlock.lock;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;


import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 基于Lua脚本实现的Redis分布式锁（可重入锁）
 *
 * @auther hzx
 * @create 2023/7/21 15:49
 */
public class DistributedRedisLock implements Lock {

    public String lockName;

    private StringRedisTemplate redisTemplate;

    private String uuid;

    private long expire = 30;// 过期时间，默认30s

    public DistributedRedisLock() {
    }

    public DistributedRedisLock(StringRedisTemplate redisTemplate,String lockName,String uuid) {
        this.lockName = lockName;
        this.redisTemplate = redisTemplate;
        this.uuid = uuid;
    }

    @Override
    public void lock() {
        this.tryLock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        try {
            // 不设置加锁时间
            return this.tryLock(-1L,TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 获取锁（加锁）失败
        return false;
    }

    /**
     * 加锁方法
     * 	1.判断锁是否存在（exists），则直接获取锁 hset key field value
     * 	2.如果锁存在则判断是否自己的锁（hexists），如果是自己的锁则重入：hincrby key field increment
     * 	3.否则重试：递归 循环
     * @param time
     * @param unit
     * @return
     * @throws InterruptedException
     */
    @Override
    public boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException {
        if(time != -1){
            this.expire = unit.toSeconds(time);

        }

        String script = "if redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1 " +
                "then" +
                "   redis.call('hincrby', KEYS[1], ARGV[1], 1) " +
                "   redis.call('expire', KEYS[1], ARGV[2]) " +
                "   return 1 " +
                "else" +
                "   return 0 " +
                "end";
        // 完成加锁方法
        // 锁的名字，id，过期时间：这里用的是StringRedisTemplate，所以要用String类型
        while (Boolean.FALSE.equals(this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class),
                // 锁的名字，id，过期时间：这里用的是StringRedisTemplate，所以要用String类型
                Arrays.asList(lockName), getId(), String.valueOf(expire)))){
            // 获取不到锁一直重试，睡一会给别的线程机会
            Thread.sleep(30);
        }

        return true;
    }

    // 用于获取每个锁的id，每个服务每个线程要保证同一个id，确保分布式系统的情况不会出问题
    // 给线程拼接唯一标识
    public String getId() {// 每个服务一个uuid，然后拼接服务中每个线程的id
        return uuid + ":" + Thread.currentThread().getId();
    }

    /**
     * 解锁方法
     * 1.判断自己的锁是否存在（hexists），不存在则返回nil
     * 2.如果自己的锁存在，则减1（hincrby -1），判断减1后的值是否为0，为0则释放锁（del）并返回1
     * 3.不为0，返回0
     */
    @Override
    public void unlock() {
        String script = "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 " +
                "then " +
                "   return nil " +
                "elseif redis.call('hincrby', KEYS[1], ARGV[1], -1) == 0 " +
                "   then return redis.call('del', KEYS[1]) " +
                "else " +
                "   return 0 " +
                "end";
        Long flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Arrays.asList(lockName), getId());
        if(flag == null){// 恶意释放锁
            // 抛出一个监听状态异常
            throw new IllegalMonitorStateException("this lock doesn't belong to you!");

        }


    }

    @NotNull
    @Override
    public Condition newCondition() {
        return null;
    }
}
