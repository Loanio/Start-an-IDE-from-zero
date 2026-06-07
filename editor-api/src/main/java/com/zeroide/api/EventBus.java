package com.zeroide.api;

public interface EventBus {
    <T> Subscription subscribe(Class<T> eventType, EventListener<T> listener);

    void publish(Object event);
}
