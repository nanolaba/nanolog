package com.nanolaba.logging;

import com.nanolaba.logging.wrap.Audit;
import com.nanolaba.logging.wrapper.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code nanolog.caller.skip} and {@code LOG.skipCaller(...)}: the frames of logging helpers are skipped like those of
 * {@code LOG}, so an entry belongs to the code that called the helper.
 */
class CallerSkipTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    private final Map<String, String> environment = new HashMap<>();
    private final CallerDetection.Locator originalLocator = LOG.callerLocator;
    private final Consumer<Thread> originalHooks = Summary.shutdownHooks;
    private MemoryLogger memory;

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        Summary.reset();
        Summary.shutdownHooks = hook -> { };
        DbgSource.clearCache();
        LOG.clearLimits();
    }

    @AfterEach
    public void tearDown() {
        if (memory != null) {
            memory.close();
        }
        System.clearProperty(DefaultLoggerCreator.PROPERTY_CALLER_SKIP);
        DefaultLoggerCreator.environment = System::getenv;
        CallerDetection.forgetSkipped();
        LOG.callerLocator = originalLocator;
        LOG.init();
        Summary.shutdownHooks = originalHooks;
        Summary.reset();
        DbgSource.clearCache();
        LOG.clearLimits();
    }

    private void skip(String value) {
        System.setProperty(DefaultLoggerCreator.PROPERTY_CALLER_SKIP, value);
        init();
    }

    /**
     * {@code LOG.init()}, which reads the settings and installs the default logger, then the memory logger again.
     */
    private void init() {
        LOG.init();
        if (memory == null) {
            memory = MemoryLogger.install();
        } else {
            LOG.init(memory);
        }
    }

    private void assertSource(Class expected, String message) {
        assertEquals(message, last().getFormattedMessage());
        assertEquals(expected, last().getSourceClass(), message);
    }

    private static int here() {
        return new Throwable().getStackTrace()[1].getLineNumber();
    }

    private LogEntry last() {
        List<LogEntry> entries = memory.getEntries();
        return entries.get(entries.size() - 1);
    }

    @ParameterizedTest
    @MethodSource("com.nanolaba.logging.CallerDetectionTest#locators")
    public void testAHelperNamedInTheSettingIsSkipped(CallerDetection.Locator locator) {
        skip("com.nanolaba.logging.wrap.Audit");
        LOG.callerLocator = locator;

        int line = here() + 1;
        Audit.record("Paid {}", 5);

        LogEntry entry = last();
        assertEquals("Paid 5", entry.getFormattedMessage());
        assertEquals(CallerSkipTest.class, entry.getSourceClass());
        assertEquals(CallerSkipTest.class.getName(), entry.getCaller().getClassName());
        assertEquals("testAHelperNamedInTheSettingIsSkipped", entry.getCaller().getMethodName());
        assertEquals(line, entry.getCaller().getLineNumber());
        assertEquals(Audit.class.getName(), entry.getCallerBoundary(), "a location-aware backend looks past the helper");
    }

    @Test
    public void testWithoutTheSettingTheHelperIsTheSource() {
        init();

        Audit.record("Paid");

        assertSource(Audit.class, "Paid");
        assertEquals("record", last().getCaller().getMethodName());
        assertEquals(LogEntry.LOG_BOUNDARY, last().getCallerBoundary());
    }

    @Test
    public void testAPackageCoversItsClassesButNotAPackageWithALongerName() {
        skip("com.nanolaba.logging.wrap");

        Audit.record("from a helper");
        assertSource(CallerSkipTest.class, "from a helper");

        Tool.record("from a tool");
        assertSource(Tool.class, "from a tool");
        assertEquals(LogEntry.LOG_BOUNDARY, last().getCallerBoundary());
    }

    @Test
    public void testNestedClassesOfASkippedClassAreSkipped() {
        skip("com.nanolaba.logging.wrap.Audit");

        Audit.Inner.record("inner");
        assertSource(CallerSkipTest.class, "inner");
        assertEquals(Audit.Inner.class.getName(), last().getCallerBoundary());

        Audit.recordInner("through the outer class");
        assertSource(CallerSkipTest.class, "through the outer class");
        assertEquals(Audit.class.getName(), last().getCallerBoundary(), "the boundary is the frame right before the caller");
    }

    @Test
    public void testANestedClassAloneCanBeSkipped() {
        skip("com.nanolaba.logging.wrap.Audit$Inner");

        Audit.recordInner("through the outer class");

        assertSource(Audit.class, "through the outer class");
        assertEquals("recordInner", last().getCaller().getMethodName());
        assertEquals(Audit.Inner.class.getName(), last().getCallerBoundary());
    }

    @Test
    public void testTheEnvironmentVariable() {
        environment.put("NANOLOG_CALLER_SKIP", "com.nanolaba.logging.wrap");
        init();

        Audit.record("from a helper");

        assertSource(CallerSkipTest.class, "from a helper");
    }

    @Test
    public void testInvalidNamesAreReportedAndTheOthersApply() {
        skip(" com.acme.* ,, com..x , com.nanolaba.logging.wrap.Audit ,");

        assertEquals("[NANOLOG] Invalid value \"com.acme.*\" of \"nanolog.caller.skip\": expected class or package names separated by commas; " +
                     "set as a system property" + NL +
                     "[NANOLOG] Invalid value \"com..x\" of \"nanolog.caller.skip\": expected class or package names separated by commas; " +
                     "set as a system property" + NL, getErrAndClear(), "empty items are not reported");

        Audit.record("from a helper");
        assertSource(CallerSkipTest.class, "from a helper");
    }

    @Test
    public void testSkipCaller() {
        init();

        LOG.skipCaller(Audit.class);
        Audit.record("from a helper");
        assertSource(CallerSkipTest.class, "from a helper");
        assertEquals(Audit.class.getName(), last().getCallerBoundary());

        // a library registers its helper once, and LOG.init() keeps it
        init();
        Audit.record("after LOG.init()");
        assertSource(CallerSkipTest.class, "after LOG.init()");

        LOG.skipCaller((Class<?>[]) null);
        LOG.skipCaller(null, Tool.class);
        Tool.record("from a tool");
        assertSource(CallerSkipTest.class, "from a tool");
        assertEquals("", getErrAndClear());

        CallerDetection.forgetSkipped();
        Audit.record("forgotten");
        assertSource(Audit.class, "forgotten");
    }

    @Test
    public void testTheSettingAndSkipCallerAddUp() {
        skip("com.nanolaba.logging.wrapper");
        LOG.skipCaller(Audit.class);

        Tool.record("from a tool");
        assertSource(CallerSkipTest.class, "from a tool");
        Audit.record("from a helper");
        assertSource(CallerSkipTest.class, "from a helper");
    }

    @Test
    public void testAStackOfSkippedFramesOnlyHasNoCaller() {
        skip("com,org,java,jdk,sun");

        Audit.record("nobody");

        assertEquals("nobody", last().getFormattedMessage());
        assertNull(last().getSourceClass());
        assertNull(last().getCaller());
        assertEquals(LogEntry.LOG_BOUNDARY, last().getCallerBoundary());
    }

    @Test
    public void testEachCallerOfAHelperHasItsOwnRateLimit() {
        skip("com.nanolaba.logging.wrap.Audit");

        for (int i = 0; i < 2; i++) {
            Audit.recordOnce("first call site");
            Audit.recordOnce("second call site");
        }

        assertEquals(2, memory.getEntries().size(), memory.getMessages().toString());
        assertEquals(Audit.class.getName(), last().getCallerBoundary());
    }

    @Test
    public void testDbgThroughAHelper() {
        skip("com.nanolaba.logging.wrap.Audit");

        int line = here() + 1;
        Audit.dbg(42);
        assertTrue(last().isDbg());
        assertEquals(line, last().getCaller().getLineNumber());
        assertEquals(Audit.class.getName(), last().getCallerBoundary());

        Audit.dbgOnce(7);
        assertEquals("testDbgThroughAHelper", last().getCaller().getMethodName());
        assertEquals(Audit.class.getName(), last().getCallerBoundary());
        Dbg.clearCalls();
    }

    @Test
    public void testAFrameOfNanologRightBeforeTheCallerKeepsItsBoundary() {
        skip("com.nanolaba.logging.wrap.Audit");

        LOG.catching(Audit.task("in a task"));

        assertEquals("in a task", last().getFormattedMessage());
        assertEquals(CallerSkipTest.class, last().getSourceClass());
        assertEquals(LogEntry.LOG_BOUNDARY, last().getCallerBoundary(), "LOG.catching, not the helper, is the frame before the caller");
    }
}
