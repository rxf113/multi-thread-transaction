**<center> spring 中多线程批处理及事务问题 & [springmvc 子线程获取不到 RequestAttributes 问题](#springmvc) </center>**
------

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
                    if(!wrong.get()){
                        consumer.accept(simpleList);
                    }               
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

    /**
     * 自定义返回类
     */
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

// 处理异常等
//    if(!result.isSuccess()){
//       List<Exception> exceptions = result.getExceptions();
//        //do something
//    }   
```


<div id="springmvc"></div>

### springmvc 子线程获取不到 RequestAttributes 问题


根据上面这种方式，在实际业务中测试了一下，果然出现了问题，子线程获取不到用户信息。我们的用户信息是，对当前线程request里的jwt信息解析出来的，现在问题是

org.springframework.web.context.request.RequestContextHolder 类 的 getRequestAttributes 方法，获取不到 RequestAttributes，自然也获取不到request

```java
@Nullable
public static RequestAttributes getRequestAttributes() {
    RequestAttributes attributes = requestAttributesHolder.get();
    if (attributes == null) {
        attributes = inheritableRequestAttributesHolder.get();
    }
    return attributes;
}
```



看这代码发现有个 inheritableRequestAttributesHolder ，如果 inheritableRequestAttributesHolder 里有东西的话子线程是能获取到的，

可是再看下代码，发现 springmvc 默认 inheritable 是 false，没开启 inheritableRequestAttributesHolder

```java
public static void setRequestAttributes(@Nullable RequestAttributes attributes) {
   setRequestAttributes(attributes, false);
}

/**
 * Bind the given RequestAttributes to the current thread.
 * @param attributes the RequestAttributes to expose,
 * or {@code null} to reset the thread-bound context
 * @param inheritable whether to expose the RequestAttributes as inheritable
 * for child threads (using an {@link InheritableThreadLocal})
 */
public static void setRequestAttributes(@Nullable RequestAttributes attributes, boolean inheritable) {
   if (attributes == null) {
      resetRequestAttributes();
   }
   else {
      if (inheritable) {
         inheritableRequestAttributesHolder.set(attributes);
         requestAttributesHolder.remove();
      }
      else {
         requestAttributesHolder.set(attributes);
         inheritableRequestAttributesHolder.remove();
      }
   }
}
```



于是我就想能不能添加个配置，开启 inheritable，再看看源码，找到了 org.springframework.web.filter.RequestContextFilter 类

```java
private boolean threadContextInheritable = false;


	/**
	 * Set whether to expose the LocaleContext and RequestAttributes as inheritable
	 * for child threads (using an {@link java.lang.InheritableThreadLocal}).
	 * <p>Default is "false", to avoid side effects on spawned background threads.
	 * Switch this to "true" to enable inheritance for custom child threads which
	 * are spawned during request processing and only used for this request
	 * (that is, ending after their initial task, without reuse of the thread).
	 * <p><b>WARNING:</b> Do not use inheritance for child threads if you are
	 * accessing a thread pool which is configured to potentially add new threads
	 * on demand (e.g. a JDK {@link java.util.concurrent.ThreadPoolExecutor}),
	 * since this will expose the inherited context to such a pooled thread.
	 */

		
//翻译一下 =================

	/**
      * 设置是否将 LocaleContext 和 RequestAttributes 公开为子线程可继承（使用 InheritableThreadLocal）。默认为“false”，
      * 以避免对生成的后台线程产生副作用。将此切换为“true”以启用自定义子线程的继承，
      * 这些子线程在请求处理期间产生并仅用于此请求（即，在其初始任务之后结束，不重用线程）。
      * 警告：如果您正在访问配置为可能按需添加新线程的线程池（例如，JDK java.util.concurrent.ThreadPoolExecutor），
      * 请不要对子线程使用继承，因为这会将继承的上下文暴露给这样的池线
      */   
	public void setThreadContextInheritable(boolean threadContextInheritable) {
		this.threadContextInheritable = threadContextInheritable;
	}

```



反正就是说不建议使用 InheritableThreadLocal。所以最好别通过这种方式来处理。最后还是选择手动处理，在多线程调用的时候

```java
//手动在主线程获取requestAttributes
RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

MultiThreadTransaction.Result result = multiThreadTransaction.executeWithTransaction(dataList, 20, simpleList -> {
        
        //放入子线程里
        RequestContextHolder.setRequestAttributes(requestAttributes);

        //....其他代码
}
```
