package com.nanolaba.logging;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }


    @Test
    public void testNullArguments() {
        Mutable<String> message = new MutableObject<>();
        LOG.init(entry -> message.setValue(entry.toString()));

        LOG.warn("hello {}", "asd", 123, null);
        assertEquals("LogEntry{level=WARN, sourceClass=com.nanolaba.logging.LOGTest, throwable=null, message=hello {}, args=[asd, 123, null]}", message.getValue());
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
}