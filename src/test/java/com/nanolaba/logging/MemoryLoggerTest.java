package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.TRACE;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryLoggerTest extends AbstractLoggerTest {

    @Test
    public void testCapturesEveryLevelInOrder() {
        MemoryLogger memory = new MemoryLogger();
        LOG.init(memory);

        LOG.trace("t");
        LOG.debug("d {}", 1);
        LOG.info("i");
        LOG.warn("w");
        LOG.error("e");

        List<LogEntry> entries = memory.getEntries();
        assertEquals(5, entries.size());
        assertEquals(Arrays.asList(TRACE, DEBUG, INFO, WARN, ERROR),
                     Arrays.asList(entries.get(0).getLevel(), entries.get(1).getLevel(), entries.get(2).getLevel(),
                                   entries.get(3).getLevel(), entries.get(4).getLevel()));
        assertEquals(Arrays.asList("t", "d 1", "i", "w", "e"), memory.getMessages());
        assertEquals(MemoryLoggerTest.class, entries.get(0).getSourceClass());
        assertEquals("", getOutAndClear() + getErrAndClear(), "a memory logger prints nothing");
    }

    @Test
    public void testEveryLevelIsEnabled() {
        MemoryLogger memory = new MemoryLogger();
        for (LogEntry.LogEntryLevel level : LogEntry.LogEntryLevel.values()) {
            assertTrue(memory.isEnabled(level, InfoObject.class));
            assertTrue(memory.isEnabled(level, null));
        }
    }

    @Test
    public void testFilteringAndContains() {
        MemoryLogger memory = new MemoryLogger();
        LOG.init(memory);

        LOG.info("Charge of {} accepted", 42);
        LOG.warn("Charge of {} failed, retrying", 42);
        LOG.warn("Disk space is low");

        assertEquals(Arrays.asList("Charge of 42 failed, retrying", "Disk space is low"),
                     messagesOf(memory.getEntries(WARN)));
        assertEquals(Collections.emptyList(), memory.getEntries(ERROR));

        assertTrue(memory.contains(WARN, "retrying"));
        assertTrue(memory.contains(INFO, "accepted"));
        assertFalse(memory.contains(INFO, "retrying"), "the level must match as well");
        assertFalse(memory.contains(WARN, "nothing like this"));
    }

    @Test
    public void testSnapshotsAndClear() {
        MemoryLogger memory = new MemoryLogger();
        LOG.init(memory);

        LOG.info("first");
        List<LogEntry> snapshot = memory.getEntries();
        LOG.info("second");

        assertEquals(1, snapshot.size(), "a snapshot does not change when more entries arrive");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));
        assertThrows(UnsupportedOperationException.class, () -> memory.getMessages().add(null));
        assertThrows(UnsupportedOperationException.class, () -> memory.getEntries(INFO).add(null));

        memory.clear();
        assertEquals(Collections.emptyList(), memory.getEntries());

        LOG.info("third");
        assertEquals(Collections.singletonList("third"), memory.getMessages());
    }

    @Test
    public void testCapacityKeepsTheNewestEntries() {
        MemoryLogger memory = new MemoryLogger(2);
        LOG.init(memory);

        LOG.info("1");
        LOG.info("2");
        assertEquals(Arrays.asList("1", "2"), memory.getMessages());

        LOG.info("3");
        assertEquals(Arrays.asList("2", "3"), memory.getMessages());

        assertEquals(1, new MemoryLogger(1).getCapacity());
        assertEquals(Integer.MAX_VALUE, new MemoryLogger().getCapacity());
        assertThrows(IllegalArgumentException.class, () -> new MemoryLogger(0));
    }

    @Test
    public void testConcurrentWrites() throws InterruptedException {
        MemoryLogger memory = new MemoryLogger();
        LOG.init(memory);

        int threads = 8;
        int perThread = 1000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < perThread; j++) {
                    LOG.info("entry {}", j);
                }
            });
            workers.add(worker);
            worker.start();
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }

        assertEquals(threads * perThread, memory.getEntries().size());
    }

    @Test
    public void testConcurrentWritesWithCapacity() throws InterruptedException {
        MemoryLogger memory = new MemoryLogger(100);
        LOG.init(memory);

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread worker = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    LOG.info("entry {}", j);
                }
            });
            workers.add(worker);
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        assertEquals(100, memory.getEntries().size());
    }

    @Test
    public void testInstallRestoresThePreviousLogger() {
        ILogger previous = entry -> {
        };
        LOG.init(previous);

        try (MemoryLogger memory = MemoryLogger.install()) {
            assertSame(memory, LOG.getLogger());
            LOG.info("captured");
            assertEquals(Collections.singletonList("captured"), memory.getMessages());
        }

        assertSame(previous, LOG.getLogger(), "close() must put back the logger that was installed before, not the default one");
    }

    @Test
    public void testInstallRestoresThePreviousLoggerWhenTheBodyThrows() {
        ILogger previous = entry -> {
        };
        LOG.init(previous);

        assertThrows(IllegalStateException.class, () -> {
            try (MemoryLogger ignored = MemoryLogger.install()) {
                throw new IllegalStateException("the test body failed");
            }
        });

        assertSame(previous, LOG.getLogger());
    }

    @Test
    public void testCloseWithoutInstallChangesNothing() {
        ILogger current = entry -> {
        };
        LOG.init(current);

        new MemoryLogger().close();

        assertSame(current, LOG.getLogger());
    }

    @Test
    public void testInstallForwardingCapturesAndForwards() {
        ConsoleLogger console = new ConsoleLogger().setShowDate(false).setDebugEnabled(false);
        LOG.init(console);

        try (MemoryLogger memory = MemoryLogger.installForwarding()) {
            LOG.info(InfoObject.class, "both");
            LOG.debug(InfoObject.class, "only captured");

            assertEquals(Arrays.asList("both", "only captured"), memory.getMessages(),
                         "the memory logger captures every level, whatever the previous logger has enabled");
            assertEquals("INFO [InfoObject] both" + System.lineSeparator(), getOutAndClear(),
                         "the previous logger keeps receiving the entries it has enabled");
        }

        assertSame(console, LOG.getLogger());
    }

    private static final String N = System.lineSeparator();

    @Test
    public void testAssertNoneAtOrAbove() {
        MemoryLogger memory = new MemoryLogger();
        memory.log(new LogEntry(DEBUG, InfoObject.class, null, "loading"));
        memory.log(new LogEntry(INFO, InfoObject.class, null, "loaded {} items", 3));
        memory.log(new LogEntry(null, InfoObject.class, null, "no level"));

        memory.assertNoneAtOrAbove(WARN);
        memory.log(new LogEntry(WARN, InfoObject.class, null, "retrying"));
        memory.assertNoneAtOrAbove(ERROR);

        AssertionError error = assertThrows(AssertionError.class, () -> memory.assertNoneAtOrAbove(WARN));
        assertEquals("Expected no entry at WARN or above, but 1 was logged. Captured entries:" + N +
                     "DEBUG [InfoObject] loading" + N +
                     "INFO [InfoObject] loaded 3 items" + N +
                     "[InfoObject] no level" + N +
                     "WARN [InfoObject] retrying",
                     error.getMessage());

        memory.log(new LogEntry(ERROR, InfoObject.class, null, "failed"));
        assertTrue(assertThrows(AssertionError.class, () -> memory.assertNoneAtOrAbove(TRACE)).getMessage()
                           .startsWith("Expected no entry at TRACE or above, but 4 were logged."));
    }

    @Test
    public void testFailureMessageShowsStackTracesAndCallSites() {
        MemoryLogger memory = new MemoryLogger();
        LOG.init(memory);

        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.error("failed {}", 42, new IllegalStateException("boom"));

        String message = assertThrows(AssertionError.class, () -> memory.assertNoneAtOrAbove(ERROR)).getMessage();
        assertTrue(message.startsWith("Expected no entry at ERROR or above, but 1 was logged. Captured entries:" + N +
                                      "ERROR [MemoryLoggerTest.testFailureMessageShowsStackTracesAndCallSites(MemoryLoggerTest.java:" + line +
                                      ")] failed 42 java.lang.IllegalStateException: boom" + N +
                                      "\tat com.nanolaba.logging.MemoryLoggerTest.testFailureMessageShowsStackTracesAndCallSites"), message);
    }

    @Test
    public void testAssertLogged() {
        MemoryLogger memory = new MemoryLogger();
        AssertionError empty = assertThrows(AssertionError.class, () -> memory.assertLogged(WARN, "retrying"));
        assertEquals("Expected a WARN entry containing \"retrying\". Captured entries: none", empty.getMessage());

        memory.log(new LogEntry(INFO, InfoObject.class, null, "Charge of {} failed, retrying", 42));
        memory.assertLogged(INFO, "failed, retry");

        AssertionError otherLevel = assertThrows(AssertionError.class, () -> memory.assertLogged(WARN, "retrying"));
        assertEquals("Expected a WARN entry containing \"retrying\". Captured entries:" + N +
                     "INFO [InfoObject] Charge of 42 failed, retrying",
                     otherLevel.getMessage());
    }

    @Test
    public void testAssertTemplateLogged() {
        MemoryLogger memory = new MemoryLogger();
        memory.log(new LogEntry(WARN, InfoObject.class, null, "Retry {} of {}", 1, 3));
        memory.log(new LogEntry(WARN, InfoObject.class, new IllegalStateException(), null));

        memory.assertTemplateLogged(WARN, "Retry {} of {}");

        AssertionError formatted = assertThrows(AssertionError.class, () -> memory.assertTemplateLogged(WARN, "Retry 1 of 3"));
        assertTrue(formatted.getMessage().startsWith("Expected a WARN entry with the template \"Retry 1 of 3\". Captured entries:" + N +
                                                     "WARN [InfoObject] Retry 1 of 3" + N),
                   "the template is the raw message, not the formatted one: " + formatted.getMessage());
        assertThrows(AssertionError.class, () -> memory.assertTemplateLogged(ERROR, "Retry {} of {}"), "the level must match as well");
    }

    @Test
    public void testEntriesOfAClass() {
        MemoryLogger memory = new MemoryLogger();
        LOG.init(memory);

        LOG.info("from the test");
        LOG.info(InfoObject.class, "explicit");
        LOG.warn(InfoObject.class, "explicit again");
        LOG.info((Class) null, "no class");

        assertEquals(Arrays.asList("explicit", "explicit again"), messagesOf(memory.getEntries(InfoObject.class)));
        assertEquals(Collections.singletonList("from the test"), messagesOf(memory.getEntries(MemoryLoggerTest.class)));
        assertEquals(Collections.singletonList("no class"), messagesOf(memory.getEntries((Class) null)));
    }

    private static List<String> messagesOf(List<LogEntry> entries) {
        List<String> result = new ArrayList<>();
        for (LogEntry entry : entries) {
            result.add(entry.getFormattedMessage());
        }
        return result;
    }
}
