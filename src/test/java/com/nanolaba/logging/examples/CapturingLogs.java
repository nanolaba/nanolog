package com.nanolaba.logging.examples;

import com.nanolaba.logging.ConsoleLogger;
import com.nanolaba.logging.CompositeLogger;
import com.nanolaba.logging.LOG;
import com.nanolaba.logging.LogEntry;
import com.nanolaba.logging.MemoryLogger;

/**
 * Capturing log entries in a test without a mocking framework. The regions are imported into the README.
 * The methods carry no {@code @Test} annotation on purpose: examples only need to compile.
 */
public class CapturingLogs {

    // nrg:begin:capture
    void chargeIsRetried() {
        try (MemoryLogger log = MemoryLogger.install()) { // close() puts the previous logger back
            new PaymentService().charge(42); // the code under test

            log.assertLogged(LogEntry.LogEntryLevel.WARN, "Charge of 42 failed, retrying");
            log.assertNoneAtOrAbove(LogEntry.LogEntryLevel.ERROR); // a failure lists everything that was logged
        }
    }
    // nrg:end:capture

    // nrg:begin:parallel
    void chargeIsRetriedInParallel() { // safe while JUnit runs other tests in parallel
        try (MemoryLogger log = MemoryLogger.capture()) { // this thread only; the logger of LOG stays installed
            new PaymentService().charge(42);

            log.assertLogged(LogEntry.LogEntryLevel.WARN, "Charge of 42 failed, retrying");
        }
    }
    // nrg:end:parallel

    // nrg:begin:diagnostics
    void noPlaceholderMismatches() { // the test run sets -Dnanolog.strict=true
        try (MemoryLogger log = MemoryLogger.install()) {
            new PaymentService().charge(42);

            log.assertNoDiagnostics(); // fails with the call site and the template of every mismatch
        }
    }
    // nrg:end:diagnostics

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
