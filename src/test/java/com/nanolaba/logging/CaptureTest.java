package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MemoryLogger#capture()}: the entries of one thread, for tests that run in parallel in one JVM.
 */
class CaptureTest extends AbstractLoggerTest {

    private final Consumer<Thread> originalHooks = Summary.shutdownHooks;
    private MemoryLogger global;

    @BeforeEach
    public void setUp() {
        Summary.shutdownHooks = hook -> { };
        Summary.reset();
        DbgSource.clearCache();
        LOG.clearLimits();
        DefaultLoggerCreator.forgetDiagnostics();
        global = new MemoryLogger();
        LOG.init(global);
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty(DefaultLoggerCreator.PROPERTY_STRICT);
        Summary.reset();
        Summary.shutdownHooks = originalHooks;
        LOG.clearLimits();
        DefaultLoggerCreator.forgetDiagnostics();
        LOG.init();
    }

    private static List<String> messages(MemoryLogger memory) {
        return memory.getMessages();
    }

    /**
     * Runs the task on another thread and waits for it.
     */
    private static void onAnotherThread(Runnable task) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        thread.start();
        thread.join();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @Test
    public void testACaptureKeepsTheEntriesOfItsThreadFromTheLogger() throws InterruptedException {
        try (MemoryLogger capture = MemoryLogger.capture()) {
            LOG.info("mine");
            onAnotherThread(() -> LOG.info("other"));

            assertEquals(Collections.singletonList("mine"), messages(capture));
        }
        LOG.info("after");

        assertEquals(Arrays.asList("other", "after"), messages(global));
    }

    @Test
    public void testTwoThreadsSeeOnlyTheirOwnEntries() throws InterruptedException {
        CountDownLatch bothOpen = new CountDownLatch(2);
        List<List<String>> seen = Collections.synchronizedList(new ArrayList<>());
        Runnable test = () -> {
            String name = Thread.currentThread().getName();
            try (MemoryLogger capture = MemoryLogger.capture()) {
                bothOpen.countDown();
                await(bothOpen);
                for (int i = 0; i < 100; i++) {
                    LOG.info(name);
                }
                seen.add(messages(capture));
            }
        };
        Thread first = new Thread(test, "first");
        Thread second = new Thread(test, "second");
        first.start();
        second.start();
        first.join();
        second.join();

        assertEquals(2, seen.size());
        for (List<String> messages : seen) {
            assertEquals(100, messages.size());
            assertEquals(1, messages.stream().distinct().count(), messages.toString());
        }
        assertEquals(Collections.emptyList(), messages(global));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testCaptureForwardingPassesTheEntriesOnAtTheLevelsOfTheLogger() {
        LOG.init(new ConsoleLogger().setLevel("WARN").setShowDate(false));

        try (MemoryLogger capture = MemoryLogger.captureForwarding()) {
            LOG.debug("only captured");
            LOG.warn("both");

            assertEquals(Arrays.asList("only captured", "both"), messages(capture));
        }
        assertEquals("WARN [CaptureTest] both" + System.lineSeparator(), getOutAndClear());
    }

    @Test
    public void testEveryLevelIsCapturedWhateverTheLogger() {
        LOG.init(new ConsoleLogger().setLevel("OFF"));

        try (MemoryLogger capture = MemoryLogger.capture()) {
            assertTrue(LOG.isTraceEnabled());
            assertTrue(LOG.isTraceEnabled(String.class));
            LOG.trace("t");
            LOG.debug(String.class, "with a class");
            LOG.dbg(42);
            LOG.once().trace("once");
            LOG.once().dbg();

            assertEquals(5, capture.getEntries().size(), messages(capture).toString());
        }
        assertFalse(LOG.isTraceEnabled());
        assertEquals("", getOutAndClear());
    }

    @Test
    public void testNestedCapturesBothSeeTheEntriesOfTheInnerOne() {
        try (MemoryLogger outer = MemoryLogger.captureForwarding()) {
            LOG.info("outer");
            try (MemoryLogger inner = MemoryLogger.capture()) {
                LOG.info("inner");

                assertEquals(Collections.singletonList("inner"), messages(inner));
            }
            LOG.info("outer again");

            assertEquals(Arrays.asList("outer", "inner", "outer again"), messages(outer));
        }
        assertEquals(Arrays.asList("outer", "outer again"), messages(global), "the inner capture keeps its entries");
    }

    @Test
    public void testACaptureClosedTwiceOrFromAnotherThreadEndsOnce() throws InterruptedException {
        MemoryLogger first = MemoryLogger.capture();
        MemoryLogger second = MemoryLogger.capture();
        assertEquals(2, LOG.openCaptures());
        first.close();
        assertEquals(1, LOG.openCaptures());
        first.close();
        assertEquals(1, LOG.openCaptures());
        LOG.info("second only");
        onAnotherThread(second::close);
        LOG.info("nobody");
        assertEquals(0, LOG.openCaptures(), "logging is back on its fast path");

        assertEquals(Collections.singletonList("second only"), messages(second));
        assertEquals(Collections.emptyList(), messages(first));
        assertEquals(Collections.singletonList("nobody"), messages(global));
        onAnotherThread(() -> {
            try (MemoryLogger capture = MemoryLogger.capture()) {
                LOG.info("a new capture");
                assertEquals(1, capture.getEntries().size());
            }
        });
    }

    @Test
    public void testTheDiagnosticsOfTheThreadReachTheCapture() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_STRICT, "true");
        LOG.init();
        LOG.init(global);
        getErrAndClear();

        try (MemoryLogger capture = MemoryLogger.capture()) {
            LOG.info("{} and {}", 1);

            assertEquals(1, capture.getDiagnostics().size());
            assertThrows(AssertionError.class, capture::assertNoDiagnostics);
        }
        assertEquals(Collections.emptyList(), global.getDiagnostics());

        try (MemoryLogger capture = MemoryLogger.captureForwarding()) {
            LOG.info("{} or {}", 1);

            assertEquals(1, capture.getDiagnostics().size());
        }
        assertEquals(1, global.getDiagnostics().size(), "forwarded");
    }

    @Test
    public void testAFailingLoggerDoesNotLoseTheCapturedEntry() {
        LOG.init(entry -> {
            throw new IllegalStateException("broken");
        });
        getErrAndClear();

        try (MemoryLogger capture = MemoryLogger.captureForwarding()) {
            LOG.warn("kept");

            assertEquals(Collections.singletonList("kept"), messages(capture));
        }
        LOG.clearReportedLoggerFailures();
    }

    @Test
    public void testADbgEntryIsForwardedWhateverTheLevelsInAgentMode() {
        MemoryLogger quiet = new MemoryLogger() {
            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                return level.ordinal() >= WARN.ordinal();
            }
        };
        LOG.init(quiet);
        Dbg.ignoreLevels = true;
        try (MemoryLogger capture = MemoryLogger.captureForwarding()) {
            LOG.dbg("shown");
            LOG.debug("not forwarded");

            assertEquals(2, capture.getEntries().size());
        } finally {
            Dbg.ignoreLevels = false;
        }
        assertEquals(1, quiet.getEntries().size(), messages(quiet).toString());
        assertTrue(quiet.getEntries().get(0).isDbg());
    }

    @Test
    public void testALoggerInstalledInsideACaptureReceivesTheEntries() {
        try (MemoryLogger capture = MemoryLogger.capture()) {
            try (MemoryLogger installed = MemoryLogger.install()) {
                LOG.info("both");

                assertEquals(Collections.singletonList("both"), messages(installed));
            }
            assertEquals(Collections.singletonList("both"), messages(capture));
        }
        assertEquals(Collections.emptyList(), messages(global));
    }

    @Test
    public void testABufferTakesTheLevelsOfTheLoggerAndHoldsTheEntriesBack() {
        ConsoleLogger console = new ConsoleLogger().setLevel("WARN");
        LOG.init(console);

        try (MemoryLogger buffer = MemoryLogger.buffer()) {
            assertFalse(LOG.isDebugEnabled(), "the levels of the logger");
            LOG.debug("below the level");
            LOG.warn("held back");
            try (MemoryLogger capture = MemoryLogger.capture()) {
                LOG.debug("for the capture only");

                assertEquals(Collections.singletonList("for the capture only"), messages(capture));
            }

            assertEquals(Collections.singletonList("held back"), messages(buffer));
            assertTrue(buffer.keptFrom == console);
        }
        assertEquals("", getOutAndClear());
    }

    @Test
    public void testAnInstalledMemoryLoggerAndACaptureWorkTogether() {
        try (MemoryLogger installed = MemoryLogger.install()) {
            try (MemoryLogger capture = MemoryLogger.capture()) {
                LOG.info("captured");
            }
            LOG.info("installed");

            assertEquals(Collections.singletonList("installed"), messages(installed));
        }
    }
}
