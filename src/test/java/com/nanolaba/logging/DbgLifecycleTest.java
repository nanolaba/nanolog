package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifecycle of {@code LOG.dbg}, which is meant to be temporary: the call sites that ran are listed at exit, and
 * {@code nanolog.dbg} switches the calls off or reports each of them as a diagnostic.
 */
class DbgLifecycleTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();
    private static final String FILE = "DbgLifecycleTest.java:";

    private final Consumer<Thread> originalHooks = Dbg.shutdownHooks;
    private final CallerDetection.Locator originalLocator = LOG.callerLocator;
    private final List<Thread> hooks = new ArrayList<>();
    private final Map<String, String> environment = new HashMap<>();
    private MemoryLogger memory;

    @BeforeEach
    public void install() {
        Dbg.clearCalls();
        Dbg.shutdownHooks = hooks::add;
        DefaultLoggerCreator.environment = environment::get;
        DbgSource.clearCache();
        Dump.clearCache();
        LOG.clearLimits();
        memory = MemoryLogger.install();
    }

    @AfterEach
    public void restore() {
        memory.close();
        Dbg.shutdownHooks = originalHooks;
        Dbg.clearCalls();
        LOG.callerLocator = originalLocator;
        DefaultLoggerCreator.environment = System::getenv;
        System.clearProperty(DefaultLoggerCreator.PROPERTY_DBG);
        LOG.clearLimits();
        LOG.init();
    }

    private static int lineOf(Throwable here) {
        return here.getStackTrace()[0].getLineNumber();
    }

    /**
     * Runs the hooks registered so far, as the JVM does when it exits, and returns what they printed.
     */
    private String exit() {
        getErrAndClear();
        for (Thread hook : hooks) {
            hook.run();
        }
        return getErrAndClear();
    }

    // THE LIST AT EXIT

    @Test
    public void testTheCallSitesThatRanAreListedAtExitInTheOrderTheyFirstRan() {
        int first = lineOf(new Throwable()) + 1;
        LOG.dbg("first");
        int second = lineOf(new Throwable()) + 2;
        for (int i = 0; i < 3; i++) {
            LOG.dbg(i);
        }
        int third = lineOf(new Throwable()) + 1;
        LOG.dbg();

        assertEquals(5, memory.getEntries().size(), "every call is printed as before");
        assertEquals(1, hooks.size(), "one hook, registered by the first call");
        assertEquals("nanolog-dbg", hooks.get(0).getName());
        assertEquals("[NANOLOG] 3 LOG.dbg calls ran in this JVM: " + FILE + first + ", " + FILE + second + ", " + FILE + third + NL,
                     exit());
    }

    @Test
    public void testOneCallSite() {
        int line = lineOf(new Throwable()) + 1;
        LOG.dbg(1, 2);

        assertEquals("[NANOLOG] 1 LOG.dbg call ran in this JVM: " + FILE + line + NL, exit());
    }

    @Test
    public void testLimitedCallsAreListedToo() {
        int once = lineOf(new Throwable()) + 2;
        for (int i = 0; i < 2; i++) {
            LOG.once().dbg(i);
        }
        int limited = lineOf(new Throwable()) + 1;
        LOG.atMostEvery(1, TimeUnit.HOURS).dbg();

        assertEquals("[NANOLOG] 2 LOG.dbg calls ran in this JVM: " + FILE + once + ", " + FILE + limited + NL, exit());
    }

    @Test
    public void testNothingIsListedWithoutDbgCalls() {
        LOG.debug("plain");
        LOG.info("{}", 1);

        assertTrue(hooks.isEmpty());
    }

    @Test
    public void testCallsWhoseLevelIsOffAreNotListed() {
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
                throw new AssertionError("nothing is logged");
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                return false;
            }
        });

        assertEquals(1, (int) LOG.dbg(1));
        LOG.once().dbg(2);

        assertTrue(hooks.isEmpty());
    }

    @Test
    public void testAClearedListPrintsNothing() {
        LOG.dbg(1);
        Dbg.clearCalls();

        assertEquals("", exit());
    }

    @Test
    public void testACallSiteWithoutAFrameIsListedAsUnknown() {
        LOG.callerLocator = () -> CallerDetection.Caller.NONE;
        LOG.dbg(1);

        assertEquals("[NANOLOG] 1 LOG.dbg call ran in this JVM: unknown source" + NL, exit());
    }

    @Test
    public void testAHookThatCannotBeRegisteredDoesNotFailTheCall() {
        Dbg.shutdownHooks = hook -> {
            throw new IllegalStateException("Shutdown in progress");
        };

        assertEquals(42, (int) LOG.dbg(42));
        assertEquals(1, memory.getEntries().size());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testSite() {
        assertEquals("Foo.java:12", Dbg.site(new StackTraceElement("a.Foo", "run", "Foo.java", 12)));
        assertEquals("Foo.java:0", Dbg.site(new StackTraceElement("a.Foo", "run", "Foo.java", 0)));
        assertEquals("Foo.java", Dbg.site(new StackTraceElement("a.Foo", "run", "Foo.java", -1)));
        assertEquals("a.Foo:12", Dbg.site(new StackTraceElement("a.Foo", "run", null, 12)));
        assertEquals("unknown source", Dbg.site(null));
    }

    // NANOLOG.DBG

    @Test
    public void testModeIsReadFromTheSettings() {
        assertEquals(Dbg.Mode.ON, DefaultLoggerCreator.readDbgMode());

        System.setProperty(DefaultLoggerCreator.PROPERTY_DBG, " Off ");
        assertEquals(Dbg.Mode.OFF, DefaultLoggerCreator.readDbgMode());

        System.setProperty(DefaultLoggerCreator.PROPERTY_DBG, "FAIL");
        assertEquals(Dbg.Mode.FAIL, DefaultLoggerCreator.readDbgMode());

        System.setProperty(DefaultLoggerCreator.PROPERTY_DBG, "on");
        assertEquals(Dbg.Mode.ON, DefaultLoggerCreator.readDbgMode());

        System.clearProperty(DefaultLoggerCreator.PROPERTY_DBG);
        environment.put("NANOLOG_DBG", "off");
        assertEquals(Dbg.Mode.OFF, DefaultLoggerCreator.readDbgMode());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testAnInvalidModeIsReportedAndPrints() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_DBG, "yes");

        assertEquals(Dbg.Mode.ON, DefaultLoggerCreator.readDbgMode());
        assertEquals("[NANOLOG] Invalid value \"yes\" of \"nanolog.dbg\": expected on, off or fail" + NL, getErrAndClear());
    }

    @Test
    public void testInitReadsTheMode() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_DBG, "off");
        LOG.init();
        assertEquals(Dbg.Mode.OFF, Dbg.mode);

        System.clearProperty(DefaultLoggerCreator.PROPERTY_DBG);
        LOG.init();
        assertEquals(Dbg.Mode.ON, Dbg.mode);
    }

    /**
     * {@code LOG.init()} may change the mode while other threads call {@code dbg}; pitest does not mutate
     * {@code volatile}, so the contract is asserted, as for {@code LOG.strict}.
     */
    @Test
    public void testModeAndDumpSettingAreSafelyPublished() throws NoSuchFieldException {
        assertTrue(Modifier.isVolatile(Dbg.class.getDeclaredField("mode").getModifiers()), "Dbg.mode must be volatile");
        assertTrue(Modifier.isVolatile(Dump.class.getDeclaredField("auto").getModifiers()), "Dump.auto must be volatile");
    }

    @Test
    public void testOffSilencesEveryCallWithoutAStackWalk() {
        Dbg.mode = Dbg.Mode.OFF;
        CountingLocator counting = new CountingLocator(originalLocator);
        LOG.callerLocator = counting;
        List<String> list = new ArrayList<>();

        assertSame(list, LOG.dbg(list));
        LOG.dbg(1, 2);
        LOG.dbg();
        assertSame(list, LOG.once().dbg(list));
        LOG.once().dbg(1, 2);
        LOG.once().dbg();

        assertEquals(0, counting.walks.get());
        assertTrue(memory.getEntries().isEmpty());
        assertTrue(hooks.isEmpty());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testOnReportsNothing() {
        LOG.dbg(1);

        assertTrue(memory.getDiagnostics().isEmpty());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testFailReportsEveryCallAndStillPrintsIt() {
        Dbg.mode = Dbg.Mode.FAIL;

        int line = lineOf(new Throwable()) + 2;
        for (int i = 0; i < 2; i++) {
            assertEquals(i, (int) LOG.dbg(i));
        }

        String diagnostic = "com.nanolaba.logging.DbgLifecycleTest.testFailReportsEveryCallAndStillPrintsIt(" + FILE + line +
                            "): LOG.dbg call left in the code (nanolog.dbg=fail)";
        assertEquals(2, memory.getEntries().size());
        assertEquals(Arrays.asList(diagnostic, diagnostic), memory.getDiagnostics(), "every occurrence reaches the logger");
        assertEquals("[NANOLOG] " + diagnostic + NL, getErrAndClear(), "standard error gets each call site once");
        AssertionError error = assertThrows(AssertionError.class, memory::assertNoDiagnostics);
        assertTrue(error.getMessage().startsWith("Expected no diagnostics, but Nanolog reported 2:" + NL + diagnostic), error.getMessage());
        assertEquals("[NANOLOG] 1 LOG.dbg call ran in this JVM: " + FILE + line + NL, exit());
    }

    @Test
    public void testFailReportsLimitedCallsThatGetThrough() {
        Dbg.mode = Dbg.Mode.FAIL;

        for (int i = 0; i < 3; i++) {
            LOG.once().dbg(i);
        }

        assertEquals(1, memory.getEntries().size());
        assertEquals(1, memory.getDiagnostics().size());
        assertTrue(memory.getDiagnostics().get(0).endsWith("): LOG.dbg call left in the code (nanolog.dbg=fail)"));
    }

    @Test
    public void testFailWithoutAFrame() {
        Dbg.mode = Dbg.Mode.FAIL;
        LOG.callerLocator = () -> CallerDetection.Caller.NONE;

        LOG.dbg(1);

        assertEquals(Arrays.asList("unknown source: LOG.dbg call left in the code (nanolog.dbg=fail)"), memory.getDiagnostics());
    }
}
