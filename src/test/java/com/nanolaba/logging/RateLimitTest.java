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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.TRACE;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.once()} and {@code LOG.atMostEvery(...)}: rate limiting per call site, with the number of skipped calls
 * on the next entry that gets through.
 */
class RateLimitTest extends AbstractLoggerTest {

    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final CallerDetection.Locator originalLocator = LOG.callerLocator;
    private MemoryLogger memory;

    @BeforeEach
    public void install() {
        LOG.nanoClock = now::get;
        LOG.clearLimits();
        memory = MemoryLogger.install();
    }

    @AfterEach
    public void restore() {
        memory.close();
        LOG.nanoClock = System::nanoTime;
        LOG.callerLocator = originalLocator;
        CallerDetection.ThrowableWalk.stackTraceOf = Throwable::getStackTrace;
        LOG.clearLimits();
        LOG.init();
    }

    private void deprecatedOption(int i) {
        LOG.once().warn("Option {} is deprecated", i);
    }

    private void queueFull(int i) {
        LOG.atMostEvery(10, TimeUnit.SECONDS).info("Queue is full, dropping {}", i);
    }

    private List<Long> skippedCounts() {
        List<Long> result = new ArrayList<>();
        for (LogEntry entry : memory.getEntries()) {
            result.add(entry.getSkippedCount());
        }
        return result;
    }

    @Test
    public void testOnceLogsTheFirstCallOfACallSiteOnly() {
        for (int i = 0; i < 3; i++) {
            deprecatedOption(i);
        }
        now.addAndGet(TimeUnit.DAYS.toNanos(365));
        deprecatedOption(3);

        assertEquals(Collections.singletonList("Option 0 is deprecated"), memory.getMessages());
        assertEquals(Collections.singletonList(0L), skippedCounts());

        LOG.once().warn("another call site");
        assertEquals(Arrays.asList("Option 0 is deprecated", "another call site"), memory.getMessages());
    }

    @Test
    public void testAtMostEveryFollowsThePeriodAndCountsTheSkippedCalls() {
        queueFull(1);
        now.addAndGet(TimeUnit.SECONDS.toNanos(5));
        queueFull(2);
        queueFull(3);
        now.addAndGet(TimeUnit.SECONDS.toNanos(5));
        queueFull(4);
        queueFull(5);
        now.addAndGet(TimeUnit.SECONDS.toNanos(10) - 1);
        queueFull(6);
        now.addAndGet(1);
        queueFull(7);

        assertEquals(Arrays.asList("Queue is full, dropping 1", "Queue is full, dropping 4", "Queue is full, dropping 7"),
                     memory.getMessages());
        assertEquals(Arrays.asList(0L, 2L, 2L), skippedCounts(), "the count covers the calls since the previous entry");
    }

    @Test
    public void testDisabledLevelsAreNeitherLoggedNorCounted() {
        memory.close();
        Switchable logger = new Switchable();
        LOG.init(logger);

        for (int i = 0; i < 3; i++) {
            logger.enabled = i == 2;
            queueFull(i);
        }

        assertEquals(Collections.singletonList("Queue is full, dropping 2"), logger.messages);
        assertEquals(Collections.singletonList(0L), logger.skipped, "the disabled calls before it are not counted");
    }

    @Test
    public void testSuppressedCallBuildsNoMessage() {
        AtomicInteger built = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            LOG.once().info(() -> "built " + built.incrementAndGet());
        }

        assertEquals(Collections.singletonList("built 1"), memory.getMessages());
        assertEquals(1, built.get());
    }

    @Test
    public void testZeroOrNegativePeriodOrMissingUnitLimitsNothing() {
        for (int i = 0; i < 2; i++) {
            LOG.atMostEvery(0, TimeUnit.SECONDS).info("zero");
            LOG.atMostEvery(-5, TimeUnit.SECONDS).info("negative");
            LOG.atMostEvery(10, null).info("no unit");
        }

        assertEquals(Arrays.asList("zero", "negative", "no unit", "zero", "negative", "no unit"), memory.getMessages());
        assertEquals(Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L), skippedCounts());
        assertEquals("", getErrAndClear(), "no diagnostic: a loop must not flood System.err");
    }

    @Test
    public void testCallWithoutACallSiteIsNotLimited() {
        LOG.callerLocator = CallerDetection.ThrowableWalk.INSTANCE;
        CallerDetection.ThrowableWalk.stackTraceOf = t -> new StackTraceElement[0];

        for (int i = 0; i < 3; i++) {
            LOG.once().info("no call site");
        }

        assertEquals(3, memory.getEntries().size(), "without a call site there is no key to limit by");
        assertNull(memory.getEntries().get(0).getCaller());
    }

    @Test
    public void testEveryLevelAndEveryShape() {
        IllegalStateException e = new IllegalStateException("boom");
        LOG.Limited once = LOG.once();

        once.trace("t {}", 1);
        once.trace(() -> "t lazy");
        once.trace(e, "t {} failed", 2);
        once.trace(e, () -> "t lazy failed");
        once.debug("d {}", 1);
        once.debug(() -> "d lazy");
        once.debug(e, "d {} failed", 2);
        once.debug(e, () -> "d lazy failed");
        once.info("i {}", 1);
        once.info(() -> "i lazy");
        once.info(e, "i {} failed", 2);
        once.info(e, () -> "i lazy failed");
        once.warn("w {}", 1);
        once.warn(() -> "w lazy");
        once.warn(e, "w {} failed", 2);
        once.warn(e, () -> "w lazy failed");
        once.error("e {}", 1);
        once.error(() -> "e lazy");
        once.error(e, "e {} failed", 2);
        once.error(e, () -> "e lazy failed");

        List<LogEntry> entries = memory.getEntries();
        assertEquals(20, entries.size());
        LogEntry.LogEntryLevel[] levels = {TRACE, DEBUG, INFO, WARN, ERROR};
        String[] prefixes = {"t", "d", "i", "w", "e"};
        for (int level = 0; level < levels.length; level++) {
            String p = prefixes[level];
            assertEntry(entries.get(level * 4), levels[level], p + " 1", null);
            assertEntry(entries.get(level * 4 + 1), levels[level], p + " lazy", null);
            assertEntry(entries.get(level * 4 + 2), levels[level], p + " 2 failed", e);
            assertEntry(entries.get(level * 4 + 3), levels[level], p + " lazy failed", e);
        }
        assertEquals(RateLimitTest.class, entries.get(0).getSourceClass());
        assertEquals("testEveryLevelAndEveryShape", entries.get(0).getCaller().getMethodName());
    }

    private static void assertEntry(LogEntry entry, LogEntry.LogEntryLevel level, String message, Throwable throwable) {
        assertEquals(level, entry.getLevel(), message);
        assertEquals(message, entry.getFormattedMessage());
        assertSame(throwable, entry.getThrowable(), message);
    }

    @Test
    public void testConcurrentCallsAreLoggedOrCountedExactlyOnce() throws InterruptedException {
        // a clock that moves on every reading, and a period of a few readings: the threads keep racing for the emission
        AtomicLong clock = new AtomicLong();
        LOG.nanoClock = clock::incrementAndGet;
        int threads = 8;
        int calls = 2000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < calls; i++) {
                    contended();
                }
            });
            worker.start();
            workers.add(worker);
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
        // after a long pause, one more call carries the calls skipped since the last entry
        clock.addAndGet(1_000_000);
        contended();

        long logged = memory.getEntries().size();
        long skipped = 0;
        for (LogEntry entry : memory.getEntries()) {
            skipped += entry.getSkippedCount();
        }
        assertEquals(threads * calls + 1, logged + skipped, "every call is either logged or counted, exactly once");
        assertTrue(logged > 1 && skipped > 0, "the race went through both paths: logged " + logged + ", skipped " + skipped);
    }

    private void contended() {
        LOG.atMostEvery(5, TimeUnit.NANOSECONDS).info("contended");
    }

    /**
     * Records entries while enabled, logs nothing while disabled.
     */
    private static class Switchable implements ILogger {

        volatile boolean enabled;
        final List<String> messages = new ArrayList<>();
        final List<Long> skipped = new ArrayList<>();

        @Override
        public void log(LogEntry entry) {
            messages.add(entry.getFormattedMessage());
            skipped.add(entry.getSkippedCount());
        }

        @Override
        public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
            return enabled;
        }
    }
}
