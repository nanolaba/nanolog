package com.nanolaba.logging;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LOGTest {

    @Test
    public void testTraceLog() {

        Mutable<String> message = new MutableObject<>();

        LOG.init(entry -> message.setValue(entry.toString()));

        assertTrue(LOG.isTraceEnabled());

        LOG.trace(LOGTest.class, new RuntimeException("ex"));
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=null, args=[]}", message.getValue());

        LOG.trace(LOGTest.class, new RuntimeException("ex"), () -> "ex1");
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=ex1, args=[]}", message.getValue());

        LOG.trace(LOGTest.class, new RuntimeException("ex"), "ex2");
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=ex2, args=[]}", message.getValue());

        LOG.trace(LOGTest.class, () -> "ex3");
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex3, args=[]}", message.getValue());

        LOG.trace(LOGTest.class, "ex4");
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex4, args=[]}", message.getValue());

        LOG.trace(() -> "ex5");
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex5, args=[]}", message.getValue());

        LOG.trace("ex6");
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex6, args=[]}", message.getValue());

        LOG.trace(new RuntimeException("ex7"), () -> "ex7");
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex7, message=ex7, args=[]}", message.getValue());

        LOG.trace(new RuntimeException("ex8"), "ex8");
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex8, message=ex8, args=[]}", message.getValue());

        LOG.trace("ex9 {}", "asd", 123);
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex9 {}, args=[asd, 123]}", message.getValue());

        LOG.trace(LOGTest.class, "ex10 {}", "asd", 123);
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex10 {}, args=[asd, 123]}", message.getValue());

        LOG.trace(new RuntimeException("ex11"));
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex11, message=null, args=[]}", message.getValue());

        LOG.trace(new RuntimeException("ex12"), "ex12 {}", "asd", 123);
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex12, message=ex12 {}, args=[asd, 123]}", message.getValue());

        LOG.trace(LOGTest.class, new RuntimeException("ex13"), "ex13 {}", "asd", 123);
        assertEquals("LogEntry{level=TRACE, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex13, message=ex13 {}, args=[asd, 123]}", message.getValue());
    }

    @Test
    public void testDebugLog() {

        Mutable<String> message = new MutableObject<>();

        LOG.init(entry -> message.setValue(entry.toString()));

        assertTrue(LOG.isDebugEnabled());

        LOG.debug(LOGTest.class, new RuntimeException("ex"));
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=null, args=[]}", message.getValue());

        LOG.debug(LOGTest.class, new RuntimeException("ex"), () -> "ex1");
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=ex1, args=[]}", message.getValue());

        LOG.debug(LOGTest.class, new RuntimeException("ex"), "ex2");
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=ex2, args=[]}", message.getValue());

        LOG.debug(LOGTest.class, () -> "ex3");
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex3, args=[]}", message.getValue());

        LOG.debug(LOGTest.class, "ex4");
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex4, args=[]}", message.getValue());

        LOG.debug(() -> "ex5");
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex5, args=[]}", message.getValue());

        LOG.debug("ex6");
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex6, args=[]}", message.getValue());

        LOG.debug(new RuntimeException("ex7"), () -> "ex7");
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex7, message=ex7, args=[]}", message.getValue());

        LOG.debug(new RuntimeException("ex8"), "ex8");
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex8, message=ex8, args=[]}", message.getValue());

        LOG.debug("ex9 {}", "asd", 123);
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex9 {}, args=[asd, 123]}", message.getValue());

        LOG.debug(LOGTest.class, "ex10 {}", "asd", 123);
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex10 {}, args=[asd, 123]}", message.getValue());

        LOG.debug(new RuntimeException("ex11"));
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex11, message=null, args=[]}", message.getValue());

        LOG.debug(new RuntimeException("ex12"), "ex12 {}", "asd", 123);
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex12, message=ex12 {}, args=[asd, 123]}", message.getValue());

        LOG.debug(LOGTest.class, new RuntimeException("ex13"), "ex13 {}", "asd", 123);
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex13, message=ex13 {}, args=[asd, 123]}", message.getValue());
    }


    @Test
    public void testInfoLog() {

        Mutable<String> message = new MutableObject<>();

        LOG.init(entry -> message.setValue(entry.toString()));

        assertTrue(LOG.isInfoEnabled());

        LOG.info(LOGTest.class, new RuntimeException("ex"));
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=null, args=[]}", message.getValue());

        LOG.info(LOGTest.class, new RuntimeException("ex"), () -> "ex1");
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=ex1, args=[]}", message.getValue());

        LOG.info(LOGTest.class, new RuntimeException("ex"), "ex2");
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=ex2, args=[]}", message.getValue());

        LOG.info(LOGTest.class, () -> "ex3");
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex3, args=[]}", message.getValue());

        LOG.info(LOGTest.class, "ex4");
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex4, args=[]}", message.getValue());

        LOG.info(() -> "ex5");
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex5, args=[]}", message.getValue());

        LOG.info("ex6");
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex6, args=[]}", message.getValue());

        LOG.info(new RuntimeException("ex7"), () -> "ex7");
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex7, message=ex7, args=[]}", message.getValue());

        LOG.info(new RuntimeException("ex8"), "ex8");
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex8, message=ex8, args=[]}", message.getValue());

        LOG.info("ex9 {}", "asd", 123);
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex9 {}, args=[asd, 123]}", message.getValue());

        LOG.info(LOGTest.class, "ex10 {}", "asd", 123);
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex10 {}, args=[asd, 123]}", message.getValue());

        LOG.info(new RuntimeException("ex11"));
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex11, message=null, args=[]}", message.getValue());

        LOG.info(new RuntimeException("ex12"), "ex12 {}", "asd", 123);
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex12, message=ex12 {}, args=[asd, 123]}", message.getValue());

        LOG.info(LOGTest.class, new RuntimeException("ex13"), "ex13 {}", "asd", 123);
        assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex13, message=ex13 {}, args=[asd, 123]}", message.getValue());
    }


    @Test
    public void testErrorLog() {

        Mutable<String> message = new MutableObject<>();

        LOG.init(entry -> message.setValue(entry.toString()));

        assertTrue(LOG.isErrorEnabled());

        LOG.error(LOGTest.class, new RuntimeException("ex"));
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=null, args=[]}", message.getValue());

        LOG.error(LOGTest.class, new RuntimeException("ex"), () -> "ex1");
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=ex1, args=[]}", message.getValue());

        LOG.error(LOGTest.class, new RuntimeException("ex"), "ex2");
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=ex2, args=[]}", message.getValue());

        LOG.error(LOGTest.class, () -> "ex3");
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex3, args=[]}", message.getValue());

        LOG.error(LOGTest.class, "ex4");
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex4, args=[]}", message.getValue());

        LOG.error(() -> "ex5");
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex5, args=[]}", message.getValue());

        LOG.error("ex6");
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex6, args=[]}", message.getValue());

        LOG.error(new RuntimeException("ex7"), () -> "ex7");
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex7, message=ex7, args=[]}", message.getValue());

        LOG.error(new RuntimeException("ex8"), "ex8");
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex8, message=ex8, args=[]}", message.getValue());

        LOG.error("ex9 {}", "asd", 123);
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex9 {}, args=[asd, 123]}", message.getValue());

        LOG.error(LOGTest.class, "ex10 {}", "asd", 123);
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex10 {}, args=[asd, 123]}", message.getValue());

        LOG.error(new RuntimeException("ex11"));
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex11, message=null, args=[]}", message.getValue());

        LOG.error(new RuntimeException("ex12"), "ex12 {}", "asd", 123);
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex12, message=ex12 {}, args=[asd, 123]}", message.getValue());

        LOG.error(LOGTest.class, new RuntimeException("ex13"), "ex13 {}", "asd", 123);
        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex13, message=ex13 {}, args=[asd, 123]}", message.getValue());
    }


    @Test
    public void testWarnLog() {

        Mutable<String> message = new MutableObject<>();

        LOG.init(entry -> message.setValue(entry.toString()));

        assertTrue(LOG.isWarnEnabled());

        LOG.warn(LOGTest.class, new RuntimeException("ex"));
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=null, args=[]}", message.getValue());

        LOG.warn(LOGTest.class, new RuntimeException("ex"), () -> "ex1");
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=ex1, args=[]}", message.getValue());

        LOG.warn(LOGTest.class, new RuntimeException("ex"), "ex2");
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex, message=ex2, args=[]}", message.getValue());

        LOG.warn(LOGTest.class, () -> "ex3");
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex3, args=[]}", message.getValue());

        LOG.warn(LOGTest.class, "ex4");
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex4, args=[]}", message.getValue());

        LOG.warn(() -> "ex5");
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex5, args=[]}", message.getValue());

        LOG.warn("ex6");
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex6, args=[]}", message.getValue());

        LOG.warn(new RuntimeException("ex7"), () -> "ex7");
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex7, message=ex7, args=[]}", message.getValue());

        LOG.warn(new RuntimeException("ex8"), "ex8");
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex8, message=ex8, args=[]}", message.getValue());

        LOG.warn("ex9 {}", "asd", 123);
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex9 {}, args=[asd, 123]}", message.getValue());

        LOG.warn(LOGTest.class, "ex10 {}", "asd", 123);
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex10 {}, args=[asd, 123]}", message.getValue());

        LOG.warn(new RuntimeException("ex11"));
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex11, message=null, args=[]}", message.getValue());

        LOG.warn(new RuntimeException("ex12"), "ex12 {}", "asd", 123);
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex12, message=ex12 {}, args=[asd, 123]}", message.getValue());

        LOG.warn(LOGTest.class, new RuntimeException("ex13"), "ex13 {}", "asd", 123);
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=java.lang.RuntimeException: ex13, message=ex13 {}, args=[asd, 123]}", message.getValue());
    }


    @Test
    public void testNullArguments() {
        Mutable<String> message = new MutableObject<>();
        LOG.init(entry -> message.setValue(entry.toString()));

        LOG.warn("hello {}", "asd", 123, null);
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=hello {}, args=[asd, 123, null]}", message.getValue());
    }


    /**
     * The SLF4J habit of passing the exception last must keep the stack trace at every level and in every form.
     */
    @Test
    public void testTrailingThrowableAtEveryLevel() {
        Mutable<LogEntry> entry = new MutableObject<>();
        LOG.init(entry::setValue);
        RuntimeException e = new RuntimeException("ex");

        Runnable[] withArgument = {
                () -> LOG.trace("a {}", 1, e),
                () -> LOG.debug("a {}", 1, e),
                () -> LOG.info("a {}", 1, e),
                () -> LOG.warn("a {}", 1, e),
                () -> LOG.error("a {}", 1, e),
                () -> LOG.trace(LOGTest.class, "a {}", 1, e),
                () -> LOG.debug(LOGTest.class, "a {}", 1, e),
                () -> LOG.info(LOGTest.class, "a {}", 1, e),
                () -> LOG.warn(LOGTest.class, "a {}", 1, e),
                () -> LOG.error(LOGTest.class, "a {}", 1, e),
                () -> LOG.log(LogEntry.LogEntryLevel.INFO, LOGTest.class, null, () -> "a {}", 1, e),
        };
        for (Runnable call : withArgument) {
            entry.setValue(null);
            call.run();
            assertSame(e, entry.getValue().getThrowable());
            assertArrayEquals(new Object[]{1}, entry.getValue().getArgs());
            assertEquals("a 1", entry.getValue().getFormattedMessage());
            assertEquals(LOGTest.class, entry.getValue().getSourceClass());
        }

        Runnable[] withoutArguments = {
                () -> LOG.trace("Failed", e),
                () -> LOG.debug("Failed", e),
                () -> LOG.info("Failed", e),
                () -> LOG.warn("Failed", e),
                () -> LOG.error("Failed", e),
        };
        for (Runnable call : withoutArguments) {
            entry.setValue(null);
            call.run();
            assertSame(e, entry.getValue().getThrowable());
            assertArrayEquals(new Object[0], entry.getValue().getArgs());
            assertEquals("Failed", entry.getValue().getFormattedMessage());
        }

        // an explicit exception keeps its place, the trailing one stays an ordinary argument
        RuntimeException trailing = new RuntimeException("trailing");
        LOG.error(e, "text", trailing);
        assertSame(e, entry.getValue().getThrowable());
        assertArrayEquals(new Object[]{trailing}, entry.getValue().getArgs());
    }

    @Test
    public void testCallerFrame() {
        Mutable<LogEntry> entry = new MutableObject<>();
        LOG.init(entry::setValue);

        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.info("where am I");

        StackTraceElement caller = entry.getValue().getCaller();
        assertEquals(LOGTest.class.getName(), caller.getClassName());
        assertEquals("testCallerFrame", caller.getMethodName());
        assertEquals("LOGTest.java", caller.getFileName());
        assertEquals(line, caller.getLineNumber());
    }

    @Test
    public void testCallerAtEveryLevel() {
        Mutable<LogEntry> entry = new MutableObject<>();
        LOG.init(entry::setValue);
        RuntimeException e = new RuntimeException("ex");

        Runnable[] calls = {
                () -> LOG.trace(e, () -> "m"), () -> LOG.trace(e, "m"), () -> LOG.trace(e), () -> LOG.trace(() -> "m"),
                () -> LOG.trace("m"), () -> LOG.trace("m {}", 1), () -> LOG.trace(e, "m {}", 1),
                () -> LOG.debug(e, () -> "m"), () -> LOG.debug(e, "m"), () -> LOG.debug(e), () -> LOG.debug(() -> "m"),
                () -> LOG.debug("m"), () -> LOG.debug("m {}", 1), () -> LOG.debug(e, "m {}", 1),
                () -> LOG.info(e, () -> "m"), () -> LOG.info(e, "m"), () -> LOG.info(e), () -> LOG.info(() -> "m"),
                () -> LOG.info("m"), () -> LOG.info("m {}", 1), () -> LOG.info(e, "m {}", 1),
                () -> LOG.warn(e, () -> "m"), () -> LOG.warn(e, "m"), () -> LOG.warn(e), () -> LOG.warn(() -> "m"),
                () -> LOG.warn("m"), () -> LOG.warn("m {}", 1), () -> LOG.warn(e, "m {}", 1),
                () -> LOG.error(e, () -> "m"), () -> LOG.error(e, "m"), () -> LOG.error(e), () -> LOG.error(() -> "m"),
                () -> LOG.error("m"), () -> LOG.error("m {}", 1), () -> LOG.error(e, "m {}", 1),
        };
        for (Runnable call : calls) {
            entry.setValue(null);
            call.run();
            StackTraceElement caller = entry.getValue().getCaller();
            assertEquals(LOGTest.class.getName(), caller.getClassName());
            assertTrue(caller.getMethodName().startsWith("lambda$testCallerAtEveryLevel"), caller.getMethodName());
            assertEquals("LOGTest.java", caller.getFileName());
        }
    }

    @Test
    public void testLevelChecksWithoutClassUseTheCaller() {
        java.util.List<Class> checked = new java.util.ArrayList<>();
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                checked.add(sourceClass);
                return true;
            }
        });

        LOG.isTraceEnabled();
        LOG.isDebugEnabled();
        LOG.isInfoEnabled();
        LOG.isWarnEnabled();
        LOG.isErrorEnabled();

        assertEquals(java.util.Arrays.asList(LOGTest.class, LOGTest.class, LOGTest.class, LOGTest.class, LOGTest.class), checked);
    }

    @Test
    public void testNoCallerWithExplicitClass() {
        Mutable<LogEntry> entry = new MutableObject<>();
        LOG.init(entry::setValue);
        RuntimeException e = new RuntimeException("ex");

        Runnable[] calls = {
                () -> LOG.info(LOGTest.class, e, () -> "m"), () -> LOG.info(LOGTest.class, e, "m"),
                () -> LOG.info(LOGTest.class, () -> "m"), () -> LOG.info(LOGTest.class, "m"),
                () -> LOG.info(LOGTest.class, "m {}", 1), () -> LOG.info(LOGTest.class, e),
                () -> LOG.info(LOGTest.class, e, "m {}", 1),
                () -> LOG.log(LogEntry.LogEntryLevel.INFO, LOGTest.class, null, () -> "m"),
        };
        for (Runnable call : calls) {
            entry.setValue(null);
            call.run();
            assertEquals(LOGTest.class, entry.getValue().getSourceClass());
            assertNull(entry.getValue().getCaller());
        }
    }

    @Test
    public void testCallerInNestedClasses() {
        Mutable<LogEntry> entry = new MutableObject<>();
        LOG.init(entry::setValue);

        new NamedInnerCLass().log("from a named inner class");
        assertEquals(LOGTest.class, entry.getValue().getSourceClass());
        assertEquals(NamedInnerCLass.class.getName(), entry.getValue().getCaller().getClassName());
        assertEquals("log", entry.getValue().getCaller().getMethodName());

        new Runnable() {
            @Override
            public void run() {
                LOG.info("from an anonymous class");
            }
        }.run();
        assertEquals(LOGTest.class, entry.getValue().getSourceClass());
        assertTrue(entry.getValue().getCaller().getClassName().startsWith(LOGTest.class.getName() + "$"));
        assertEquals("run", entry.getValue().getCaller().getMethodName());
    }

    @SuppressWarnings({"Convert2Lambda", "TrivialFunctionalExpressionUsage", "AnonymousInnerClass"})
    @Test
    public void testInnerClasses() {

        Mutable<String> message = new MutableObject<>();

        LOG.init(entry -> message.setValue(entry.toString()));

        //lambda
        ((Runnable) () -> {
            LOG.debug("ex6");
            assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex6, args=[]}", message.getValue());
        }).run();

        //anonymous class
        new Runnable() {
            @Override
            public void run() {
                LOG.debug("ex7");
                assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex7, args=[]}", message.getValue());
            }
        }.run();

        //anonymous class with two levels
        new Runnable() {
            @Override
            public void run() {
                new Runnable() {
                    @Override
                    public void run() {
                        LOG.debug("ex8");
                        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex8, args=[]}", message.getValue());
                    }
                }.run();
            }
        }.run();

        //anonymous class with three levels
        new Runnable() {
            @Override
            public void run() {
                new Runnable() {
                    @Override
                    public void run() {
                        new Runnable() {
                            @Override
                            public void run() {
                                LOG.debug("ex9");
                                assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex9, args=[]}", message.getValue());
                            }
                        }.run();
                    }
                }.run();
            }
        }.run();

        // named inner class
        new NamedInnerCLass().log("ex10");
        assertEquals("LogEntry{level=DEBUG, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=ex10, args=[]}", message.getValue());
    }


    private class NamedInnerCLass {

        public void log(String message) {
            LOG.debug(message);
        }
    }

    /**
     * The caller lives in a child class loader (app server, plugin framework) that LOG's own loader cannot see,
     * but it is the thread's context class loader: the source class must still be detected.
     */
    @Test
    public void testCallerFromContextClassLoader() throws Exception {
        Mutable<Class> sourceClass = new MutableObject<>();
        Mutable<StackTraceElement> frame = new MutableObject<>();
        LOG.init(entry -> {
            sourceClass.setValue(entry.getSourceClass());
            frame.setValue(entry.getCaller());
        });

        try (ForeignCaller caller = new ForeignCaller()) {
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(caller.loader);
            try {
                caller.callLog();
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
            assertSame(caller.callerClass, sourceClass.getValue());
            assertEquals(ForeignCaller.CLASS_NAME, frame.getValue().getClassName());
            assertEquals("call", frame.getValue().getMethodName());
        }
    }

    /**
     * The caller's class loader is neither the context class loader nor LOG's loader: the frame cannot be resolved,
     * so it is skipped instead of breaking the logging call.
     */
    @Test
    public void testCallerFromUnresolvableClassLoader() throws Exception {
        Mutable<Class> sourceClass = new MutableObject<>();
        Mutable<StackTraceElement> frame = new MutableObject<>();
        LOG.init(entry -> {
            sourceClass.setValue(entry.getSourceClass());
            frame.setValue(entry.getCaller());
        });

        try (ForeignCaller caller = new ForeignCaller()) {
            caller.callLog();
            // the next resolvable frame is a JDK reflection class, which differs between JDK versions
            assertNotNull(sourceClass.getValue());
            assertTrue(sourceClass.getValue() != caller.callerClass);
            // the reported frame is the one the source class was taken from, not the skipped foreign one
            assertTrue(frame.getValue().getClassName().startsWith(sourceClass.getValue().getName()), frame.getValue().getClassName());
        }
    }

    /**
     * The context class loader cannot see the caller (or is absent), but LOG's own loader can: the fallback resolves it.
     */
    @Test
    public void testCallerResolvedByOwnLoaderWhenContextLoaderCannotSeeIt() throws Exception {
        Mutable<Class> sourceClass = new MutableObject<>();
        LOG.init(entry -> sourceClass.setValue(entry.getSourceClass()));

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader isolated = new URLClassLoader(new URL[0], null)) {
            Thread.currentThread().setContextClassLoader(isolated);
            LOG.info("the context class loader sees nothing");
            assertEquals(LOGTest.class, sourceClass.getValue());

            Thread.currentThread().setContextClassLoader(null);
            LOG.info("there is no context class loader at all");
            assertEquals(LOGTest.class, sourceClass.getValue());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    /**
     * Compiles a class that calls {@code LOG.info(...)} into a temporary directory and loads it through a child-first
     * class loader, so that the class is invisible to the loader that loaded {@link LOG}.
     */
    private static class ForeignCaller implements AutoCloseable {

        private static final String CLASS_NAME = "nanolog.foreign.ForeignCaller";

        final Path dir;
        final URLClassLoader loader;
        final Class<?> callerClass;

        ForeignCaller() throws Exception {
            dir = Files.createTempDirectory("nanolog-foreign");
            Path source = dir.resolve("nanolog/foreign/ForeignCaller.java");
            Files.createDirectories(source.getParent());
            Files.write(source, ("package nanolog.foreign;\n" +
                                 "public class ForeignCaller {\n" +
                                 "    public static void call() { com.nanolaba.logging.LOG.info(\"from a foreign class loader\"); }\n" +
                                 "}\n").getBytes(StandardCharsets.UTF_8));

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            assertNotNull(compiler, "the tests must run on a JDK");
            int rc = compiler.run(null, null, null, "-d", dir.toString(), "-cp", System.getProperty("java.class.path"), source.toString());
            assertEquals(0, rc, "compilation of the foreign caller failed");

            loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, LOGTest.class.getClassLoader());
            callerClass = loader.loadClass(CLASS_NAME);
            assertSame(loader, callerClass.getClassLoader());
        }

        void callLog() throws Exception {
            callerClass.getMethod("call").invoke(null);
        }

        @Override
        public void close() throws Exception {
            loader.close();
            Files.walk(dir)
                 .sorted(java.util.Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }
}