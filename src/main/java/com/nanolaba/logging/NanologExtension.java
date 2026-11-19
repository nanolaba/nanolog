package com.nanolaba.logging;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A JUnit 5 extension that prints the log of a test only when the test fails, for a test run read by a person or a
 * coding agent: the log of a passing test is noise, the log of a failing one is the evidence.
 * <pre>
 * &#64;ExtendWith(NanologExtension.class)
 * class PaymentServiceTest { ... }
 * </pre>
 * While a test runs, what its thread writes to the logger of {@code LOG} is held back, at the levels of that logger.
 * When the test fails, it is written after all, between {@code --- log of PaymentServiceTest.charge ---} and
 * {@code --- end of the log of PaymentServiceTest.charge ---}; when it passes or is aborted, it goes only to the run
 * log, if that is on. Tests that JUnit runs in parallel keep their logs apart, and {@link MemoryLogger} works inside
 * a test as usual. {@code nanolog.junit.output} changes what is printed: {@code failed}, the default, {@code all}
 * (every log, as it is written) or {@code none}.
 * <p>
 * The log of the class outside its tests is held back the same way, from {@code beforeAll} to {@code afterAll}: the
 * {@code @BeforeAll} and {@code @AfterAll} methods, and the test instances that JUnit creates, in which
 * {@code SpringExtension} starts the application context. It is written between {@code --- log of class
 * PaymentServiceTest ---} and its end line when a test of the class fails or the class itself does, after the tests
 * of the class; otherwise it goes only to the run log (#100).
 * <p>
 * JUnit registers it for every test class with {@code junit.jupiter.extensions.autodetection.enabled=true}. It is
 * compiled against the JUnit Jupiter API, which the tests have anyway; Nanolog itself has no dependencies.
 */
public final class NanologExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback,
                                               TestWatcher {

    /**
     * The held-back log of each running test and test class, by its unique ID. Not in the store of the test: JUnit 5.13
     * and newer close that store before the {@code TestWatcher} callbacks, which then cannot take the log out of it.
     */
    private static final Map<String, Held> BUFFERS = new ConcurrentHashMap<>();

    /**
     * {@code nanolog.junit.output}, read once per JVM.
     */
    private static volatile String output;

    /**
     * The held-back log of a test or a test class, with the buffers of the enclosing class that it took off the thread
     * while it is open, so that each entry is held back once.
     */
    private static final class Held {

        final MemoryLogger buffer = MemoryLogger.buffer();
        final List<MemoryLogger> paused;
        /**
         * Whether a test of the class failed; set from the thread of that test.
         */
        volatile boolean failed;

        Held(List<MemoryLogger> paused) {
            this.paused = paused;
        }

        /**
         * Stops holding back and puts the buffers of the enclosing class back on the thread.
         */
        void close() {
            buffer.close();
            LOG.resumeBuffers(paused);
        }
    }

    /**
     * Creates the extension; JUnit does, for {@code @ExtendWith} or through {@code ServiceLoader}.
     */
    public NanologExtension() {
        // the setting is read once per JVM, when the first test starts
    }

    private static String output() {
        String value = output;
        if (value == null) {
            value = DefaultLoggerCreator.readChoice(DefaultLoggerCreator.PROPERTY_JUNIT_OUTPUT, "failed", "all", "none");
            output = value;
        }
        return value;
    }

    /**
     * Reads {@code nanolog.junit.output} again at the next test; for tests.
     */
    static void reset() {
        output = null;
    }

    /**
     * Starts holding back the log of the class, unless every log is printed.
     *
     * @param context the context of the class
     */
    @Override
    public void beforeAll(ExtensionContext context) {
        hold(context);
    }

    /**
     * Stops holding back the log of the class and writes it, framed by the name of the class, if a test of the class
     * failed or the class did; otherwise only the run log, if it is on, receives it.
     *
     * @param context the context of the class
     */
    @Override
    public void afterAll(ExtensionContext context) {
        Held held = BUFFERS.remove(context.getUniqueId());
        if (held != null) {
            held.close();
            boolean failed = held.failed || context.getExecutionException().isPresent();
            write(held.buffer, "class " + context.getRequiredTestClass().getSimpleName(), failed);
        }
    }

    /**
     * Starts holding back the log of the test, unless every log is printed.
     *
     * @param context the context of the test
     */
    @Override
    public void beforeEach(ExtensionContext context) {
        hold(context);
    }

    /**
     * Stops holding back the log of the test; what was held back waits for the outcome of the test.
     *
     * @param context the context of the test
     */
    @Override
    public void afterEach(ExtensionContext context) {
        Held held = BUFFERS.get(context.getUniqueId());
        if (held != null) {
            held.close();
        }
    }

    private static void hold(ExtensionContext context) {
        if (!"all".equals(output())) {
            BUFFERS.put(context.getUniqueId(), new Held(LOG.pauseBuffers()));
        }
    }

    /**
     * Writes the log of the failed test, framed by its name, unless {@code nanolog.junit.output=none}, and marks the
     * classes around it as failed, so that their logs are written too.
     *
     * @param context the context of the test
     * @param cause   why the test failed
     */
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        for (Optional<ExtensionContext> parent = context.getParent(); parent.isPresent(); parent = parent.get().getParent()) {
            Held enclosing = BUFFERS.get(parent.get().getUniqueId());
            if (enclosing != null) {
                enclosing.failed = true;
            }
        }
        Held held = BUFFERS.remove(context.getUniqueId());
        if (held != null) {
            write(held.buffer, name(context), true);
        }
    }

    /**
     * Drops the log of the passed test; only the run log, if it is on, receives it.
     *
     * @param context the context of the test
     */
    @Override
    public void testSuccessful(ExtensionContext context) {
        dropLog(context);
    }

    /**
     * Drops the log of the aborted test, as of a passed one.
     *
     * @param context the context of the test
     * @param cause   why the test was aborted
     */
    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        dropLog(context);
    }

    private static void dropLog(ExtensionContext context) {
        Held held = BUFFERS.remove(context.getUniqueId());
        if (held != null) {
            write(held.buffer, name(context), false);
        }
    }

    /**
     * Writes a held-back log to the logger it was kept from, framed by {@code name}, when it failed and
     * {@code nanolog.junit.output=failed}; otherwise gives it to the run log.
     */
    private static void write(MemoryLogger buffer, String name, boolean failed) {
        List<LogEntry> entries = buffer.getEntries();
        if (failed && "failed".equals(output()) && !entries.isEmpty()) {
            SystemStreams.out().println("--- log of " + name + " ---");
            for (LogEntry entry : entries) {
                try {
                    buffer.keptFrom.log(entry);
                } catch (Throwable t) {
                    LOG.reportLoggerFailure(buffer.keptFrom, t, "the log of a failed test is not printed");
                    break;
                }
            }
            SystemStreams.out().println("--- end of the log of " + name + " ---");
        } else {
            RunLog.replay(buffer.keptFrom, entries);
        }
    }

    /**
     * {@code PaymentServiceTest.charge}, followed by the display name when the test has one of its own, as a repeated
     * or parameterized test does.
     */
    private static String name(ExtensionContext context) {
        Method method = context.getRequiredTestMethod();
        String name = context.getRequiredTestClass().getSimpleName() + "." + method.getName();
        return context.getDisplayName().equals(method.getName() + "()") ? name : name + " " + context.getDisplayName();
    }
}
