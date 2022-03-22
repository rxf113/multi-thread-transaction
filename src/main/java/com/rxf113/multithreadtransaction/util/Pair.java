package com.rxf113.multithreadtransaction.util;

/**
 * @author rxf113
 */
public class Pair<T, U> {

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
}
