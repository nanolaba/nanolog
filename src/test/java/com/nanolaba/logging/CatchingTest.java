package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.catching(...)} and {@code LOG.installUncaughtExceptionHandler()}.
 */
class CatchingTest extends AbstractLoggerTest {

    private Thread.UncaughtExceptionHandler original;
    private LongSupplier originalClock;

    @BeforeEach
    public void setUp() {
        original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(null);
        originalClock = LOG.nanoClock;
        LOG.nanoClock = () -> 0L;
        Summary.shutdownHooks = thread -> {
        };
    }

    @AfterEach
    public void tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(original);
        LOG.nanoClock = originalClock;
        Summary.shutdownHooks = Runtime.getRuntime()::addShutdownHook;
        Breadcrumbs.threshold = -1;
        Breadcrumbs.drainThread();
        Contexts.CURRENT.remove();
        Thread.interrupted();
        LOG.init();
    }

    // CATCHING

    @Test
    public void testAnExceptionIsLoggedAndSwallowed() {
        IllegalStateException boom = new IllegalStateException("boom");
        try (MemoryLogger memory = MemoryLogger.install()) {
            int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
            LOG.catching(() -> {
                throw boom;
            });

            assertEquals(1, memory.getEntries().size());
            LogEntry entry = memory.getEntries().get(0);
            assertEquals(ERROR, entry.getLevel());
            assertSame(boom, entry.getThrowable());
            assertEquals("Caught and ignored", entry.getFormattedMessage());
            assertEquals(CatchingTest.class, entry.getSourceClass(), "the code that called catching");
            assertEquals("testAnExceptionIsLoggedAndSwallowed", entry.getCaller().getMethodName());
            assertEquals(line, entry.getCaller().getLineNumber());
        }
    }

    @Test
    public void testNothingIsLoggedWithoutAnException() {
        AtomicBoolean ran = new AtomicBoolean();
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.catching(() -> ran.set(true));
            assertTrue(ran.get());
            assertEquals(42, (int) LOG.catching(() -> 42, -1));
            assertEquals(0, memory.getEntries().size());
        }
    }

    @Test
    public void testCheckedExceptionsAreCaught() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.catching(() -> {
                throw new IOException("disk full");
            });
            assertEquals("disk full", memory.getEntries().get(0).getThrowable().getMessage());
        }
    }

    @Test
    public void testAnErrorIsNotCaught() {
        AssertionError failed = new AssertionError("failed");
        try (MemoryLogger memory = MemoryLogger.install()) {
            assertSame(failed, assertThrows(AssertionError.class, () -> LOG.catching(() -> {
                throw failed;
            })));
            assertSame(failed, assertThrows(AssertionError.class, () -> LOG.catching(() -> {
                throw failed;
            }, "default")));
            assertEquals(0, memory.getEntries().size());
        }
    }

    @Test
    public void testADefaultValueOnFailure() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
            int parsed = LOG.catching(() -> Integer.parseInt("x"), -1);

            assertEquals(-1, parsed);
            LogEntry entry = memory.getEntries().get(0);
            assertEquals("Caught, returning -1", entry.getFormattedMessage());
            assertTrue(entry.getThrowable() instanceof NumberFormatException);
            assertEquals(line, entry.getCaller().getLineNumber());

            assertNull(LOG.catching(() -> {
                throw new IOException();
            }, null));
            assertEquals("Caught, returning null", memory.getEntries().get(1).getFormattedMessage());
        }
    }

    @Test
    public void testAnInterruptionIsKept() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.catching(() -> {
                throw new InterruptedException("stop");
            });
            assertTrue(Thread.interrupted(), "the interrupt flag is set again");
            assertNull(LOG.catching(() -> {
                throw new InterruptedException("stop");
            }, null));
            assertTrue(Thread.interrupted());
            LOG.catching(() -> {
                throw new IOException();
            });
            assertFalse(Thread.interrupted());
            assertEquals(3, memory.getEntries().size());
        }
    }

    @Test
    public void testTheLevelsApply() {
        LOG.init(new ConsoleLogger().setLevel(CatchingTest.class.getName(), "OFF"));
        LOG.catching(() -> {
            throw new IllegalStateException();
        });
        assertEquals(-2, (int) LOG.catching(() -> {
            throw new IllegalStateException();
        }, -2));
        assertEquals("", getErrAndClear());
        assertEquals("", getOutAndClear());
    }

    @Test
    public void testACaughtExceptionFailsTheUnit() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Unit u = LOG.unit("cleanup")) {
                LOG.catching(() -> {
                    throw new IllegalStateException();
                });
            }
            assertEquals("error", memory.getEntries().get(1).getFields().get("outcome"));
        }
    }

    // THE UNCAUGHT EXCEPTION HANDLER

    private static void runAndDie(Runnable body) throws InterruptedException {
        Thread thread = new Thread(body, "worker-1");
        thread.start();
        thread.join();
    }

    @Test
    public void testAnUncaughtExceptionIsLoggedThenPassedOn() throws Exception {
        List<String> previous = new ArrayList<>();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> previous.add(t.getName() + ": " + e.getMessage()));
        LOG.installUncaughtExceptionHandler();
        Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
        LOG.installUncaughtExceptionHandler();
        assertSame(installed, Thread.getDefaultUncaughtExceptionHandler(), "installing again changes nothing");

        try (MemoryLogger memory = MemoryLogger.install()) {
            int line = new Throwable().getStackTrace()[0].getLineNumber() + 2;
            runAndDie(() -> {
                throw new IllegalStateException("boom");
            });

            assertEquals(1, memory.getEntries().size());
            LogEntry entry = memory.getEntries().get(0);
            assertEquals(ERROR, entry.getLevel());
            assertEquals("Uncaught exception in thread worker-1", entry.getFormattedMessage());
            assertEquals("boom", entry.getThrowable().getMessage());
            assertEquals("worker-1", entry.getThreadName());
            assertEquals(CatchingTest.class, entry.getSourceClass(), "the class that threw, not the dispatch code of the JVM");
            assertEquals(line, entry.getCaller().getLineNumber());
        }
        assertEquals(Arrays.asList("worker-1: boom"), previous, "the previous handler still sees the failure");
    }

    @Test
    public void testWithoutAPreviousHandlerOnlyTheLogHasIt() throws Exception {
        Thread.setDefaultUncaughtExceptionHandler(null);
        LOG.installUncaughtExceptionHandler();
        try (MemoryLogger memory = MemoryLogger.install()) {
            runAndDie(() -> {
                throw new IllegalStateException("quiet");
            });
            assertEquals(1, memory.getEntries().size());
        }
        assertFalse(getErrAndClear().contains("Exception in thread"), "the JVM prints nothing of its own");
    }

    @Test
    public void testTheSourceIsTheFirstFrameOutsideTheJdk() throws Exception {
        LOG.installUncaughtExceptionHandler();
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        try (MemoryLogger memory = MemoryLogger.install()) {
            int line = new Throwable().getStackTrace()[0].getLineNumber() + 2;
            runAndDie(() -> {
                Integer.parseInt("not a number");
            });
            LogEntry parsed = memory.getEntries().get(0);
            assertEquals(CatchingTest.class, parsed.getSourceClass());
            assertEquals(line, parsed.getCaller().getLineNumber());

            Throwable onlyJdk = new IllegalStateException("jdk");
            onlyJdk.setStackTrace(new StackTraceElement[]{new StackTraceElement("java.lang.Thread", "run", "Thread.java", 1)});
            handler.uncaughtException(Thread.currentThread(), onlyJdk);
            assertEquals(Thread.class, memory.getEntries().get(1).getSourceClass(), "only the JDK: its top frame");

            Throwable unknown = new IllegalStateException("gone");
            StackTraceElement gone = new StackTraceElement("com.nowhere.Gone", "run", "Gone.java", 3);
            unknown.setStackTrace(new StackTraceElement[]{gone});
            handler.uncaughtException(Thread.currentThread(), unknown);
            assertNull(memory.getEntries().get(2).getSourceClass(), "a class nobody can load");
            assertSame(gone, memory.getEntries().get(2).getCaller());

            Throwable none = new IllegalStateException("none");
            none.setStackTrace(new StackTraceElement[0]);
            handler.uncaughtException(Thread.currentThread(), none);
            assertNull(memory.getEntries().get(3).getSourceClass());
            assertNull(memory.getEntries().get(3).getCaller());

            Throwable nested = new IllegalStateException("nested");
            nested.setStackTrace(new StackTraceElement[]{
                    new StackTraceElement("javax.swing.JPanel", "paint", "JPanel.java", 1),
                    new StackTraceElement("jdk.internal.Foo", "x", "Foo.java", 1),
                    new StackTraceElement("sun.misc.Bar", "x", "Bar.java", 1),
                    new StackTraceElement("com.sun.Baz", "x", "Baz.java", 1),
                    new StackTraceElement(InfoObject.class.getName(), "call", "InfoObject.java", 9)});
            handler.uncaughtException(Thread.currentThread(), nested);
            assertEquals(InfoObject.class, memory.getEntries().get(4).getSourceClass());
            assertEquals(9, memory.getEntries().get(4).getCaller().getLineNumber());

            Throwable anonymous = new IllegalStateException("anonymous");
            anonymous.setStackTrace(new StackTraceElement[]{new StackTraceElement(Inner.class.getName(), "x", "CatchingTest.java", 5)});
            handler.uncaughtException(Thread.currentThread(), anonymous);
            assertEquals(CatchingTest.class, memory.getEntries().get(5).getSourceClass(), "a nested class counts as its top-level class");
        }
    }

    private static final class Inner {
    }

    @Test
    public void testTheDyingThreadWritesItsUnitsAndBreadcrumbs() throws Exception {
        LOG.installUncaughtExceptionHandler();
        Breadcrumbs.threshold = DEBUG.ordinal();
        try (MemoryLogger memory = MemoryLogger.install()) {
            runAndDie(() -> {
                LOG.debug("before the unit");
                LOG.unit("job");
                LOG.debug("inside the job");
                LOG.unit("step");
                throw new IllegalStateException("died");
            });
            List<String> messages = new ArrayList<>();
            for (LogEntry entry : memory.getEntries()) {
                messages.add(entry.getFormattedMessage());
            }
            assertEquals(Arrays.asList("--- 1 earlier DEBUG entry ---", "before the unit", "Uncaught exception in thread worker-1",
                                       "step interrupted 0 ms", "job interrupted 0 ms", "--- 1 earlier DEBUG entry ---", "inside the job"),
                         messages, "the breadcrumbs of the thread, the error, then the open units, the innermost first");
            assertEquals(1, memory.getEntries().get(3).getFields().get("error"), "the error counts in the units around it");
            assertEquals(1, memory.getEntries().get(4).getFields().get("error"));
            assertTrue(Units.OPEN.isEmpty());
        }
        Breadcrumbs.threshold = -1;
        try (MemoryLogger memory = MemoryLogger.install()) {
            runAndDie(() -> {
                LOG.debug("plain");
                throw new IllegalStateException("outside units");
            });
            assertEquals(2, memory.getEntries().size());
        }
    }

    @Test
    public void testTheHandlerNeverThrows() throws Exception {
        List<Throwable> previous = new ArrayList<>();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> previous.add(e));
        LOG.installUncaughtExceptionHandler();
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
                throw new IllegalStateException("broken logger");
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                throw new IllegalStateException("broken check");
            }
        });
        Throwable failure = new IllegalStateException("x");
        Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), failure);
        assertEquals(Arrays.asList(failure), previous);
        Throwable broken = new IllegalStateException("broken trace") {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new UnsupportedOperationException();
            }
        };
        Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), broken);
        Throwable nullTrace = new IllegalStateException("null trace") {
            @Override
            public StackTraceElement[] getStackTrace() {
                return null;
            }
        };
        Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), nullTrace);
        assertEquals(Arrays.asList(failure, broken, nullTrace), previous);
        assertNull(Uncaught.origin(nullTrace).sourceClass);
        assertNull(Uncaught.origin(broken).frame);
        getErrAndClear();
        LOG.clearReportedLoggerFailures();
    }
}
