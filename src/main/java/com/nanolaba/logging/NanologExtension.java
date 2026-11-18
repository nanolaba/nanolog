package com.nanolaba.logging;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.lang.reflect.Method;
import java.util.List;

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
 * JUnit registers it for every test class with {@code junit.jupiter.extensions.autodetection.enabled=true}. It is
 * compiled against the JUnit Jupiter API, which the tests have anyway; Nanolog itself has no dependencies.
 */
public final class NanologExtension implements BeforeEachCallback, AfterEachCallback, TestWatcher {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(NanologExtension.class);
    private static final String BUFFER = "buffer";

    /**
     * {@code nanolog.junit.output}, read once per JVM.
     */
    private static volatile String output;

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
     * Starts holding back the log of the test, unless every log is printed.
     *
     * @param context the context of the test
     */
    @Override
    public void beforeEach(ExtensionContext context) {
        if (!"all".equals(output())) {
            context.getStore(NAMESPACE).put(BUFFER, MemoryLogger.buffer());
        }
    }

    /**
     * Stops holding back the log of the test; what was held back waits for the outcome of the test.
     *
     * @param context the context of the test
     */
    @Override
    public void afterEach(ExtensionContext context) {
        MemoryLogger buffer = context.getStore(NAMESPACE).get(BUFFER, MemoryLogger.class);
        if (buffer != null) {
            buffer.close();
        }
    }

    /**
     * Writes the log of the failed test, framed by its name, unless {@code nanolog.junit.output=none}.
     *
     * @param context the context of the test
     * @param cause   why the test failed
     */
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        MemoryLogger buffer = context.getStore(NAMESPACE).remove(BUFFER, MemoryLogger.class);
        if (buffer == null) {
            return;
        }
        List<LogEntry> entries = buffer.getEntries();
        if ("failed".equals(output()) && !entries.isEmpty()) {
            String name = name(context);
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
        MemoryLogger buffer = context.getStore(NAMESPACE).remove(BUFFER, MemoryLogger.class);
        if (buffer != null) {
            RunLog.replay(buffer.keptFrom, buffer.getEntries());
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
