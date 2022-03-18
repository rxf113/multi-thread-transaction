package com.rxf113.multithreadtransaction;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author rxf113
 */
@SpringBootApplication
@MapperScan("com.rxf113.multithreadtransaction.mapper")
public class MultiThreadTransactionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiThreadTransactionApplication.class, args);
    }

}
