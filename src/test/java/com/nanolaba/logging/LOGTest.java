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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        LOG.init(entry -> sourceClass.setValue(entry.getSourceClass()));

        try (ForeignCaller caller = new ForeignCaller()) {
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(caller.loader);
            try {
                caller.callLog();
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
            assertSame(caller.callerClass, sourceClass.getValue());
        }
    }

    /**
     * The caller's class loader is neither the context class loader nor LOG's loader: the frame cannot be resolved,
     * so it is skipped instead of breaking the logging call.
     */
    @Test
    public void testCallerFromUnresolvableClassLoader() throws Exception {
        Mutable<Class> sourceClass = new MutableObject<>();
        LOG.init(entry -> sourceClass.setValue(entry.getSourceClass()));

        try (ForeignCaller caller = new ForeignCaller()) {
            caller.callLog();
            // the next resolvable frame is a JDK reflection class, which differs between JDK versions
            assertNotNull(sourceClass.getValue());
            assertTrue(sourceClass.getValue() != caller.callerClass);
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