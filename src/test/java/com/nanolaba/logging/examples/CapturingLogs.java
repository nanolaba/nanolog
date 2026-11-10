package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;
import com.nanolaba.logging.LogEntry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capturing log entries in a test without a mocking framework. The region is imported into the README.
 * The method carries no {@code @Test} annotation on purpose: examples only need to compile.
 */
public class CapturingLogs {

    // nrg:begin:capture
    void chargeIsRetried() {
        List<LogEntry> entries = new CopyOnWriteArrayList<>();
        LOG.init(entries::add); // keep every entry in memory instead of printing it
        try {
            new PaymentService().charge(42); // the code under test

            assertTrue(entries.stream().anyMatch(entry ->
                    entry.getLevel() == LogEntry.LogEntryLevel.WARN
                    && entry.getFormattedMessage().equals("Charge of 42 failed, retrying")));
        } finally {
            LOG.init(); // LOG.init is JVM-wide: put the default logger back for the other tests
        }
    }
    // nrg:end:capture

    private static class PaymentService {

        void charge(int amount) {
            LOG.warn("Charge of {} failed, retrying", amount);
        }
    }
}
