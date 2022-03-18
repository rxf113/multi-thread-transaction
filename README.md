# multi-thread-transaction

## spring 中多个线程的事务控制demo

### 使用: 
```java
        //引入工具类
        @Resource
        private MultiThreadTransaction<Pair> multiThreadTransaction;


        //初始化任务个数
        multiThreadTransaction.initCounter(list.size());

        //执行具体业务逻辑
        for (Pair<String, String> pair : list) {
            multiThreadTransaction.execute(params -> {
                //业务代码
                System.out.println(4888888);
                demoMapper.insert((String) params.first, (String) params.second);
            }, pair);
        }

        //同步等待业务执行完
        multiThreadTransaction.sync();
        
        //执行其他...
```