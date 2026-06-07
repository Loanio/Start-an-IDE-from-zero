package com.zeroide.core.events;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultEventBusTest {
    @Test
    void publishesEventsToMatchingSubscribers() {
        DefaultEventBus bus = new DefaultEventBus();
        AtomicInteger calls = new AtomicInteger();

        bus.subscribe(String.class, ignored -> calls.incrementAndGet());
        bus.publish("changed");

        assertEquals(1, calls.get());
    }

    @Test
    void subscriptionCanBeClosed() {
        DefaultEventBus bus = new DefaultEventBus();
        AtomicInteger calls = new AtomicInteger();

        var subscription = bus.subscribe(String.class, ignored -> calls.incrementAndGet());
        subscription.close();
        bus.publish("changed");

        assertEquals(0, calls.get());
    }

    @Test
    void baseTypeSubscribersReceiveSubtypes() {
        DefaultEventBus bus = new DefaultEventBus();
        AtomicInteger calls = new AtomicInteger();

        bus.subscribe(Number.class, ignored -> calls.incrementAndGet());
        bus.publish(21);

        assertEquals(1, calls.get());
    }
}
