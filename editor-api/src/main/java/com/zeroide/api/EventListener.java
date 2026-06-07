package com.zeroide.api;

@FunctionalInterface
public interface EventListener<T> {
    void onEvent(T event);
}
