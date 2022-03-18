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
public class MultiThreadTransaction<P> {

    Logger logger = LoggerFactory.getLogger(MultiThreadTransaction.class);

    /**
     * 等待所有线程完成的超时时间 / 秒
     */
    private static final Integer TIMEOUT_SECONDS = 2;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private PlatformTransactionManager platformTransactionManager;

    public void initCounter(int taskNum) {
        countDownLatch = new CountDownLatch(taskNum);
    }

    CountDownLatch countDownLatch;

    AtomicBoolean wrong = new AtomicBoolean(false);

    private final List<CompletableFuture<?>> completableFutures = new ArrayList<>();

    public void execute(Consumer<P> consumer, P param) {
        CompletableFuture<?> completableFuture = CompletableFuture.supplyAsync(() -> {

            TransactionStatus transactionStatus = platformTransactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                //business
                consumer.accept(param);
            } catch (Exception e) {
                logger.error("business error!");
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

            return null;

        });
        completableFutures.add(completableFuture);
    }


    public void sync() {
        CompletableFuture<Void> allOf = CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]));
        allOf.join();
    }
}
