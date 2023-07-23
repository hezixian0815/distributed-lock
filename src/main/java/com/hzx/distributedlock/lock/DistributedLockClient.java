package com.hzx.distributedlock.lock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 用于创建各种分布式锁的工厂
 * 1、redis分布式锁
 * 2、mysql分布式锁
 * 3、zookeeper分布式锁
 *
 * @auther hzx
 * @create 2023/7/21 16:00
 */
@Component
public class DistributedLockClient {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String uuid;

    public DistributedLockClient(){
        this.uuid = UUID.randomUUID().toString();

    }

    public DistributedRedisLock getRedisLock(String lockName){
        return new DistributedRedisLock(redisTemplate,lockName,uuid);
    }

}
