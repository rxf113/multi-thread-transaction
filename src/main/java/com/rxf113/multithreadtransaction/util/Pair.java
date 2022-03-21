package com.rxf113.multithreadtransaction.util;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RecursiveTask;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class Pair<T, U> {
    public Pair() {
        first = null;
        second = null;
    }

    public final T first;
    public final U second;

    public Pair(T first, U second) {
        this.second = second;
        this.first = first;
    }

    public static <T, U> Pair<T, U> pair(T first, U second) {
        return new Pair(first, second);
    }

    @Override
    public String toString() {
        return "(" + this.first + ", " + this.second + ")";
    }

    static class MyRec extends RecursiveTask<Integer> {

        @Override
        protected Integer compute() {
            return null;
        }
    }

    static int batchSize = 4;

    @SuppressWarnings("all")
    public static <T> List<List<? extends T>> splitList(List<List<? extends T>> res, List<? extends T> list, int batchSize) {
        if (list.size() < batchSize) {
            res.add(list);
            return res;
        }

        res.add(list.subList(0, batchSize));
        return splitList(res, list.subList(batchSize, list.size()), batchSize);
    }


    public static <T> void execute(List<List<? extends T>> lists, Consumer<List<? extends T>> consumer) {
        List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
        for (List<? extends T> list : lists) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

                    consumer.accept(list);

            });
            completableFutures.add(future);
        }
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
                .exceptionally(throwable -> {
                    throw new RuntimeException(throwable);
                })
                .join();
    }

    public static void main(String[] args) {
        //遍历 把小于k的数记录

        List<Integer> list = new ArrayList<>(10);
        IntStream.range(0, 11).forEach(list::add);
        List<List<? extends Integer>> res = new ArrayList<>();
        List<List<? extends Integer>> lists = Pair.splitList(res, list, 4);
        execute(lists, param -> {
            param.stream().forEach(System.out::println);
            if (param.size() < 4) {
                System.out.println(1 / 0);
            }
        });
        System.out.println(1243);
        CompletableFuture<Void> thenAccept = CompletableFuture.supplyAsync(() -> {

            System.out.println(Thread.currentThread().getId() + "iii 1 d");
            return 2;
        }).thenApply((param) -> {

            System.out.println(Thread.currentThread().getId() + "iii 2 d");
            return 3;
        }).thenApply(param -> {

            System.out.println(Thread.currentThread().getId() + "iii 3 d");
            return 4;
        }).thenAccept((param) -> {

            System.out.println(Thread.currentThread().getId() + "iii 4 d");
        });
        thenAccept.join();
        System.out.println(999);
    }

    static Set<Integer> set = new HashSet<>();

    public static boolean findTarget(TreeNode root, int k) {
        if (root != null) {
            int cuVal = root.val;
            if (cuVal < k) {
                if (set.contains(k - cuVal)) {
                    return true;
                } else {
                    set.add(cuVal);
                }
            }
            if (findTarget(root.left, k)) {
                return true;
            }
            return findTarget(root.right, k);
        }
        return false;
    }


    static public class TreeNode {
        int val;
        TreeNode left;
        TreeNode right;

        TreeNode() {
        }

        TreeNode(int val) {
            this.val = val;
        }

        TreeNode(int val, TreeNode left, TreeNode right) {
            this.val = val;
            this.left = left;
            this.right = right;
        }
    }
}
