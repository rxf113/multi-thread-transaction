package com.rxf113.multithreadtransaction;

import com.rxf113.multithreadtransaction.mapper.DemoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

@SpringBootTest
class MultiThreadTransactionApplicationTests {

    @Autowired
    private DemoMapper demoMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    @Test
    @Transactional(rollbackFor = Exception.class)
    void contextLoads() {
        insertSS();
    }

    public void insertSS() {
        List<CompletableFuture<Integer>> cfList = new ArrayList<>(7);
        List<TransactionStatus> transactionStatuses = new ArrayList<>(5);



      //  transactionTemplate.execute(status -> {
            IntStream.range(9, 14).forEach(i -> {
                try {

                    CompletableFuture<Integer> completableFuture = CompletableFuture.supplyAsync(() -> {
                        TransactionStatus transactionStatus = platformTransactionManager.getTransaction(new DefaultTransactionDefinition());
                        int res = demoMapper.insert(Integer.toString(i), Integer.toString(i));
                        Objects.requireNonNull(transactionTemplate.getTransactionManager()).rollback(transactionStatus);
                        return res;
                    });
                    cfList.add(completableFuture);
                } catch (Exception e) {
                    System.out.println(123);
                }
            });

        //    return 250;
     //   });

        CompletableFuture<Void> allOf = CompletableFuture.allOf(cfList.toArray(new CompletableFuture[0]));
        allOf.join();
        System.out.println("都好了？");

    }

}

//@SpringBootTest
//public class SampleTest {
//
//
//
//    @Test
//    public void testSelect() {
//        System.out.println(("----- selectAll method test ------"));
//        List<User> userList = userMapper.selectList(null);
//        Assert.assertEquals(5, userList.size());
//        userList.forEach(System.out::println);
//    }
//
//}
