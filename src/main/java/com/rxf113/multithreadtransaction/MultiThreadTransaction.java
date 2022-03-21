package com.rxf113.multithreadtransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 多线程事务控制类
 *
 * @author rxf113
 */
@Component
@Scope("prototype")
public class MultiThreadTransaction {

    Logger logger = LoggerFactory.getLogger(MultiThreadTransaction.class);

    /**
     * 等待所有线程完成的超时时间 / 秒
     */
    private static final Integer TIMEOUT_SECONDS = 2;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private PlatformTransactionManager platformTransactionManager;

    CountDownLatch countDownLatch;

    AtomicBoolean wrong;

    List<CompletableFuture<Void>> completableFutures;

    private List<Exception> exceptions = new ArrayList<>();

    @SuppressWarnings("all")
    public static <T> List<List<? extends T>> splitList(List<List<? extends T>> res, List<? extends T> list, int batchSize) {
        if (list.size() < batchSize) {
            res.add(list);
            return res;
        }
        res.add(list.subList(0, batchSize));
        return splitList(res, list.subList(batchSize, list.size()), batchSize);
    }

    public <T> void executeWithTransaction(List<? extends T> dataList, int batchSize, Consumer<List<? extends T>> consumer) {
        //拆分数据
        List<List<? extends T>> splitList = splitList(new ArrayList<>(), dataList, batchSize);
        //初始化属性
        countDownLatch = new CountDownLatch(splitList.size());
        wrong = new AtomicBoolean(false);
        completableFutures = new ArrayList<>(splitList.size());

        for (List<? extends T> simpleList : splitList) {

            CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {

                TransactionStatus transactionStatus = platformTransactionManager.getTransaction(new DefaultTransactionDefinition());

                try {
                    //business
                    consumer.accept(simpleList);
                } catch (Exception e) {
                    //不抛异常，在最后统一处理
                    exceptions.add(e);
                    wrong.set(true);
                } finally {
                    countDownLatch.countDown();
                }

                try {
                    countDownLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    logger.error("wait timeout!");
                    wrong.set(true);
                    Thread.currentThread().interrupt();
                }

                if (wrong.get()) {
                    Objects.requireNonNull(transactionTemplate.getTransactionManager()).rollback(transactionStatus);
                } else {
                    Objects.requireNonNull(transactionTemplate.getTransactionManager()).commit(transactionStatus);
                }
            });
            completableFutures.add(completableFuture);
        }
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
    }


    /**
     * 同步返回
     *
     * @return true: 成功   false:失败,可动再获取Exceptions
     */
    public boolean syncAndResult() {
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
                .join();
        return !wrong.get();
    }

    public List<Exception> getExceptions() {
        return exceptions;
    }

}
