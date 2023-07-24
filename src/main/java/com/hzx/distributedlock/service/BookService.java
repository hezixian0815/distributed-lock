package com.hzx.distributedlock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hzx.distributedlock.lock.DistributedLockClient;
import com.hzx.distributedlock.lock.DistributedRedisLock;
import com.hzx.distributedlock.mapper.BookMapper;
import com.hzx.distributedlock.pojo.Book;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @auther hzx
 * @create 2023/7/18 20:47
 */
@Service
public class BookService {

    @Autowired
    private BookMapper bookMapper;

    //可重入锁
    private ReentrantLock lock = new ReentrantLock();

    @Autowired
//    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    // 获取锁的工厂
    @Autowired
    private DistributedLockClient distributedLockClient;

    @Autowired
    private RedissonClient redissonClient;


    /**
     * Redisson自带的分布式锁，比我们自己封装的分布式锁性能要好，吞吐量达到1100
     */
    public void deBook() {

        RLock lock = this.redissonClient.getLock("lock");
        lock.lock();


        try {
            // 1.查询库存信息
            String stock = redisTemplate.opsForValue().get("stock");

            // 2.更新库存
            if (stock != null && stock.length() != 0) {
                int kc = Integer.parseInt(stock);
                if (kc > 0) {
                    // 3.扣减库存
                    redisTemplate.opsForValue().set("stock", String.valueOf(--kc));
                }
            }
        } finally {
            lock.unlock();
        }


    }


    /**
     * Redis实现可重入锁：hash数据模型 + lua脚本；吞吐量600
     */
    public void deBook5() {
        // 获取redis可重入锁
        DistributedRedisLock redisLock = distributedLockClient.getRedisLock("lock");
        // 加锁
        redisLock.lock();


        try {
            // 1.查询库存信息
            String stock = redisTemplate.opsForValue().get("stock");

            // 2.更新库存
            if (stock != null && stock.length() != 0) {
                int kc = Integer.parseInt(stock);
                if (kc > 0) {
                    // 3.扣减库存
                    redisTemplate.opsForValue().set("stock", String.valueOf(--kc));
                }
            }
//            this.test();// 测试可重入锁，当用有锁的线程重复获取锁时value会增加

        } finally {
            redisLock.unlock();
        }
    }


    public static void main(String[] args) {
        System.out.println("定时任务初始时间：" + System.currentTimeMillis());

//        Timer定时器
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("定时任务执行时间：" + System.currentTimeMillis());
            }
        }, 5000, 10000);//delay：第一次执行延迟时间，period：周期时间
    }

    /**
     * 测试可重入锁，当用有锁的线程重复获取锁时value会增加
     */
    public void test() {
        DistributedRedisLock lock = distributedLockClient.getRedisLock("lock");
        lock.lock();
        System.out.println("测试可重入锁....");
        lock.unlock();
    }


    /**
     * redis实现分布式锁
     * <p>
     * 1. 多个客户端同时获取锁（setnx）
     * 2. 获取成功，执行业务逻辑，执行完成释放锁（del）
     * 3. 其他客户端等待重试
     */
    public void deBook4() {
        // 用于判断是否是自己的锁标识
        String uuid = UUID.randomUUID().toString();
        // 加锁跟设置过期时间要保证原子性，这里是用一个指令实现
        while (!Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS))) {//设置不成功一直循环
            //重试，循环
            try {
                // 需要睡一会让其他线程抢，不然一直重试，导致锁的竞争压力更大，从而性能降低
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("等待的线程：" + Thread.currentThread().getName());
        }

        try {
            // 1.查询库存信息
            String stock = redisTemplate.opsForValue().get("stock");

            // 2.更新库存
            if (stock != null && stock.length() != 0) {
                int kc = Integer.parseInt(stock);
                if (kc > 0) {
                    redisTemplate.opsForValue().set("stock", String.valueOf(--kc));
                }
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        } finally {// 锁一定要释放
            // 防误删
            // 先判断是否是自己的锁，判断跟删除也要保证原子性，因为没有相应的一个指令可以实现，要引用Lua脚本
//            if(StringUtils.equals(redisTemplate.opsForValue().get("lock"),uuid)){
//                // 释放锁
//                redisTemplate.delete("lock");
//            }

            String scrip = "if redis.call('get', KEYS[1]) == ARGV[1] " +
                    "then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else " +
                    "return 0 " +
                    "end";
            redisTemplate.execute(new DefaultRedisScript<>(scrip, Boolean.class), Arrays.asList("lock"), uuid);
        }

    }


    /**
     * redis实现乐观锁（CAS机制：compare and set），比较版本值是否改变，来更新值
     */
    public void deBook3() {

        redisTemplate.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.watch("stock");

                // 1.查询库存信息
                Object stock = operations.opsForValue().get("stock");

                // 2. 判断库存是否充足
                int st = 0;
                if (stock != null && (st = Integer.parseInt(stock.toString())) > 0) {
                    // 3. 扣减库存
                    operations.multi();// 加锁
                    operations.opsForValue().set("stock", String.valueOf(--st));
                    List exec = operations.exec();
                    if (exec == null || exec.size() == 0) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        deBook();
                    }
                    return exec;
                }
                return null;
            }
        });

    }

    //    @Transactional
//    jvm本地锁实现乐观锁
    public void deBook2() {

        lock.lock();

        try {

            Book book = bookMapper.selectById(1L);

            //获取版本
            int version = book.getVersion();

            if (book.getCount() > 0) {
                book.setCount(book.getCount() - 1);
            }

            // 每次更新版本加1
            book.setVersion(book.getVersion() + 1);

            if (bookMapper.update(book, new UpdateWrapper<Book>()
                    .eq("id", book.getId()).eq("version", version)) == 0) {// 更新失败
//                try {
//                    Thread.sleep(40);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }

                // 重试
                deBook();
            }

            System.out.println("Book库存：" + book.getCount());
        } finally {
            lock.unlock();
        }
    }


    @Transactional
    public void deBook1() {

        lock.lock();

        try {
            LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<>();

            wrapper.eq(Book::getId, 1);
            Book book = bookMapper.selectOne(wrapper);

            if (book.getCount() > 0) {
                book.setCount(book.getCount() - 1);
            }

            bookMapper.updateById(book);

            System.out.println("Book库存：" + book.getCount());
        } finally {
            lock.unlock();
        }
    }

    public void testReadLock() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        // 加读锁
        rwLock.readLock().lock(10,TimeUnit.SECONDS);
        // TODO:一系列读操作...
        rwLock.readLock().unlock();

    }

    public void testWriteLock() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        // 加读锁
        rwLock.writeLock().lock(10,TimeUnit.SECONDS);
        // TODO:一系列写操作...
        rwLock.writeLock().unlock();
    }



}
