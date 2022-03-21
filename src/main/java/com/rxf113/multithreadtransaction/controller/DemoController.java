package com.rxf113.multithreadtransaction.controller;

import com.rxf113.multithreadtransaction.MultiThreadTransaction;
import com.rxf113.multithreadtransaction.util.Pair;
import com.rxf113.multithreadtransaction.mapper.DemoMapper;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author rxf113
 */
@Controller("/demo")
@RestController
public class DemoController {

    @Resource
    private MultiThreadTransaction multiThreadTransaction;

    @Lookup
    public MultiThreadTransaction getMultiThreadTransaction() {
        return multiThreadTransaction;
    }

    @Resource
    private DemoMapper demoMapper;

    @GetMapping(value = "multi")
    public Object demo() {
        List<Pair<String, String>> list = new ArrayList<>();
        Collections.addAll(list, Pair.pair("6", "name1"), Pair.pair("4", "name2"), Pair.pair("5", "name3"));

        multiThreadTransaction.executeWithTransaction(list, 2, (simpleList) -> {
            for (Pair<String, String> pair : simpleList) {
                demoMapper.insert(pair.first, pair.second);
            }
        });
        boolean result = multiThreadTransaction.syncAndResult();
        if (!result) {
            List<Exception> exceptions = multiThreadTransaction.getExceptions();
            //todo do something
        }
        return "null";
    }

}
