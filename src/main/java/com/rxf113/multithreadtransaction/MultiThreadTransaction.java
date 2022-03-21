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
public class MultiThreadTransaction {

    Logger logger = LoggerFactory.getLogger(MultiThreadTransaction.class);

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private PlatformTransactionManager platformTransactionManager;

    /**
     * 等待所有线程完成的超时时间 / 秒
     */
    private static final Integer TIMEOUT_SECONDS = 2;


    /**
     * 拆分list
     *
     * @param res       结果
     * @param list      原始list
     * @param batchSize 每一批的数量
     * @param <T>
     * @return
     */
    @SuppressWarnings("all")
    public static <T> List<List<? extends T>> splitList(List<List<? extends T>> res, List<? extends T> list, int batchSize) {
        if (list.size() <= batchSize) {
            res.add(list);
            return res;
        }
        res.add(list.subList(0, batchSize));
        return splitList(res, list.subList(batchSize, list.size()), batchSize);
    }

    /**
     * 执行(没有事务)
     *
     * @param dataList  原始list
     * @param batchSize 每一批的数量
     * @param consumer  具体的业务逻辑
     */
    public <T> void execute(List<? extends T> dataList, int batchSize, Consumer<List<? extends T>> consumer) {
        //拆分数据
        List<List<? extends T>> splitList = splitList(new ArrayList<>(), dataList, batchSize);
        //记录 CompletableFuture
        List<CompletableFuture<Void>> completableFutures = new ArrayList<>(splitList.size());

        for (List<? extends T> simpleList : splitList) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                consumer.accept(simpleList);
            });
            completableFutures.add(future);
        }
        //等待所有任务完成
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 执行(有事务)
     *
     * @param dataList  原始list
     * @param batchSize 每一批的数量
     * @param consumer  具体的业务逻辑
     * @return Result 自定义返回
     */
    public <T> Result executeWithTransaction(List<? extends T> dataList, int batchSize, Consumer<List<? extends T>> consumer) {
        //拆分数据
        List<List<? extends T>> splitList = splitList(new ArrayList<>(), dataList, batchSize);
        //初始化属性
        //记录线程执行完
        final CountDownLatch countDownLatch = new CountDownLatch(splitList.size());
        //错误标识
        final AtomicBoolean wrong = new AtomicBoolean(false);
        //记录所有CompletableFuture
        final List<CompletableFuture<Void>> completableFutures = new ArrayList<>(splitList.size());
        //记录所有异常
        final List<Exception> exceptions = new ArrayList<>();

        for (List<? extends T> simpleList : splitList) {

            CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                //每个线程开启一个事务
                TransactionStatus transactionStatus = platformTransactionManager.getTransaction(new DefaultTransactionDefinition());
                try {
                    //具体业务
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

                //提交或者回滚
                if (wrong.get()) {
                    Objects.requireNonNull(transactionTemplate.getTransactionManager()).rollback(transactionStatus);
                } else {
                    Objects.requireNonNull(transactionTemplate.getTransactionManager()).commit(transactionStatus);
                }
            });
            completableFutures.add(completableFuture);
        }
        //等待完成
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
        //返回结果
        if (wrong.get()) {
            return new Result(false, exceptions);
        }
        return new Result(true, null);
    }

    public static class Result {
        private final boolean success;
        private final List<Exception> exceptions;

        public Result(boolean success, List<Exception> exceptions) {
            this.success = success;
            this.exceptions = exceptions;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<Exception> getExceptions() {
            return exceptions;
        }
    }
}
