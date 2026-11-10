package com.nanolaba.logging.examples;

import com.nanolaba.logging.ConsoleLogger;
import com.nanolaba.logging.CompositeLogger;
import com.nanolaba.logging.LOG;
import com.nanolaba.logging.LogEntry;
import com.nanolaba.logging.MemoryLogger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capturing log entries in a test without a mocking framework. The regions are imported into the README.
 * The methods carry no {@code @Test} annotation on purpose: examples only need to compile.
 */
public class CapturingLogs {

    // nrg:begin:capture
    void chargeIsRetried() {
        try (MemoryLogger log = MemoryLogger.install()) { // close() puts the previous logger back
            new PaymentService().charge(42); // the code under test

            assertTrue(log.contains(LogEntry.LogEntryLevel.WARN, "Charge of 42 failed, retrying"));
        }
    }
    // nrg:end:capture

    void composite() {
        // nrg:begin:composite
        MemoryLogger memory = new MemoryLogger(1000); // keeps the newest 1000 entries
        LOG.init(new CompositeLogger(memory, new ConsoleLogger()));
        // nrg:end:composite
    }

    private static class PaymentService {

        void charge(int amount) {
            LOG.warn("Charge of {} failed, retrying", amount);
        }
    }
}
