package com.hzx.distributedlock.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @auther hzx
 * @create 2023/7/23 21:50
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 初始化配置对象
        Config config = new Config();
        // 使用单个redis服务模式
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379")// redis服务器地址
//                .setDatabase(0)// 指定redis数据库编号0~15
//                .setUsername("")// 设置redis用户名
//                .setPassword("121380")// 设置redis密码
//                .setConnectionMinimumIdleSize(10)// 连接池最小空闲连接数
//                .setConnectionPoolSize(50)// 连接池最大线程数
//                .setIdleConnectionTimeout(60000)// 线程的超时时间，超时没有被分配任务连接会被回收
//                .setConnectTimeout()// 客户端程序获取redis链接的超时时间
//                .setTimeout()// 响应超时时间
        ;
        return Redisson.create(config);
    }

}
