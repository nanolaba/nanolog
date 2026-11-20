package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import com.nanolaba.logging.wrap.Audit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.spi.LocationAwareLogger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Slf4jLoggerTest extends AbstractLoggerTest {

    @Test
    public void testInfoLevel() {
        Slf4jLogger logger = createLogger();

        LOG.init(logger);

        LOG.info(InfoObject.class, "");
        assertEquals("[main] INFO com.nanolaba.logging.util.InfoObject - " + System.lineSeparator(), getErrAndClear());

        LOG.info(InfoObject.class, "some message {} next {}", "abc", "123");
        assertEquals("[main] INFO com.nanolaba.logging.util.InfoObject - some message abc next 123" + System.lineSeparator(), getErrAndClear());

        // an entry without a message must not log the literal word "null"
        LOG.info(InfoObject.class, new Throwable("test"));
        assertTrue(getErrAndClear().startsWith("[main] INFO com.nanolaba.logging.util.InfoObject - " + System.lineSeparator() +
                                               "java.lang.Throwable: test" + System.lineSeparator() +
                                               "\tat com.nanolaba.logging.Slf4jLoggerTest.testInfoLevel"));


        for (LogEntry.LogEntryLevel level : LogEntry.LogEntryLevel.values()) {
            LOG.log(level, InfoObject.class, null, () -> "some message {} next {}", "abc", "123");
            assertEquals("[main] " + level + " com.nanolaba.logging.util.InfoObject - some message abc next 123" + System.lineSeparator(), getErrAndClear());
        }
    }

    @Test
    public void testTrailingThrowableIsPassedToSlf4j() {
        LOG.init(createLogger());

        LOG.error(InfoObject.class, "Failed to load {}", "order", new Throwable("test"));
        assertTrue(getErrAndClear().startsWith("[main] ERROR com.nanolaba.logging.util.InfoObject - Failed to load order" + System.lineSeparator() +
                                               "java.lang.Throwable: test" + System.lineSeparator() +
                                               "\tat com.nanolaba.logging.Slf4jLoggerTest.testTrailingThrowableIsPassedToSlf4j"));
    }

    @Test
    public void testNullSourceClass() {
        LOG.init(createLogger());

        LOG.info((Class) null, "no source class");
        assertEquals("[main] INFO ROOT - no source class" + System.lineSeparator(), getErrAndClear());
    }

    @Test
    public void testLocationAwareBackendGetsTheCallerBoundary() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        LOG.init(new Slf4jLogger(recorder::loggerFor));

        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.info("auto-detected");

        LocationAwareRecorder.Call call = recorder.calls.get(0);
        assertNull(call.marker);
        assertEquals(LOG.class.getName(), call.fqcn);
        assertEquals(LocationAwareLogger.INFO_INT, call.level);
        assertEquals("auto-detected", call.message);
        assertNull(call.args, "the message is already formatted, the backend must not format it again");
        assertNull(call.throwable);
        // what a backend computes from the boundary is the real call site, not Slf4jLogger or LOG
        assertEquals(Slf4jLoggerTest.class.getName(), call.location.getClassName());
        assertEquals("testLocationAwareBackendGetsTheCallerBoundary", call.location.getMethodName());
        assertEquals(line, call.location.getLineNumber());
    }

    @Test
    public void testRateLimitedCallTellsTheBackendItsOwnBoundary() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        LOG.init(new Slf4jLogger(recorder::loggerFor));
        LOG.clearLimits();

        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.once().info("limited");

        LocationAwareRecorder.Call call = recorder.calls.get(0);
        // the call passes through LOG.Limited, so a boundary of LOG alone would point the backend at that class
        assertEquals(LOG.Limited.class.getName(), call.fqcn);
        assertEquals(Slf4jLoggerTest.class.getName(), call.location.getClassName());
        assertEquals("testRateLimitedCallTellsTheBackendItsOwnBoundary", call.location.getMethodName());
        assertEquals(line, call.location.getLineNumber());
        LOG.clearLimits();
    }

    @Test
    public void testASkippedHelperIsTheBoundary() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        System.setProperty(DefaultLoggerCreator.PROPERTY_CALLER_SKIP, Audit.class.getName());
        try {
            LOG.init();
            LOG.init(new Slf4jLogger(recorder::loggerFor));

            int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
            Audit.record("through a helper");

            LocationAwareRecorder.Call call = recorder.calls.get(0);
            assertEquals(Audit.class.getName(), call.fqcn);
            // the backend reports the code that called the helper, not the helper
            assertEquals(Slf4jLoggerTest.class.getName(), call.location.getClassName());
            assertEquals("testASkippedHelperIsTheBoundary", call.location.getMethodName());
            assertEquals(line, call.location.getLineNumber());
        } finally {
            System.clearProperty(DefaultLoggerCreator.PROPERTY_CALLER_SKIP);
            LOG.init();
        }
    }

    @Test
    public void testATagIsAMarkerForALocationAwareBackend() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        LOG.init(new Slf4jLogger(recorder::loggerFor));

        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.tag("audit").warn("role changed");

        LocationAwareRecorder.Call call = recorder.calls.get(0);
        assertEquals("audit", ((Marker) call.marker).getName());
        assertEquals(LOG.Tagged.class.getName(), call.fqcn);
        assertEquals("testATagIsAMarkerForALocationAwareBackend", call.location.getMethodName());
        assertEquals(line, call.location.getLineNumber());
    }

    @Test
    public void testInAgentModeADbgEntryThatTheBackendDropsIsPrintedOnTheConsole() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        recorder.debug = false;
        Slf4jLogger logger = new Slf4jLogger(recorder::loggerFor);
        StackTraceElement frame = new StackTraceElement(InfoObject.class.getName(), "checkout", "InfoObject.java", 9);
        LogEntry dbg = LogEntry.createDbg(InfoObject.class, frame, "cart.size() = 2 (Integer)", 0, LogEntry.LOG_BOUNDARY);
        getOutAndClear();

        logger.log(dbg);
        assertEquals(1, recorder.calls.size(), "outside agent mode the backend decides");

        Dbg.ignoreLevels = true;
        try {
            logger.log(dbg);
            assertEquals("DBG [InfoObject.checkout(InfoObject.java:9)] cart.size() = 2 (Integer)" + System.lineSeparator(),
                         getOutAndClear());
            assertEquals(1, recorder.calls.size(), "not passed to a backend that would drop it (#105)");
            logger.log(LogEntry.create(LogEntry.LogEntryLevel.DEBUG, InfoObject.class, frame, null, "plain", new Object[0], 0));
            assertEquals(2, recorder.calls.size(), "only an entry of LOG.dbg");
            recorder.debug = true;
            logger.log(dbg);
            assertEquals(3, recorder.calls.size(), "a backend with DEBUG on prints it, with its call site");
            assertEquals("", getOutAndClear());
        } finally {
            Dbg.ignoreLevels = false;
        }
    }

    /**
     * A plain SLF4J logger, not location-aware: records the calls of its level methods.
     */
    private static final class PlainRecorder implements InvocationHandler {

        final List<String> calls = new ArrayList<>();

        Logger loggerFor(String name) {
            return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class[]{Logger.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().startsWith("is")) {
                return true;
            }
            if (args[0] instanceof Marker) {
                calls.add(method.getName() + " " + ((Marker) args[0]).getName() + " " + args[1] + " " + args[2]);
            } else {
                calls.add(method.getName() + " " + args[0] + " " + args[1]);
            }
            return null;
        }
    }

    @Test
    public void testATagIsAMarkerForAPlainBackend() {
        PlainRecorder recorder = new PlainRecorder();
        LOG.init(new Slf4jLogger(recorder::loggerFor));
        IllegalStateException e = new IllegalStateException("boom");

        LOG.Tagged audit = LOG.tag("audit");
        audit.trace("t");
        audit.debug("d");
        audit.info("i");
        audit.warn("w");
        audit.error(e, "e");
        LOG.info("plain");

        assertEquals(Arrays.asList("trace audit t null", "debug audit d null", "info audit i null", "warn audit w null",
                                   "error audit e java.lang.IllegalStateException: boom", "info plain null"), recorder.calls);
    }

    @Test
    public void testSkippedCountIsAppendedToTheMessage() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        Slf4jLogger logger = new Slf4jLogger(recorder::loggerFor);

        logger.log(LogEntry.create(LogEntry.LogEntryLevel.WARN, InfoObject.class, null, null, "Queue is full", new Object[0], 241));
        logger.log(LogEntry.create(LogEntry.LogEntryLevel.WARN, InfoObject.class, null, new IllegalStateException(), null, new Object[0], 2));
        logger.log(LogEntry.create(LogEntry.LogEntryLevel.WARN, InfoObject.class, null, null, "Queue is full", new Object[0], 0));

        assertEquals("Queue is full [skipped 241]", recorder.calls.get(0).message);
        assertEquals("[skipped 2]", recorder.calls.get(1).message, "an entry without a message gets the count alone");
        assertEquals("Queue is full", recorder.calls.get(2).message);
        assertEquals(LOG.class.getName(), recorder.calls.get(2).fqcn, "an entry that did not come through LOG.Limited keeps LOG");
    }

    @Test
    public void testLocationAwareBackendLevelsAndThrowables() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        LOG.init(new Slf4jLogger(recorder::loggerFor));
        IllegalStateException e = new IllegalStateException("boom");

        LOG.trace(InfoObject.class, "t");
        LOG.debug(InfoObject.class, "d");
        LOG.info(InfoObject.class, "i");
        LOG.warn(InfoObject.class, "w");
        LOG.error(InfoObject.class, "e {}", 1, e);
        LOG.info(InfoObject.class, e);

        assertEquals(Arrays.asList(LocationAwareLogger.TRACE_INT, LocationAwareLogger.DEBUG_INT, LocationAwareLogger.INFO_INT,
                                   LocationAwareLogger.WARN_INT, LocationAwareLogger.ERROR_INT, LocationAwareLogger.INFO_INT),
                     recorder.levels());
        assertEquals("e 1", recorder.calls.get(4).message);
        assertSame(e, recorder.calls.get(4).throwable);
        assertEquals("", recorder.calls.get(5).message, "an entry without a message is passed with an empty one");
        assertSame(e, recorder.calls.get(5).throwable);
        // an explicit class: the boundary still leads past LOG to this test
        assertEquals(Slf4jLoggerTest.class.getName(), recorder.calls.get(0).location.getClassName());
    }

    @Test
    public void testLoggersAreCachedPerClassAndRootIsUsedWithoutClass() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        LOG.init(new Slf4jLogger(recorder::loggerFor));

        LOG.info(InfoObject.class, "one");
        LOG.info(InfoObject.class, "two");
        LOG.info((Class) null, "three");

        assertEquals(Arrays.asList("com.nanolaba.logging.util.InfoObject", "ROOT", "ROOT"), recorder.names);
    }

    @Test
    public void testTheLinesOfTimedAndOfAUnitWithABodyHaveTheirCallSite() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        LOG.init(new Slf4jLogger(recorder::loggerFor));

        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.timed("runnable", Slf4jLoggerTest::work);
        LOG.timed("supplier", (Supplier<Integer>) Slf4jLoggerTest::one);
        LOG.unit("runnable", Slf4jLoggerTest::work);
        LOG.unit("supplier", (Supplier<Integer>) Slf4jLoggerTest::one);

        // #102: the line is written after the body, and only frames of LOG are left between the call and the logger
        assertEquals(4, recorder.calls.size());
        for (int i = 0; i < 4; i++) {
            LocationAwareRecorder.Call call = recorder.calls.get(i);
            assertEquals(LOG.class.getName(), call.fqcn, call.message);
            assertEquals(Slf4jLoggerTest.class.getName(), call.location.getClassName(), call.message);
            assertEquals("testTheLinesOfTimedAndOfAUnitWithABodyHaveTheirCallSite", call.location.getMethodName(), call.message);
            assertEquals(line + i, call.location.getLineNumber(), call.message);
        }
    }

    @Test
    public void testTheLinesOfTimedAndOfAUnitWhoseBodyThrowsHaveTheirCallSite() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        LOG.init(new Slf4jLogger(recorder::loggerFor));

        List<Throwable> thrown = new ArrayList<>();
        thrown.add(assertThrows(IllegalStateException.class, () -> LOG.timed("runnable", Slf4jLoggerTest::fail)));
        thrown.add(assertThrows(IllegalStateException.class, () -> LOG.timed("supplier", (Supplier<Integer>) Slf4jLoggerTest::failWithValue)));
        thrown.add(assertThrows(IllegalStateException.class, () -> LOG.unit("runnable", Slf4jLoggerTest::fail)));
        thrown.add(assertThrows(IllegalStateException.class, () -> LOG.unit("supplier", (Supplier<Integer>) Slf4jLoggerTest::failWithValue)));

        assertEquals(4, recorder.calls.size());
        for (int i = 0; i < 4; i++) {
            LocationAwareRecorder.Call call = recorder.calls.get(i);
            assertSame(thrown.get(i), call.throwable);
            assertEquals(LOG.class.getName(), call.fqcn, call.message);
            // the frame that called LOG, as the stack trace of the exception shows it
            assertEquals(frameAfterLog(thrown.get(i)), call.location, call.message);
        }
    }

    @Test
    public void testATimerOrAUnitClosedByTheCodeHasTheCallSiteOfTheClose() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        LOG.init(new Slf4jLogger(recorder::loggerFor));
        LOG.Timer timer = LOG.time("timer");
        LOG.Unit unit = LOG.unit("unit");

        // the call of LOG.time or LOG.unit is no longer on the stack: a backend can only find the code that closes
        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        timer.close();
        unit.close();
        LOG.unit("run").run(Slf4jLoggerTest::work);

        assertEquals(Arrays.asList(Timers.Timing.BOUNDARY, Units.Scope.BOUNDARY, Units.Scope.BOUNDARY), fqcns(recorder));
        for (int i = 0; i < 3; i++) {
            LocationAwareRecorder.Call call = recorder.calls.get(i);
            assertEquals(Slf4jLoggerTest.class.getName(), call.location.getClassName(), call.message);
            assertEquals("testATimerOrAUnitClosedByTheCodeHasTheCallSiteOfTheClose", call.location.getMethodName(), call.message);
            assertEquals(line + i, call.location.getLineNumber(), call.message);
        }
    }

    @Test
    public void testTimedAndAUnitWithABodyCalledByASkippedHelperHaveTheCallSiteOfTheHelper() {
        LocationAwareRecorder recorder = new LocationAwareRecorder();
        System.setProperty(DefaultLoggerCreator.PROPERTY_CALLER_SKIP, Audit.class.getName());
        try {
            LOG.init();
            LOG.init(new Slf4jLogger(recorder::loggerFor));

            int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
            Audit.timed("timed", Slf4jLoggerTest::work);
            Audit.unit("unit", Slf4jLoggerTest::work);

            assertEquals(Arrays.asList(Audit.class.getName(), Audit.class.getName()), fqcns(recorder));
            for (int i = 0; i < 2; i++) {
                LocationAwareRecorder.Call call = recorder.calls.get(i);
                assertEquals("testTimedAndAUnitWithABodyCalledByASkippedHelperHaveTheCallSiteOfTheHelper", call.location.getMethodName());
                assertEquals(line + i, call.location.getLineNumber(), call.message);
            }
        } finally {
            System.clearProperty(DefaultLoggerCreator.PROPERTY_CALLER_SKIP);
            LOG.init();
        }
    }

    private static void work() {
        // the body of a timer or a unit
    }

    private static int one() {
        return 1;
    }

    private static void fail() {
        throw new IllegalStateException("boom");
    }

    private static int failWithValue() {
        throw new IllegalStateException("boom");
    }

    /**
     * The first frame after those of {@code LOG} in the stack trace of an exception thrown inside a body.
     */
    private static StackTraceElement frameAfterLog(Throwable thrown) {
        boolean inLog = false;
        for (StackTraceElement element : thrown.getStackTrace()) {
            if (element.getClassName().equals(LOG.class.getName())) {
                inLog = true;
            } else if (inLog) {
                return element;
            }
        }
        throw new AssertionError("no frame of LOG");
    }

    private static List<String> fqcns(LocationAwareRecorder recorder) {
        List<String> result = new ArrayList<>();
        for (LocationAwareRecorder.Call call : recorder.calls) {
            result.add(call.fqcn);
        }
        return result;
    }

    @Test
    public void testPlainBackendIsCalledThroughTheLoggerInterface() {
        List<String> calls = new ArrayList<>();
        Logger plain = (Logger) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Logger.class}, (proxy, method, args) -> {
            if (method.getName().startsWith("is")) {
                return true;
            }
            calls.add(method.getName() + ":" + args[0]);
            return null;
        });
        LOG.init(new Slf4jLogger(name -> plain));

        LOG.warn(InfoObject.class, "plain");

        assertEquals(Collections.singletonList("warn:plain"), calls);
    }

    /**
     * A {@link LocationAwareLogger} built with a proxy. On each call it resolves the call site the way Log4j 2 does:
     * the frame after the last frame of the boundary class.
     */
    private static class LocationAwareRecorder implements InvocationHandler {

        static class Call {
            Object marker;
            String fqcn;
            int level;
            String message;
            Object[] args;
            Throwable throwable;
            StackTraceElement location;
        }

        final List<Call> calls = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        final Logger logger = (Logger) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LocationAwareLogger.class}, this);
        // what isDebugEnabled answers; every other level is on
        boolean debug = true;

        Logger loggerFor(String name) {
            names.add(name);
            return logger;
        }

        List<Integer> levels() {
            List<Integer> result = new ArrayList<>();
            for (Call call : calls) {
                result.add(call.level);
            }
            return result;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().startsWith("is")) {
                return debug || !method.getName().equals("isDebugEnabled");
            }
            if (method.getName().equals("log") && args.length == 6) {
                Call call = new Call();
                call.marker = args[0];
                call.fqcn = (String) args[1];
                call.level = (Integer) args[2];
                call.message = (String) args[3];
                call.args = (Object[]) args[4];
                call.throwable = (Throwable) args[5];
                call.location = locate(call.fqcn);
                calls.add(call);
                return null;
            }
            throw new AssertionError("unexpected call " + method);
        }

        private static StackTraceElement locate(String fqcn) {
            boolean found = false;
            for (StackTraceElement element : new Throwable().getStackTrace()) {
                if (element.getClassName().equals(fqcn)) {
                    found = true;
                } else if (found) {
                    return element;
                }
            }
            return null;
        }
    }

    protected Slf4jLogger createLogger() {
        return new Slf4jLogger();
    }
}