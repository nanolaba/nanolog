package com.nanolaba.logging;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NoUnnecessaryCallsTest {


    @Test
    public void testCalls() {

        AtomicInteger counter = new AtomicInteger(0);

        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
                counter.incrementAndGet();
                entry.getMessage();
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                return false;
            }
        });


        LOG.trace(counter::incrementAndGet);
        LOG.debug(counter::incrementAndGet);
        LOG.info(counter::incrementAndGet);
        LOG.warn(counter::incrementAndGet);
        LOG.error(counter::incrementAndGet);

        LOG.log(null, null, null, null);

        assertEquals(0, counter.get());
    }
}
