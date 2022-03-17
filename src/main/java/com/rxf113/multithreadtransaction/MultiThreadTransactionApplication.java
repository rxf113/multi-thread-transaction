package com.rxf113.multithreadtransaction;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.stream.IntStream;

/**
 * @author rxf113
 */
@SpringBootApplication
@MapperScan("com.rxf113.multithreadtransaction.mapper")
public class MultiThreadTransactionApplication {

    public static void main(String[] args) {
        IntStream.range(0, 6).forEach(System.out::println);
//        SpringApplication.run(MultiThreadTransactionApplication.class, args);
    }

}
