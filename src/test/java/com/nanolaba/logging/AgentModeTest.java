package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.nanolaba.logging.DefaultLoggerCreator.*;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent mode: Nanolog detects a coding agent from its environment variables and keeps its own console short.
 */
class AgentModeTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();
    private static final String RULE = PROPERTY_LEVEL + "." + AgentModeTest.class.getName();
    private static final String OFF_HINT = "nanolog.agent=off turns it off";

    @TempDir
    Path root;

    private final Map<String, String> environment = new HashMap<>();
    private final Supplier<Set<String>> originalNames = DefaultLoggerCreator.environmentNames;
    private final Consumer<Thread> originalHooks = Summary.shutdownHooks;
    private final List<Thread> hooks = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        DefaultLoggerCreator.environmentNames = environment::keySet;
        Summary.shutdownHooks = hooks::add;
        Summary.reset();
        DbgSource.clearCache();
        LOG.clearLimits();
        RunLog.reset();
        Agent.reset();
    }

    @AfterEach
    public void tearDown() {
        DefaultLoggerCreator.environment = System::getenv;
        DefaultLoggerCreator.environmentNames = originalNames;
        for (String property : new String[]{PROPERTY_AGENT, PROPERTY_RUN, PROPERTY_RUN_DIR, PROPERTY_LEVEL, RULE,
                PROPERTY_LOGGER_CLASS, ConsoleLogger.PROPERTY_SHOW_DATE, ConsoleLogger.PROPERTY_EXCEPTION_EXCLUDE,
                ConsoleLogger.PROPERTY_EXCEPTION_MAX_FRAMES}) {
            System.clearProperty(property);
        }
        RunLog.reset();
        Agent.reset();
        Dbg.ignoreLevels = false;
        Summary.reset();
        Summary.shutdownHooks = originalHooks;
        LOG.clearLimits();
        LOG.init();
    }

    // DETECTION

    @Test
    public void testNoAgentWithoutItsVariables() {
        assertNull(Agent.detect());
    }

    @Test
    public void testEachVariableOfACodingAgentTurnsItOn() {
        for (String name : new String[]{"AI_AGENT", "AGENT", "CLAUDECODE", "GEMINI_CLI", "CODEX_SANDBOX", "CODEX_CI",
                "CURSOR_AGENT", "VSCODE_AGENT", "CLINE_ACTIVE"}) {
            environment.clear();
            environment.put(name, "1");
            assertEquals(name + "=1", Agent.detect(), name);
        }
    }

    @Test
    public void testTheProposedStandardIsNamedFirst() {
        environment.put("CLAUDECODE", "1");
        environment.put("AI_AGENT", "claude-code");

        assertEquals("AI_AGENT=claude-code", Agent.detect());
    }

    @Test
    public void testAVariableSetToNoDoesNotCount() {
        for (String value : new String[]{"0", "false", "No", " OFF ", "", "  "}) {
            environment.put("AI_AGENT", value);
            assertNull(Agent.detect(), "[" + value + "]");
        }
        environment.put("CLAUDECODE", "1");
        assertEquals("CLAUDECODE=1", Agent.detect(), "the next variable still counts");
    }

    @Test
    public void testTheSettingOverridesTheDetection() {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_AGENT, "off");
        assertNull(Agent.detect());

        System.setProperty(PROPERTY_AGENT, " Auto ");
        assertEquals("CLAUDECODE=1", Agent.detect());

        environment.clear();
        System.setProperty(PROPERTY_AGENT, "ON");
        assertEquals("nanolog.agent=on", Agent.detect());

        System.clearProperty(PROPERTY_AGENT);
        environment.put("NANOLOG_AGENT", "on");
        assertEquals("nanolog.agent=on", Agent.detect());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testAnInvalidSettingIsReportedAndDetects() {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_AGENT, "yes");

        assertEquals("CLAUDECODE=1", Agent.detect());
        assertEquals("[NANOLOG] Invalid value \"yes\" of \"nanolog.agent\": expected auto, on or off; set as a system property" + NL, getErrAndClear());
    }

    // THE CONSOLE

    @Test
    public void testTheConsoleShowsWarnAndAboveWithoutDatesAndWithShortStackTraces() {
        ConsoleLogger console = new ConsoleLogger();

        assertFalse(console.useAgentDefaults(), "no level is set");
        assertFalse(console.isTraceEnabled());
        assertFalse(console.isDebugEnabled());
        assertFalse(console.isInfoEnabled());
        assertTrue(console.isWarnEnabled());
        assertTrue(console.isErrorEnabled());
        assertFalse(console.isShowDate());
        assertEquals(Agent.EXCLUDE, console.getExceptionExclude());
        assertEquals(Agent.MAX_FRAMES, console.getExceptionMaxFrames());
    }

    @Test
    public void testTheConsoleOfAgentModeIsShort() throws ReflectiveOperationException {
        ConsoleLogger console = new ConsoleLogger();
        console.useAgentDefaults();
        LOG.init(console);
        Method fail = AgentModeTest.class.getDeclaredMethod("failToShip");

        LOG.info("hidden");
        try {
            fail.invoke(null);
        } catch (InvocationTargetException e) {
            LOG.warn(e.getCause(), "Shipping failed");
        }

        String out = getOutAndClear();
        String escaped = NL.replace("\r", "\\r").replace("\n", "\\n");
        assertTrue(out.startsWith("WARN [AgentModeTest] Shipping failed java.lang.IllegalStateException: no stock" + escaped +
                                  "\tat com.nanolaba.logging.AgentModeTest.failToShip(AgentModeTest.java:"), out);
        assertTrue(out.contains(" frames excluded" + escaped), out);
        assertTrue(out.endsWith(NL) && out.indexOf('\n') == out.length() - 1, "one line per entry: " + out);
        assertFalse(out.contains("Method.invoke"), out);
        assertFalse(out.contains("hidden"), out);
    }

    private static void failToShip() {
        throw new IllegalStateException("no stock");
    }

    @Test
    public void testAnExplicitLevelWins() {
        System.setProperty(PROPERTY_LEVEL, "debug");
        ConsoleLogger console = new ConsoleLogger();

        assertTrue(console.useAgentDefaults());
        assertTrue(console.isDebugEnabled());
        assertFalse(console.isTraceEnabled());
    }

    @Test
    public void testAnInvalidLevelGivesTheDefaultOfAgentModeAndIsReportedOnce() {
        System.setProperty(PROPERTY_LEVEL, "loud");
        ConsoleLogger console = new ConsoleLogger();
        getErrAndClear();

        assertFalse(console.useAgentDefaults());
        assertFalse(console.isInfoEnabled());
        assertTrue(console.isWarnEnabled());
        assertEquals("", getErrAndClear(), "the constructor has reported it");
    }

    @Test
    public void testAPackageRuleWins() {
        System.setProperty(RULE, "DEBUG");
        ConsoleLogger console = new ConsoleLogger();
        console.useAgentDefaults();

        assertTrue(console.isEnabled(DEBUG, AgentModeTest.class));
        assertFalse(console.isEnabled(DEBUG, String.class));
    }

    @Test
    public void testExplicitDisplaySettingsWin() {
        System.setProperty(ConsoleLogger.PROPERTY_SHOW_DATE, "true");
        System.setProperty(ConsoleLogger.PROPERTY_EXCEPTION_EXCLUDE, "");
        System.setProperty(ConsoleLogger.PROPERTY_EXCEPTION_MAX_FRAMES, "0");
        ConsoleLogger console = new ConsoleLogger();
        console.useAgentDefaults();

        assertTrue(console.isShowDate());
        assertEquals(Collections.emptyList(), console.getExceptionExclude());
        assertEquals(0, console.getExceptionMaxFrames());
    }

    @Test
    public void testInvalidDisplaySettingsGiveTheDefaultsOfAgentModeAndAreReportedOnce() {
        System.setProperty(ConsoleLogger.PROPERTY_SHOW_DATE, "sometimes");
        System.setProperty(ConsoleLogger.PROPERTY_EXCEPTION_MAX_FRAMES, "many");
        ConsoleLogger console = new ConsoleLogger();
        getErrAndClear();
        console.useAgentDefaults();

        assertFalse(console.isShowDate());
        assertEquals(Agent.MAX_FRAMES, console.getExceptionMaxFrames());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testResetLevelsGoesBackToTheDefaultOfAgentMode() {
        ConsoleLogger console = new ConsoleLogger();
        console.useAgentDefaults();
        console.setLevel("TRACE");
        assertTrue(console.isTraceEnabled());

        console.resetLevels();

        assertFalse(console.isInfoEnabled());
        assertTrue(console.isWarnEnabled());
        assertTrue(new ConsoleLogger().setLevel("ERROR").resetLevels().isTraceEnabled(), "outside agent mode: TRACE");
    }

    // DBG

    @Test
    public void testDbgIgnoresTheLevelsInAgentMode() {
        LOG.init(new ConsoleLogger().setLevel("OFF"));
        Dbg.ignoreLevels = true;

        LOG.dbg("seen");
        LOG.dbg(1, 2);
        LOG.dbg();
        LOG.once().dbg("limited");
        LOG.once().dbg(3, 4);
        LOG.once().dbg();
        LOG.debug("not a dbg entry");

        String[] lines = getOutAndClear().split(NL);
        assertEquals(6, lines.length, Arrays.toString(lines));
        for (String line : lines) {
            assertTrue(line.startsWith("DBG "), line);
        }

        Dbg.ignoreLevels = false;
        LOG.dbg("hidden");
        LOG.atMostEvery(1, java.util.concurrent.TimeUnit.HOURS).dbg("hidden");
        assertEquals("", getOutAndClear());
    }

    @Test
    public void testACompositePassesDbgToEveryDelegateInAgentMode() {
        Recorder debug = new Recorder(true);
        Recorder quiet = new Recorder(false);
        LOG.init(new CompositeLogger(debug, quiet));

        LOG.dbg("before");
        assertEquals(Collections.singletonList("dbg"), debug.entries);
        assertEquals(Collections.emptyList(), quiet.entries, "outside agent mode dbg follows the levels");

        Dbg.ignoreLevels = true;
        LOG.dbg("after");
        LOG.debug("plain");
        LOG.warn("warning");

        assertEquals(Arrays.asList("dbg", "dbg", "DEBUG", "WARN"), debug.entries);
        assertEquals(Arrays.asList("dbg", "WARN"), quiet.entries);
    }

    private static final class Recorder implements ILogger {

        private final boolean debug;
        private final List<String> entries = new ArrayList<>();

        Recorder(boolean debug) {
            this.debug = debug;
        }

        @Override
        public void log(LogEntry entry) {
            entries.add(entry.isDbg() ? "dbg" : entry.getLevel().name());
        }

        @Override
        public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
            return debug || level.ordinal() >= WARN.ordinal();
        }
    }

    @Test
    public void testInitReadsAgentModeForDbgAndReportsAnInvalidSettingOnce() {
        System.setProperty(PROPERTY_RUN, "off");
        environment.put("CLAUDECODE", "1");
        LOG.init();
        assertTrue(Dbg.ignoreLevels);

        environment.clear();
        System.setProperty(PROPERTY_AGENT, "maybe");
        getErrAndClear();
        LOG.init();
        assertFalse(Dbg.ignoreLevels);
        assertEquals("[NANOLOG] Invalid value \"maybe\" of \"nanolog.agent\": expected auto, on or off; set as a system property" + NL, getErrAndClear());
    }

    // THE DEFAULT LOGGER

    @Test
    public void testWithoutAnAgentNothingChanges() {
        getErrAndClear();
        ConsoleLogger console = (ConsoleLogger) DefaultLoggerCreator.createDefaultLogger();

        assertTrue(console.isTraceEnabled());
        assertTrue(console.isShowDate());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testTheDefaultLoggerOfAgentModeWritesTheRunLogAndSaysSo() {
        environment.put("AI_AGENT", "claude-code");
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        getErrAndClear();

        List<ILogger> loggers = ((CompositeLogger) DefaultLoggerCreator.createDefaultLogger()).getLoggers();

        ConsoleLogger console = (ConsoleLogger) loggers.get(0);
        assertFalse(console.isInfoEnabled());
        RunLog runLog = (RunLog) loggers.get(1);
        assertTrue(runLog.isTraceEnabled(), "the file keeps every level");
        assertEquals("[NANOLOG] Agent mode (AI_AGENT=claude-code): the console shows WARN, ERROR and every LOG.dbg; " +
                     "the full log is in " + root.resolve("latest.log").toAbsolutePath() + ", WARN and ERROR also in " +
                     root.resolve("latest.errors.log").toAbsolutePath() + "; " + OFF_HINT + NL, getErrAndClear());

        DefaultLoggerCreator.createDefaultLogger();
        assertEquals("", getErrAndClear(), "once per JVM");
    }

    @Test
    public void testAgentModeWithoutTheRunLog() {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN, "off");
        getErrAndClear();

        ConsoleLogger console = (ConsoleLogger) DefaultLoggerCreator.createDefaultLogger();

        assertFalse(console.isInfoEnabled());
        assertEquals("[NANOLOG] Agent mode (CLAUDECODE=1): the console shows WARN, ERROR and every LOG.dbg; " + OFF_HINT + NL,
                     getErrAndClear());
    }

    @Test
    public void testTheHeaderSaysThatAnExplicitLevelIsKept() {
        System.setProperty(PROPERTY_AGENT, "on");
        System.setProperty(PROPERTY_RUN, "off");
        System.setProperty(PROPERTY_LEVEL, "INFO");
        getErrAndClear();

        ConsoleLogger console = (ConsoleLogger) DefaultLoggerCreator.createDefaultLogger();

        assertTrue(console.isInfoEnabled());
        assertEquals("[NANOLOG] Agent mode (nanolog.agent=on): the console keeps nanolog.level and shows every LOG.dbg; " +
                     OFF_HINT + NL, getErrAndClear());
    }

    @Test
    public void testALoggerOfItsOwnIsLeftAlone() {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN, "off");
        System.setProperty(PROPERTY_LOGGER_CLASS, MemoryLogger.class.getName());
        getErrAndClear();

        ILogger logger = DefaultLoggerCreator.createDefaultLogger();

        assertTrue(logger instanceof MemoryLogger, String.valueOf(logger));
        assertEquals("[NANOLOG] Agent mode (CLAUDECODE=1): " + OFF_HINT + NL, getErrAndClear());
    }

    @Test
    public void testTheRunLogIsOnByDefaultInAgentModeAndLeavesTheHeaderToIt() {
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        getErrAndClear();
        ILogger console = new ConsoleLogger();

        ILogger attached = RunLog.attach(console, true);

        assertSame(console, ((CompositeLogger) attached).getLoggers().get(0));
        assertSame(RunLog.current(), ((CompositeLogger) attached).getLoggers().get(1));
        assertEquals("", getErrAndClear());
        System.setProperty(PROPERTY_RUN, "off");
        assertSame(console, RunLog.attach(console, true));
    }
}
