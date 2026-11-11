package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.spi.LocationAwareLogger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
                return true;
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