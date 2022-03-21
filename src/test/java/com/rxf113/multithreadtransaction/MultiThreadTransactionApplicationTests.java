package com.rxf113.multithreadtransaction;

import com.rxf113.multithreadtransaction.mapper.DemoMapper;
import com.rxf113.multithreadtransaction.util.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SpringBootTest
class MultiThreadTransactionApplicationTests {

    @Resource
    private DemoMapper demoMapper;

    @Resource
    private MultiThreadTransaction multiThreadTransaction;

    @Test
    @Transactional(rollbackFor = Exception.class)
    void contextLoads() {
        List<Pair<String, String>> list = new ArrayList<>();
        Collections.addAll(list, Pair.pair("1", "name1"), Pair.pair("4", "name2"), Pair.pair("5", "name3"));

        MultiThreadTransaction.Result result = multiThreadTransaction.executeWithTransaction(list, 2, (simpleList) -> {
            for (Pair<String, String> pair : simpleList) {
                demoMapper.insert(pair.first, pair.second);
            }
        });

        if (result.isSuccess()) {
            System.out.println("success!");
        } else {
            //do something...
        }

    }
}