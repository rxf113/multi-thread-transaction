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
    private MultiThreadTransaction<Pair> multiThreadTransaction;

    @Test
    @Transactional(rollbackFor = Exception.class)
    void contextLoads() {
        List<Pair<String, String>> list = new ArrayList<>();
        Collections.addAll(list, Pair.pair("1", "name1"), Pair.pair("4", "name2"), Pair.pair("5", "name3"));

        multiThreadTransaction.initCounter(list.size());

        for (Pair<String, String> pair : list) {
            multiThreadTransaction.execute(params -> {
                //业务代码
                System.out.println(4888888);
                demoMapper.insert((String) params.first, (String) params.second);
            }, pair);
        }

        multiThreadTransaction.sync();

        System.out.println("success!");
    }
}