package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.logging.Level;
import java.util.logging.Logger;

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
    public void testAgentModeCollapsesRepeatsUnlessSet() {
        try {
            ConsoleLogger console = new ConsoleLogger();
            console.useAgentDefaults();
            assertEquals("collapse", console.getRepeats());

            System.setProperty(ConsoleLogger.PROPERTY_REPEATS, "keep");
            console = new ConsoleLogger();
            console.useAgentDefaults();
            assertEquals("keep", console.getRepeats());

            System.setProperty(ConsoleLogger.PROPERTY_REPEATS, "squash");
            console = new ConsoleLogger();
            getErrAndClear();
            console.useAgentDefaults();
            assertEquals("collapse", console.getRepeats());
            assertEquals("", getErrAndClear(), "reported by the constructor already");
        } finally {
            System.clearProperty(ConsoleLogger.PROPERTY_REPEATS);
        }
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

        ILogger logger = DefaultLoggerCreator.createDefaultLogger();
        List<ILogger> loggers = ((CompositeLogger) logger).getLoggers();

        ConsoleLogger console = (ConsoleLogger) loggers.get(0);
        assertFalse(console.isInfoEnabled());
        RunLog runLog = (RunLog) loggers.get(1);
        assertTrue(runLog.isTraceEnabled(), "the file keeps every level");
        assertEquals("[NANOLOG] Agent mode (AI_AGENT=claude-code): the console shows WARN, ERROR and every LOG.dbg; " +
                     "the full log is in " + root.resolve("latest.log").toAbsolutePath() + ", WARN and ERROR also in " +
                     root.resolve("latest.errors.log").toAbsolutePath() + "; " + OFF_HINT + NL, noticeFor(logger));

        assertEquals("", noticeFor(DefaultLoggerCreator.createDefaultLogger()), "once per JVM");
    }

    /**
     * The notice of agent mode as the first entry through a logger prints it; nothing is printed before.
     */
    private String noticeFor(ILogger logger) {
        assertEquals("", getErrAndClear(), "nothing is printed before the first entry");
        Agent.announce(logger, null);
        return getErrAndClear();
    }

    @Test
    public void testTheNoticeIsPrintedWithTheFirstEntry() {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN, "off");
        LOG.init();
        assertEquals("", getErrAndClear(), "a run that logs nothing prints nothing");

        LOG.error("first");

        String err = getErrAndClear();
        assertTrue(err.startsWith("[NANOLOG] Agent mode (CLAUDECODE=1): the console shows WARN, ERROR and every LOG.dbg; " +
                                  OFF_HINT + NL) && err.endsWith("first" + NL), err);
        LOG.error("second");
        assertFalse(getErrAndClear().contains("Agent mode"), "once per JVM");
    }

    @Test
    public void testTheNoticeDescribesTheLoggerTheApplicationInstalled() {
        // the logger of the configuration is replaced before anything is logged (#110)
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN, "off");
        LOG.init();
        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.init(new ConsoleLogger());
        getErrAndClear();

        LOG.warn("first");

        assertEquals("[NANOLOG] Agent mode (CLAUDECODE=1): com.nanolaba.logging.ConsoleLogger installed at " +
                     "com.nanolaba.logging.AgentModeTest.testTheNoticeDescribesTheLoggerTheApplicationInstalled(AgentModeTest.java:" +
                     line + ") keeps its own levels and format; " + OFF_HINT + NL, getErrAndClear());
    }

    @Test
    public void testNoNoticeOnceAgentModeIsTurnedOffBeforeTheFirstEntry() {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN, "off");
        LOG.init();
        System.setProperty(PROPERTY_AGENT, "off");
        LOG.init();

        LOG.error("first");

        assertFalse(getErrAndClear().contains("Agent mode"));
    }

    @Test
    public void testAgentModeWithoutTheRunLog() {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN, "off");
        getErrAndClear();

        ConsoleLogger console = (ConsoleLogger) DefaultLoggerCreator.createDefaultLogger();

        assertFalse(console.isInfoEnabled());
        assertEquals("[NANOLOG] Agent mode (CLAUDECODE=1): the console shows WARN, ERROR and every LOG.dbg; " + OFF_HINT + NL,
                     noticeFor(console));
    }

    /**
     * The line of agent mode with {@code AI_AGENT} set to a value, without the run log.
     */
    private String lineFor(String value) {
        environment.put("AI_AGENT", value);
        System.setProperty(PROPERTY_RUN, "off");
        getErrAndClear();
        Agent.reset();
        return noticeFor(DefaultLoggerCreator.createDefaultLogger());
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    @Test
    public void testTheLineShowsAValueOfUpTo64CharactersWhole() {
        String value = repeat('x', Agent.MAX_VALUE);

        assertEquals("[NANOLOG] Agent mode (AI_AGENT=" + value + "): the console shows WARN, ERROR and every LOG.dbg; " +
                     OFF_HINT + NL, lineFor(value));
    }

    @Test
    public void testTheLineCutsALongerValue() {
        // anything in the process tree may set the variable, and the agent pays for every character of the line
        String start = repeat('x', Agent.MAX_VALUE);

        assertEquals("[NANOLOG] Agent mode (AI_AGENT=\"" + start + "... [+4936 chars]\"): the console shows WARN, ERROR " +
                     "and every LOG.dbg; " + OFF_HINT + NL, lineFor(repeat('x', 5000)));
        assertTrue(lineFor(start + "y").startsWith("[NANOLOG] Agent mode (AI_AGENT=\"" + start + "... [+1 char]\"): "));
    }

    @Test
    public void testTheCutDoesNotSplitACharacterOutsideTheBmp() {
        String start = repeat('x', Agent.MAX_VALUE - 1);

        assertTrue(lineFor(start + "\uD83E\uDD16y").startsWith("[NANOLOG] Agent mode (AI_AGENT=\"" + start + "... [+3 chars]\"): "),
                   "the robot face U+1F916 is two chars, and half of it would print as ?");
    }

    @Test
    public void testTheLineQuotesAValueThatWouldNotReadAsOne() {
        assertTrue(lineFor("my agent; nanolog.agent=on").startsWith(
                "[NANOLOG] Agent mode (AI_AGENT=\"my agent; nanolog.agent=on\"): the console shows "));
        assertTrue(lineFor("a\nb").startsWith("[NANOLOG] Agent mode (AI_AGENT=\"a\\u000ab\"): "), "still one line");
    }

    @Test
    public void testTheLineEscapesAFormatCharacterOfTheValue() {
        // U+202E reverses the rest of the line in a terminal that applies bidi
        assertTrue(lineFor("claude\u202Eedoc-").startsWith("[NANOLOG] Agent mode (AI_AGENT=claude\\u202eedoc-): "));
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
                     OFF_HINT + NL, noticeFor(console));
    }

    @Test
    public void testALoggerOfItsOwnIsLeftAlone() {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN, "off");
        System.setProperty(PROPERTY_LOGGER_CLASS, MemoryLogger.class.getName());
        getErrAndClear();

        ILogger logger = DefaultLoggerCreator.createDefaultLogger();

        assertTrue(logger instanceof MemoryLogger, String.valueOf(logger));
        assertEquals("[NANOLOG] Agent mode (CLAUDECODE=1): " + OFF_HINT + NL, noticeFor(logger));
    }

    @Test
    public void testTheRunLogIsOnByDefaultInAgentModeAndLeavesTheHeaderToIt() {
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        getErrAndClear();
        ILogger console = new ConsoleLogger();

        ILogger attached = RunLog.attach(console, true, "LOG.init()");

        assertSame(console, ((CompositeLogger) attached).getLoggers().get(0));
        assertSame(RunLog.current(), ((CompositeLogger) attached).getLoggers().get(1));
        assertEquals("", getErrAndClear());
        System.setProperty(PROPERTY_RUN, "off");
        assertSame(console, RunLog.attach(console, true, "LOG.init()"));
    }

    @Test
    public void testAToolTurnsAgentModeOffAfterLogHasInitialised() {
        // a hook of Claude Code, whose stderr the agent reads as the reason it refused an edit (#111)
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        LOG.init();
        System.setProperty(PROPERTY_AGENT, "off");
        LOG.init();
        ConsoleLogger own = new ConsoleLogger();
        LOG.init(own);
        getErrAndClear();

        LOG.error("Edit README.src.md instead");

        assertSame(own, LOG.getLogger(), "no run log");
        assertTrue(getErrAndClear().endsWith("Edit README.src.md instead" + NL));
        assertFalse(Files.exists(root.resolve("runs")), "no file either");
        Summary.printOnce();
        assertEquals("", getErrAndClear(), "the summary is off without agent mode");
    }

    @Test
    public void testARunThatLogsNothingWritesNothing() {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        LOG.init();

        Summary.printOnce();

        assertEquals("", getErrAndClear());
        assertFalse(Files.exists(root.resolve("runs")), "latest.log of the run before stays");
    }

    @Test
    public void testAgentModeTurnedOffInTheMiddleOfTheRunSaysSoAndKeepsTheSummary() throws IOException {
        // a test that sets nanolog.agent=off and does not put it back, before the LOG.init() of the next one (#115)
        environment.put("AI_AGENT", "claude-code");
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        LOG.init();
        Throwable first = new Throwable(); LOG.warn("entry 1, agent mode on");
        Throwable second = new Throwable(); LOG.error("entry 2, agent mode on");
        getErrAndClear();
        getOutAndClear();
        System.setProperty(PROPERTY_AGENT, "off");

        Throwable here = new Throwable(); LOG.init();
        String init = "LOG.init() at " + here.getStackTrace()[0];
        String stop = "[NANOLOG] Agent mode turned off by " + init + " (nanolog.agent=off): the run log ends here, after 2 entries";
        assertEquals(stop + NL, getErrAndClear());
        LOG.error("entry 3, after LOG.init()");
        LOG.init();
        getErrAndClear();

        Path latest = root.resolve("latest.log");
        List<String> log = Files.readAllLines(latest, StandardCharsets.UTF_8);
        assertEquals(3, log.size(), String.valueOf(log));
        assertEquals(stop, log.get(2), "whoever reads the file sees why it ends");
        Summary.printOnce();
        String site = "AgentModeTest.java:";
        assertEquals("[NANOLOG] Run summary until " + init + " turned it off: 2 entries: 1 ERROR, 1 WARN; the full log is in " +
                     latest.toAbsolutePath() + NL +
                     "[NANOLOG]   ERROR x1 at " + site + second.getStackTrace()[0].getLineNumber() + ": entry 2, agent mode on" + NL +
                     "[NANOLOG]   WARN x1 at " + site + first.getStackTrace()[0].getLineNumber() + ": entry 1, agent mode on" + NL,
                     getErrAndClear(), "the end of the run still sums up what the line of agent mode sent to the file");
    }

    // LEVELS WITH THE RUN LOG

    @Test
    public void testTheRunLogKeepsWhatTheShortConsoleLeavesOut() throws IOException {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        LOG.init();
        getErrAndClear();

        assertTrue(LOG.isInfoEnabled(), "the levels the console has without agent mode");
        LOG.info("only in the file");

        assertEquals("", getOutAndClear());
        String log = String.join(NL, Files.readAllLines(root.resolve("latest.log"), StandardCharsets.UTF_8));
        assertTrue(log.contains("only in the file"), log);
    }

    @Test
    public void testALoggerOfItsOwnKeepsItsLevelsWithTheRunLog() throws IOException {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        System.setProperty(PROPERTY_LOGGER_CLASS, JulLogger.class.getName());
        // a strong reference: java.util.logging forgets the level of a logger that is collected
        Logger jul = Logger.getLogger(AgentModeTest.class.getName());
        jul.setLevel(Level.INFO);
        jul.setUseParentHandlers(false);
        try {
            LOG.init();
            getErrAndClear();

            assertTrue(LOG.isInfoEnabled());
            assertFalse(LOG.isDebugEnabled(), "the levels of JUL, as without agent mode (#103)");
            LOG.info("in the file");
            int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
            LOG.dbg("seen");

            String out = getOutAndClear();
            assertEquals("DBG [AgentModeTest.testALoggerOfItsOwnKeepsItsLevelsWithTheRunLog(AgentModeTest.java:" + line + ")] " +
                         "\"seen\" = \"seen\"" + NL, out, "JUL would drop it (#105)");
            String log = String.join(NL, Files.readAllLines(root.resolve("latest.log"), StandardCharsets.UTF_8));
            assertTrue(log.contains("in the file") && log.contains("seen"), log);
        } finally {
            jul.setLevel(null);
            jul.setUseParentHandlers(true);
        }
    }
}
