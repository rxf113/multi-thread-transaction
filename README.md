## spring 中多线程批处理及事务问题

一个功能，需要对大量数据进行操作、验证、写库等等。比如处理一个一百万条数据的 List ，采用多线程优化一下，简单的思路是拆分这个 List 比如拆分成十个List 一个List 十万条数据，然后用十个线程执行。

这里我采用 CompletableFuture 的方式来实现， 简单写下代码 :

```java
    /**
     * 拆分list
     *
     * @param res 结果
     * @param list 原始list
     * @param batchSize 每一批的数量
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
     * 执行
     *
     * @param dataList 原始list
     * @param batchSize 每一批的数量
     * @param consumer 具体的业务逻辑
     * @param <T>
     */
    public <T> void execute(List<? extends T> dataList, int batchSize, Consumer<List<? extends T>> consumer) {
        //拆分数据
        List<List<? extends T>> splitList = splitList(new ArrayList<>(), dataList, batchSize);
        //记录 CompletableFuture
        List<CompletableFuture<Void>> completableFutures = new ArrayList<>(splitList.size());

        for (List<? extends T> simpleList : splitList) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                //执行业务代码
                consumer.accept(simpleList);
            });
            completableFutures.add(future);
        }
        //等待所有任务完成
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
    }
	
```


代码很简单，封装一个拆分List的方法，每次执行的时候，先拆分List，然后一个线程执行一部分数据。如果不考虑其他问题，那这个代码就可以直接用了。

但是如果业务中还涉及到了数据库的操作的话，就需要考虑事务问题了。原生的 spring 注解式或者编程式事务都不太能支持多个线程在一个事务里这种情况。

于是想着可以拓展编程式事务，以上面代码的例子，每一个线程都手动开启一个事务，用一个全局变量 **wrong** 记录执行状态，只要有一个线程执行失败就修改**wrong** 为失败，使用一个 **CountDownLatch** 保证每个线程执行完后都等待其他所有线程执行完成，然后判断 **wrong**，错误就回滚正确就提交。



跟着这个想法拓展一下上面的代码：

```java
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
                    //等待所有线程完成
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
```


这样有原业务需要改为多线程实现的话，只需要稍微改造下，就可以直接调用了



比如原业务是：

```java

@Resource
private DemoMapper demoMapper;

List<Pair<String, String>> list = new ArrayList<>();
Collections.addAll(list, Pair.pair("k1", "v1"), Pair.pair("k2", "v2"), Pair.pair("k3", "v3"));

for (Pair<String, String> pair : list) {
	demoMapper.insert(pair.first, pair.second);
}

```



那么现在只需稍加改造:

```java
@Resource
private MultiThreadTransaction multiThreadTransaction;

@Resource
private DemoMapper demoMapper;

MultiThreadTransaction.Result result = multiThreadTransaction.executeWithTransaction(list, 2, simpleList -> {
    for (Pair<String, String> pair : simpleList) {
        demoMapper.insert(pair.first, pair.second);
    }
});

//    if(!result.isSuccess()){
//       List<Exception> exceptions = result.getExceptions();
//        //do something
//    }   
```

