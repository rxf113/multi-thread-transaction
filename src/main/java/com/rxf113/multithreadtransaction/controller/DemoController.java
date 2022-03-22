package com.rxf113.multithreadtransaction.controller;

import com.rxf113.multithreadtransaction.MultiThreadTransaction;
import com.rxf113.multithreadtransaction.mapper.DemoMapper;
import com.rxf113.multithreadtransaction.util.Pair;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

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

    @Resource
    private DemoMapper demoMapper;

    @GetMapping(value = "multi")
    public Object demo() {
        List<Pair<String, String>> list = new ArrayList<>();
        Collections.addAll(list, Pair.pair("k1", "v1"), Pair.pair("k2", "v2"), Pair.pair("k3", "v3"));
//        for (Pair<String, String> pair : list) {
//            demoMapper.insert(pair.first, pair.second);
//        }

        //手动在主线程获取requestAttributes
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        MultiThreadTransaction.Result result = multiThreadTransaction.executeWithTransaction(list, 2, simpleList -> {
            //放入子线程里
            RequestContextHolder.setRequestAttributes(requestAttributes);

            for (Pair<String, String> pair : simpleList) {
                demoMapper.insert(pair.first, pair.second);
            }
        });
        if (!result.isSuccess()) {
            List<Exception> exceptions = result.getExceptions();
            //do something
        }
        return null;
    }

}
