package com.nanolaba.logging;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MemoizingMessageTest {

    @Test
    public void testMemoizingMessage() {

        AtomicInteger counter = new AtomicInteger(0);

        LOG.init(entry -> {
            entry.getMessage();
            entry.getMessage();
            entry.getMessage();
        });

        LOG.info(counter::incrementAndGet);

        assertEquals(1, counter.get());
    }

    @Test
    public void testMemoizingNull() {

        AtomicInteger counter = new AtomicInteger(0);

        LOG.init(entry -> {
            entry.getMessage();
            entry.getMessage();
            entry.getMessage();
        });

        LOG.info(() -> {
            counter.incrementAndGet();
            return null;
        });

        assertEquals(1, counter.get());
    }
}
