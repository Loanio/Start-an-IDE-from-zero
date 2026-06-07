package com.zeroide.api;

@FunctionalInterface
public interface Subscription extends AutoCloseable {
    @Override
    void close();
}
