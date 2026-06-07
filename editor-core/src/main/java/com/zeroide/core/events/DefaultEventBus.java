package com.zeroide.core.events;

import com.zeroide.api.EventBus;
import com.zeroide.api.EventListener;
import com.zeroide.api.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultEventBus implements EventBus {
    private static final Logger log = LoggerFactory.getLogger(DefaultEventBus.class);

    private final Map<Class<?>, CopyOnWriteArrayList<EventListener<?>>> listeners = new ConcurrentHashMap<>();

    @Override
    public <T> Subscription subscribe(Class<T> eventType, EventListener<T> listener) {
        listeners.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> {
            List<EventListener<?>> registered = listeners.get(eventType);
            if (registered != null) {
                registered.remove(listener);
            }
        };
    }

    @Override
    public void publish(Object event) {
        if (event == null) {
            return;
        }
        listeners.forEach((eventType, registered) -> {
            if (eventType.isAssignableFrom(event.getClass())) {
                for (EventListener<?> listener : registered) {
                    dispatch(listener, event);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> void dispatch(EventListener<?> listener, Object event) {
        try {
            ((EventListener<T>) listener).onEvent((T) event);
        } catch (RuntimeException ex) {
            log.warn("Event listener failed for {}", event.getClass().getSimpleName(), ex);
        }
    }
}
