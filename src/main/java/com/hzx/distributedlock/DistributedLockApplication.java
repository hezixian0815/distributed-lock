package com.hzx.distributedlock;

import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.hzx.distributedlock.mapper")
public class DistributedLockApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedLockApplication.class, args);
    }

}
