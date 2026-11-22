package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run log: every entry of the run in a file at a predictable place, so an agent reads it instead of running again.
 */
class RunLogTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();
    private static final long MB = 1024 * 1024;

    @TempDir
    Path root;

    private final RunLog.Linker originalLinker = RunLog.linker;
    private final List<RunLog> opened = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        RunLog.reset();
    }

    @AfterEach
    public void tearDown() {
        RunLog.linker = originalLinker;
        for (RunLog log : opened) {
            log.close();
        }
        RunLog.reset();
        for (String property : new String[]{DefaultLoggerCreator.PROPERTY_RUN, DefaultLoggerCreator.PROPERTY_RUN_DIR,
                DefaultLoggerCreator.PROPERTY_RUN_FORMAT, DefaultLoggerCreator.PROPERTY_RUN_KEEP,
                DefaultLoggerCreator.PROPERTY_RUN_MAX_SIZE, DefaultLoggerCreator.PROPERTY_LEVEL, DefaultLoggerCreator.PROPERTY_AGENT}) {
            System.clearProperty(property);
        }
        LOG.init();
    }

    private RunLog open(String id) throws IOException {
        return open(id, false, 10, 100 * MB);
    }

    private RunLog open(String id, boolean json, int keep, long maxSize) throws IOException {
        RunLog log = RunLog.open(root, json, keep, maxSize, id);
        opened.add(log);
        return log;
    }

    private static LogEntry entry(LogEntry.LogEntryLevel level, String message, Throwable throwable) {
        return new LogEntry(level, RunLogTest.class, throwable, message);
    }

    private static List<String> lines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private List<String> runs() throws IOException {
        try (Stream<Path> files = Files.list(root.resolve("runs"))) {
            return files.map(file -> file.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    // WHERE

    @Test
    public void testTheDirectoryFollowsTheBuild() throws IOException {
        Path plain = Files.createDirectories(root.resolve("plain"));
        Path maven = Files.createDirectories(root.resolve("maven"));
        Files.createFile(maven.resolve("pom.xml"));
        Path both = Files.createDirectories(root.resolve("both"));
        Files.createFile(both.resolve("pom.xml"));
        Files.createFile(both.resolve("build.gradle"));

        assertEquals(plain.resolve(".nanolog"), RunLog.directory(null, plain));
        assertEquals(maven.resolve("target/nanolog"), RunLog.directory(null, maven));
        assertEquals(both.resolve("target/nanolog"), RunLog.directory(null, both), "Maven first");
        for (String file : new String[]{"build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"}) {
            Path gradle = Files.createDirectories(root.resolve("gradle-" + file));
            Files.createFile(gradle.resolve(file));
            assertEquals(gradle.resolve("build/nanolog"), RunLog.directory(null, gradle), file);
        }
    }

    @Test
    public void testAConfiguredDirectoryWins() {
        Path work = root.resolve("work");

        assertEquals(work.resolve("logs/run"), RunLog.directory(" logs/run ", work));
        assertEquals(root.resolve("elsewhere"), RunLog.directory(root.resolve("elsewhere").toString(), work));
        assertEquals(work.resolve(".nanolog"), RunLog.directory(" ", work), "a blank value is no value");
    }

    @Test
    public void testIdAndPid() {
        assertTrue(RunLog.id(0, "4567").matches("\\d{8}-\\d{6}-\\d{3}-4567"), RunLog.id(0, "4567"));
        assertTrue(RunLog.pid().matches("\\d+"), RunLog.pid());
    }

    @Test
    public void testARuntimeWithoutJavaManagementGetsARandomId() {
        // a jlink image of java.base alone, or a module that does not read java.management
        String id = RunLog.pid("com.acme.MissingManagementFactory");
        assertTrue(id.matches("r[0-9a-f]{4}"), id);
    }

    // FILES

    @Test
    public void testOpeningCreatesTheRunFilesAndTheLatestLinks() throws IOException {
        RunLog log = open("20261117-134435-123-4567");

        assertEquals(Arrays.asList("20261117-134435-123-4567.errors.log", "20261117-134435-123-4567.log"), runs());
        assertEquals(root.resolve("latest.log"), log.shownFile());
        assertEquals(root.resolve("latest.errors.log"), log.shownErrorsFile());

        log.log(entry(INFO, "hello", null));

        assertEquals(lines(root.resolve("runs/20261117-134435-123-4567.log")), lines(root.resolve("latest.log")),
                     "latest.log is the file of the run itself, written once");
        assertEquals(1, lines(root.resolve("latest.log")).size());
    }

    @Test
    public void testADirectoryCreatedForTheRunLogIsIgnoredByGit() throws IOException {
        Path directory = root.resolve("new/.nanolog");
        opened.add(RunLog.open(directory, false, 10, MB, "1"));
        assertEquals(Arrays.asList("*"), lines(directory.resolve(".gitignore")));

        Path existing = Files.createDirectories(root.resolve("existing"));
        opened.add(RunLog.open(existing, false, 10, MB, "1"));
        assertFalse(Files.exists(existing.resolve(".gitignore")), "a directory of the user is left alone");
    }

    @Test
    public void testWithoutHardLinksTheRunFilesArePrinted() throws IOException {
        RunLog.linker = (link, target) -> {
            throw new IOException("not supported");
        };

        RunLog log = open("1");

        assertEquals(root.resolve("runs/1.log"), log.shownFile());
        assertEquals(root.resolve("runs/1.errors.log"), log.shownErrorsFile());
        assertFalse(Files.exists(root.resolve("latest.log")));
    }

    @Test
    public void testTheLastRunsAreKept() throws IOException {
        for (int run = 1; run <= 5; run++) {
            open("2026-" + run, false, 3, MB).close();
        }

        assertEquals(Arrays.asList("2026-3.errors.log", "2026-3.log", "2026-4.errors.log", "2026-4.log",
                                   "2026-5.errors.log", "2026-5.log"), runs());
    }

    @Test
    public void testANewRunTakesLatestWhileTheOldOneIsStillWriting() throws IOException {
        RunLog first = open("1");
        first.log(entry(INFO, "first", null));

        RunLog second = open("2");
        second.log(entry(INFO, "second", null));
        first.log(entry(INFO, "first again", null));

        assertEquals(1, lines(root.resolve("latest.log")).size());
        assertTrue(lines(root.resolve("latest.log")).get(0).endsWith("second"));
        assertEquals(2, lines(root.resolve("runs/1.log")).size(), "the old run goes on in its own file");
    }

    @Test
    public void testARunWhoseLatestWasTakenNamesItsOwnFiles() throws IOException {
        // two forks of Surefire write the same directory, and latest.log is the file of the one that started last
        RunLog first = open("1");
        RunLog second = open("2");

        assertEquals(root.resolve("runs/1.log"), first.shownFile());
        assertEquals(root.resolve("runs/1.errors.log"), first.shownErrorsFile());
        assertEquals(root.resolve("latest.log"), second.shownFile());
        assertEquals(root.resolve("latest.errors.log"), second.shownErrorsFile());
    }

    @Test
    public void testARunWhoseLatestWasDeletedNamesItsOwnFiles() throws IOException {
        RunLog log = open("1");
        Files.delete(root.resolve("latest.log"));
        Files.delete(root.resolve("latest.errors.log"));

        assertEquals(root.resolve("runs/1.log"), log.shownFile());
        assertEquals(root.resolve("runs/1.errors.log"), log.shownErrorsFile());
    }

    // THE LIST OF THE RUN SUMMARY

    @Test
    public void testTheListOfTheSummaryIsWrittenNextToTheLogOfTheRun() throws IOException {
        RunLog log = open("1");
        List<String> kinds = Arrays.asList("ERROR x1 at A.java:1: a", "WARN x2 at B.java:2: b");

        assertEquals(root.resolve("latest.summary.log"), log.writeSummary(kinds));
        assertEquals(kinds, lines(root.resolve("runs/1.summary.log")));
        assertTrue(Files.isSameFile(root.resolve("latest.summary.log"), root.resolve("runs/1.summary.log")));
    }

    @Test
    public void testARunWhoseLatestWasTakenLeavesLatestSummaryToTheNewerRun() throws IOException {
        // latest.summary.log is the list of the run in latest.log, the one that started last (#99)
        RunLog first = open("1");
        RunLog second = open("2");

        assertEquals(root.resolve("runs/1.summary.log"), first.writeSummary(Arrays.asList("first")));
        assertFalse(Files.exists(root.resolve("latest.summary.log")));

        assertEquals(root.resolve("latest.summary.log"), second.writeSummary(Arrays.asList("second")));
        assertEquals(Arrays.asList("second"), lines(root.resolve("latest.summary.log")));
    }

    @Test
    public void testANewRunRemovesLatestSummaryOfTheRunBefore() throws IOException {
        open("1").writeSummary(Arrays.asList("first"));

        open("2");

        assertFalse(Files.exists(root.resolve("latest.summary.log")), "it would not be the list of the run in latest.log");
        assertEquals(Arrays.asList("first"), lines(root.resolve("runs/1.summary.log")));
    }

    @Test
    public void testWithoutHardLinksTheListIsNamedByItsFile() throws IOException {
        RunLog.linker = (link, target) -> {
            throw new IOException("not supported");
        };
        RunLog log = open("1");

        assertEquals(root.resolve("runs/1.summary.log"), log.writeSummary(Arrays.asList("one")));
        assertFalse(Files.exists(root.resolve("latest.summary.log")));
    }

    @Test
    public void testATestJvmOfABuildToolNamesItsOwnList() throws IOException {
        System.setProperty(COMMAND, "org.apache.maven.surefire.booter.ForkedBooter /home/me/shop/target/surefire " +
                                    "2026-11-19T17-16-05_286-jvmRun1 surefire-123tmp surefire_0-456tmp");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        RunLog.attach(new ConsoleLogger(), false, "LOG.init()");
        RunLog.current().start();
        getErrAndClear();

        Path shown = RunLog.current().writeSummary(Arrays.asList("one"));

        assertEquals(root.resolve("runs"), shown.getParent());
        assertTrue(shown.getFileName().toString().endsWith(".summary.log"), shown.toString());
        assertTrue(Files.isSameFile(root.resolve("latest.summary.log"), shown), "latest.log is still the log of this run");
    }

    @Test
    public void testAListThatCannotBeWrittenIsNotNamed() throws IOException {
        RunLog log = open("1");
        Files.createDirectory(root.resolve("runs/1.summary.log"));
        getErrAndClear();

        assertNull(log.writeSummary(Arrays.asList("one")));
        String printed = getErrAndClear();
        assertTrue(printed.startsWith("[NANOLOG] Cannot write the list of the run summary " + root.resolve("runs/1.summary.log") +
                                      ": "), printed);
        assertFalse(Files.exists(root.resolve("latest.summary.log")));
    }

    @Test
    public void testTheListsGoWithTheirRuns() throws IOException {
        for (int run = 1; run <= 5; run++) {
            RunLog log = open("2026-" + run, false, 3, MB);
            log.writeSummary(Arrays.asList("run " + run));
            log.close();
        }

        assertEquals(Arrays.asList("2026-3.errors.log", "2026-3.log", "2026-3.summary.log", "2026-4.errors.log", "2026-4.log",
                                   "2026-4.summary.log", "2026-5.errors.log", "2026-5.log", "2026-5.summary.log"), runs());
    }

    // WHAT IS WRITTEN

    @Test
    public void testTextShowsTheThreadAndTheCallSite() throws IOException {
        RunLog log = open("1");
        StackTraceElement here = new StackTraceElement(RunLogTest.class.getName(), "run", "RunLogTest.java", 42);

        log.log(LogEntry.create(DEBUG, RunLogTest.class, here, null, "found {}", new Object[]{3}));

        String line = lines(root.resolve("latest.log")).get(0);
        assertTrue(line.startsWith("DEBUG "), line);
        assertTrue(line.endsWith(" [" + Thread.currentThread().getName() + "] [RunLogTest.run(RunLogTest.java:42)] found 3"), line);
        assertEquals(0, lines(root.resolve("latest.errors.log")).size(), "DEBUG is not an error");
    }

    @Test
    public void testWarningsAndErrorsAlsoGoToTheErrorsFileOneLineEach() throws IOException {
        RunLog log = open("1");

        log.log(entry(INFO, "fine", null));
        log.log(entry(WARN, "slow", null));
        log.log(entry(ERROR, "failed\nbadly", new IllegalStateException("broken")));

        List<String> all = lines(root.resolve("latest.log"));
        assertTrue(all.size() > 4, "the stack trace is in the full log");
        assertTrue(all.stream().anyMatch(line -> line.startsWith("\tat ")), all.toString());
        List<String> errors = lines(root.resolve("latest.errors.log"));
        assertEquals(2, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith("WARN ") && errors.get(0).endsWith("slow"), errors.get(0));
        assertTrue(errors.get(1).startsWith("ERROR ") &&
                   errors.get(1).endsWith("failed\\nbadly java.lang.IllegalStateException: broken"), errors.get(1));
    }

    @Test
    public void testTheErrorsFileEscapesWhatWouldActInATerminal() throws IOException {
        // tail -f latest.errors.log would erase a line and move up for a value with ESC [2K ESC [1A (#113)
        RunLog log = open("1");
        String esc = String.valueOf((char) 0x1B);

        log.log(entry(WARN, "user " + esc + "[2K" + esc + "[1Afake line, admin" + MultilineTest.cp(0x202E) + "txt.exe", null));

        assertEquals(Collections.singletonList("user \\u001b[2K\\u001b[1Afake line, admin\\u202etxt.exe"),
                     lines(root.resolve("latest.errors.log")).stream().map(line -> line.substring(line.indexOf("user ")))
                             .collect(Collectors.toList()));
    }

    @Test
    public void testTheFullLogKeepsLineBreaksAndEscapesBidiControlsAndTags() throws IOException {
        // an agent reads the run log back: no line may read other than it holds, also with multiline=keep (#113)
        RunLog log = open("1");
        String esc = String.valueOf((char) 0x1B);

        log.log(entry(INFO, "one" + esc + "[31m" + MultilineTest.cp(0x2066) + "\ntwo" + MultilineTest.cp(0xE0041), null));

        List<String> all = lines(root.resolve("latest.log"));
        assertEquals(2, all.size(), all.toString());
        assertTrue(all.get(0).endsWith("one" + esc + "[31m\\u2066"), all.get(0));
        assertEquals("two\\udb40\\udc41", all.get(1));
    }

    @Test
    public void testAnEntryWithoutALevelIsNotAnError() throws IOException {
        RunLog log = open("1");

        log.log(entry(null, "no level", null));

        assertEquals(1, lines(root.resolve("latest.log")).size());
        assertEquals(0, lines(root.resolve("latest.errors.log")).size());
    }

    @Test
    public void testMistypedRunSettingsAreReported() {
        Map<String, String> system = new HashMap<>();
        system.put("nanolog.run.color", "red");
        system.put("nanolog.run.formatt", "json");

        assertEquals(Arrays.asList("Unknown setting \"nanolog.run.color\" (system property)",
                                   "Unknown setting \"nanolog.run.formatt\" (system property); did you mean \"nanolog.run.format\"?"),
                     Settings.findings(system, Collections.emptyMap(), Collections.emptyMap(), "file"));
    }

    @Test
    public void testJson() throws IOException {
        RunLog log = open("1", true, 10, MB);

        log.log(entry(INFO, "fine", null));
        log.log(entry(ERROR, "failed", new IllegalStateException("broken")));

        List<String> all = lines(root.resolve("latest.log"));
        assertEquals(2, all.size());
        assertTrue(all.get(0).startsWith("{\"ts\":") && all.get(0).contains("\"level\":\"INFO\"") && all.get(0).endsWith("}"), all.get(0));
        List<String> errors = lines(root.resolve("latest.errors.log"));
        assertEquals(Arrays.asList(all.get(1)), errors);
        assertTrue(errors.get(0).contains("\"stackTrace\":"), errors.get(0));
    }

    @Test
    public void testTheLevelsAreThoseOfTheSettings() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_LEVEL, "INFO");

        RunLog log = open("1");

        assertFalse(log.isEnabled(DEBUG, RunLogTest.class));
        assertTrue(log.isEnabled(INFO, RunLogTest.class));
    }

    @Test
    public void testCreatingTheRunLogRepeatsNoDiagnosticOfTheConsoleSettings() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_LEVEL, "LOUD");
        getErrAndClear();

        open("1", true, 10, MB);

        assertEquals("", getErrAndClear());
        DefaultLoggerCreator.printDiagnostic("after");
        assertEquals("[NANOLOG] after" + NL, getErrAndClear());
    }

    // LIMITS AND FAILURES

    @Test
    public void testAFileStopsAtItsSizeWithANote() throws IOException {
        RunLog log = open("1", false, 10, 200);

        for (int i = 0; i < 10; i++) {
            log.log(entry(WARN, "line number " + i, null));
        }

        for (String file : new String[]{"latest.log", "latest.errors.log"}) {
            List<String> lines = lines(root.resolve(file));
            assertEquals("[NANOLOG] The run log reached nanolog.run.maxSize; later entries are not written", lines.get(lines.size() - 1));
            assertTrue(lines.size() < 10, lines.toString());
            assertTrue(Files.size(root.resolve(file)) <= 200 + lines.get(lines.size() - 1).length() + NL.length());
            assertTrue(lines.get(lines.size() - 2).endsWith("line number " + (lines.size() - 2)), "the head of the run is kept");
        }
    }

    @Test
    public void testAJsonFileStopsWithAJsonNote() throws IOException {
        RunLog log = open("1", true, 10, 10);

        log.log(entry(ERROR, "too long", null));
        log.log(entry(ERROR, "skipped", null));

        assertEquals(Arrays.asList("{\"nanolog\":\"The run log reached nanolog.run.maxSize; later entries are not written\"}"),
                     lines(root.resolve("latest.log")));
    }

    @Test
    public void testAnEntryThatFitsExactlyIsWritten() throws IOException {
        RunLog probe = open("probe", true, 10, MB);
        probe.log(entry(INFO, "x", null));
        long size = Files.size(root.resolve("runs/probe.log"));

        RunLog log = open("1", true, 10, size);
        log.log(entry(INFO, "x", null));
        log.log(entry(INFO, "x", null));

        List<String> lines = lines(root.resolve("latest.log"));
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).startsWith("{\"nanolog\":"), lines.toString());
    }

    @Test
    public void testAnIoErrorTurnsTheRunLogOffWithOneDiagnostic() {
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        RunLog log = DefaultLoggerCreator.quietly(() -> new RunLog(root.resolve("run.log"), broken, root.resolve("errors.log"), errors, false, MB));
        getErrAndClear();

        log.log(entry(ERROR, "first", null));
        log.log(entry(ERROR, "second", null));

        assertEquals("[NANOLOG] Cannot write the run log " + root.resolve("run.log") + ": java.io.IOException: disk full; " +
                     "it is off for the rest of the run" + NL, getErrAndClear());
        assertEquals(0, errors.size(), "the errors file stops together with the run log");
    }

    @Test
    public void testAFailingErrorsFileTurnsTheRunLogOffToo() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };
        RunLog log = DefaultLoggerCreator.quietly(() -> new RunLog(root.resolve("run.log"), out, root.resolve("errors.log"), broken, false, MB));
        getErrAndClear();

        log.log(entry(ERROR, "first", null));
        int size = out.size();
        log.log(entry(ERROR, "second", null));

        assertEquals(size, out.size());
        assertTrue(getErrAndClear().startsWith("[NANOLOG] Cannot write the run log " + root.resolve("errors.log") + ": "));
    }

    /**
     * Records whether it was closed; {@code failing} also makes closing fail.
     */
    private static final class Closing extends ByteArrayOutputStream {

        private final boolean failing;
        boolean closed;

        Closing(boolean failing) {
            this.failing = failing;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (failing) {
                throw new IOException("already gone");
            }
        }
    }

    @Test
    public void testClosingClosesBothFilesAndStopsWriting() {
        Closing out = new Closing(false);
        Closing errors = new Closing(true);
        RunLog log = DefaultLoggerCreator.quietly(() -> new RunLog(root.resolve("run.log"), out, root.resolve("errors.log"), errors, false, MB));

        log.close();
        log.log(entry(ERROR, "after", null));

        assertTrue(out.closed);
        assertTrue(errors.closed);
        assertEquals(0, out.size() + errors.size());
        assertEquals("", getErrAndClear(), "a file that fails to close is gone anyway");
    }

    // SWITCHING IT ON

    @Test
    public void testResetClosesTheRunLogOfTheJvm() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        RunLog runLog = (RunLog) ((CompositeLogger) RunLog.attach(new ConsoleLogger(), false, "LOG.init()")).getLoggers().get(1);
        runLog.start();

        RunLog.reset();
        runLog.log(entry(INFO, "after reset", null));

        assertEquals(0, lines(root.resolve("latest.log")).size());
    }

    @Test
    public void testTheFilesAreOpenedWithTheFirstEntry() throws IOException {
        // a run that logs nothing leaves latest.log to the run before (#111)
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        getErrAndClear();

        ILogger attached = RunLog.attach(new ConsoleLogger(), false, "LOG.init()");

        assertEquals("", getErrAndClear());
        assertFalse(Files.exists(root.resolve("runs")));
        assertNull(RunLog.opened(), "nothing names files that do not exist");
        attached.log(entry(INFO, "first", null));
        assertEquals("[NANOLOG] Run log: " + root.resolve("latest.log").toAbsolutePath() + "; WARN and ERROR also in " +
                     root.resolve("latest.errors.log").toAbsolutePath() + NL, getErrAndClear());
        assertSame(RunLog.current(), RunLog.opened());
        assertEquals(1, lines(root.resolve("latest.log")).size());
    }

    @Test
    public void testALaterInitThatTurnsItOffStopsIt() throws IOException {
        // a tool sets nanolog.agent=off and calls LOG.init() before it logs (#111)
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        ILogger console = new ConsoleLogger();
        ILogger attached = RunLog.attach(console, false, "LOG.init()");

        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "off");
        assertSame(console, RunLog.attach(console, false, "LOG.init()"));

        assertNull(RunLog.current());
        assertSame(console, RunLog.with(console), "a logger installed later gets no run log");
        attached.log(entry(INFO, "after", null));
        assertFalse(Files.exists(root.resolve("runs")), "the run log stopped before it opened a file");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        assertSame(console, RunLog.attach(console, false, "LOG.init()"), "for the rest of the JVM");
        assertEquals("", getErrAndClear(), "no file, nothing to say about it");
    }

    @Test
    public void testARunLogStoppedAfterItsEntriesEndsWithALineThatSaysWhy() throws IOException {
        // read back, a file that just stops looks like a run that crashed or hung there (#115)
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        ILogger console = new ConsoleLogger();
        ILogger attached = RunLog.attach(console, false, "LOG.init() at com.acme.Tool.main(Tool.java:6)");
        attached.log(entry(INFO, "first", null));
        attached.log(entry(WARN, "second", null));
        getErrAndClear();
        getOutAndClear();

        System.clearProperty(DefaultLoggerCreator.PROPERTY_RUN);
        assertSame(console, RunLog.attach(console, false, "LOG.init() at com.acme.Tool.main(Tool.java:9)"));

        String line = "Run log turned off by LOG.init() at com.acme.Tool.main(Tool.java:9) (nanolog.run=off): it ends here, " +
                      "after 2 entries";
        assertEquals("[NANOLOG] " + line + NL, getErrAndClear());
        List<String> log = lines(root.resolve("latest.log"));
        assertEquals(3, log.size(), String.valueOf(log));
        assertEquals("[NANOLOG] " + line, log.get(2));
        List<String> errors = lines(root.resolve("latest.errors.log"));
        assertEquals(2, errors.size(), "the errors file is followed with tail -f: " + errors);
        assertEquals("[NANOLOG] " + line, errors.get(1));
        attached.log(entry(ERROR, "after", null));
        assertEquals(3, lines(root.resolve("latest.log")).size(), "nothing follows it");
    }

    @Test
    public void testTheLineCountsOneEntry() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        RunLog.attach(new ConsoleLogger(), false, "LOG.init()").log(entry(INFO, "only", null));
        getErrAndClear();

        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "off");
        RunLog.attach(new ConsoleLogger(), false, "LOG.init()");

        assertEquals("[NANOLOG] Run log turned off by LOG.init() (nanolog.run=off): it ends here, after 1 entry" + NL,
                     getErrAndClear());
    }

    @Test
    public void testAJsonRunLogEndsWithALineOfJson() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_FORMAT, "json");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        RunLog.attach(new ConsoleLogger(), false, "LOG.init() at \"Tool\"").log(entry(WARN, "only", null));
        getErrAndClear();

        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "off");
        RunLog.attach(new ConsoleLogger(), false, "LOG.init() at \"Tool\"");

        String line = "{\"nanolog\":\"Run log turned off by LOG.init() at \\\"Tool\\\" (nanolog.run=off): it ends here, after 1 entry\"}";
        List<String> log = lines(root.resolve("latest.log"));
        assertEquals(line, log.get(log.size() - 1));
        List<String> errors = lines(root.resolve("latest.errors.log"));
        assertEquals(line, errors.get(errors.size() - 1));
    }

    @Test
    public void testTheLineEscapesWhatItEchoes() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        RunLog.attach(new ConsoleLogger(), false, "LOG.init()").log(entry(INFO, "only", null));
        getErrAndClear();

        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "off");
        RunLog.attach(new ConsoleLogger(), false, "LOG.init() at Tool.main(Tool" + (char) 0x202E + ".java:6)");

        String line = "[NANOLOG] Run log turned off by LOG.init() at Tool.main(Tool\\u202e.java:6) (nanolog.run=off): it ends " +
                      "here, after 1 entry";
        assertEquals(line + NL, getErrAndClear());
        List<String> log = lines(root.resolve("latest.log"));
        assertEquals(line, log.get(log.size() - 1));
    }

    @Test
    public void testARunLogClosedByAnErrorSaysNothingMore() throws IOException {
        // it said that it is off when it failed
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        RunLog.attach(new ConsoleLogger(), false, "LOG.init()").log(entry(INFO, "only", null));
        RunLog.current().close();
        getErrAndClear();

        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "off");
        RunLog.attach(new ConsoleLogger(), false, "LOG.init()");

        assertEquals("", getErrAndClear());
        assertEquals(1, lines(root.resolve("latest.log")).size());
    }

    @Test
    public void testAgentModeTurnedOffIsNamedAsTheReason() throws IOException {
        // the line of agent mode named the run log, and nanolog.agent=off is what the agent was told turns it off
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        RunLog.attach(new ConsoleLogger(), true, "LOG.init()").log(entry(WARN, "only", null));
        getErrAndClear();
        System.setProperty(DefaultLoggerCreator.PROPERTY_AGENT, "off");

        RunLog.attach(new ConsoleLogger(), false, "LOG.init() at com.acme.Tool.main(Tool.java:6)");

        String line = "[NANOLOG] Agent mode turned off by LOG.init() at com.acme.Tool.main(Tool.java:6) (nanolog.agent=off): " +
                      "the run log ends here, after 1 entry";
        assertEquals(line + NL, getErrAndClear());
        List<String> log = lines(root.resolve("latest.log"));
        assertEquals(line, log.get(log.size() - 1));
    }

    @Test
    public void testNanologRunOffIsTheReasonEvenWhenAgentModeEnds() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        RunLog.attach(new ConsoleLogger(), true, "LOG.init()").log(entry(WARN, "only", null));
        getErrAndClear();
        System.setProperty(DefaultLoggerCreator.PROPERTY_AGENT, "off");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "off");

        RunLog.attach(new ConsoleLogger(), false, "LOG.init()");

        assertEquals("[NANOLOG] Run log turned off by LOG.init() (nanolog.run=off): it ends here, after 1 entry" + NL,
                     getErrAndClear());
    }

    @Test
    public void testAgentModeThatEndedBeforeIsNotTheReason() throws IOException {
        // nanolog.run=on kept the run log when agent mode ended; it stops when that setting goes
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        RunLog.attach(new ConsoleLogger(), true, "LOG.init()").log(entry(WARN, "only", null));
        System.setProperty(DefaultLoggerCreator.PROPERTY_AGENT, "off");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        RunLog.attach(new ConsoleLogger(), false, "LOG.init()");
        getErrAndClear();
        System.clearProperty(DefaultLoggerCreator.PROPERTY_RUN);

        RunLog.attach(new ConsoleLogger(), false, "LOG.init()");

        assertEquals("[NANOLOG] Run log turned off by LOG.init() (nanolog.run=off): it ends here, after 1 entry" + NL,
                     getErrAndClear());
    }

    @Test
    public void testOffByDefault() {
        ILogger console = new ConsoleLogger();

        assertSame(console, RunLog.attach(console, false, "LOG.init()"));
        assertFalse(Files.exists(root.resolve("latest.log")));
    }

    @Test
    public void testOnAddsTheRunLogToTheLoggerOncePerJvm() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        getErrAndClear();

        ILogger console = new ConsoleLogger();
        ILogger first = RunLog.attach(console, false, "LOG.init()");
        RunLog.current().start();

        assertEquals("[NANOLOG] Run log: " + root.resolve("latest.log").toAbsolutePath() + "; WARN and ERROR also in " +
                     root.resolve("latest.errors.log").toAbsolutePath() + NL, getErrAndClear());
        List<ILogger> loggers = ((CompositeLogger) first).getLoggers();
        assertSame(console, loggers.get(0));
        RunLog runLog = (RunLog) loggers.get(1);

        ILogger again = RunLog.attach(console, false, "LOG.init()");
        assertSame(runLog, ((CompositeLogger) again).getLoggers().get(1), "LOG.init() keeps writing the same file");
        assertEquals("", getErrAndClear());
        assertEquals(2, runs().size());

        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "off");
        assertSame(console, RunLog.attach(console, false, "LOG.init()"));
    }

    @Test
    public void testATestJvmOfABuildToolIsKnownByItsCommand() {
        assertTrue(RunLog.testWorker("org.apache.maven.surefire.booter.ForkedBooter /home/me/shop/target/surefire " +
                                     "2026-11-19T17-16-05_286-jvmRun1 surefire-123tmp surefire_0-456tmp"));
        // the manifest-only jar of Surefire, in a directory with a space
        assertTrue(RunLog.testWorker("C:\\Users\\Jo Smith\\shop\\target\\surefire\\surefirebooter-20261119171605286_3.jar " +
                                     "C:\\Users\\Jo Smith\\shop\\target\\surefire 2026-11-19T17-16-05_286-jvmRun1 " +
                                     "surefire-123tmp surefire_0-456tmp"));
        assertTrue(RunLog.testWorker("/home/me/shop/target/surefire/surefirebooter1234.jar"));
        assertTrue(RunLog.testWorker("worker.org.gradle.process.internal.worker.GradleWorkerMain 'Gradle Test Executor 1'"));

        assertFalse(RunLog.testWorker(null));
        assertFalse(RunLog.testWorker(""));
        assertFalse(RunLog.testWorker("com.acme.shop.Main"));
        assertFalse(RunLog.testWorker("/home/me/shop/target/shop.jar --port 8080"));
        assertFalse(RunLog.testWorker("/home/me/shop/target/surefirebooter.jar.bak"));
        assertFalse(RunLog.testWorker("com.intellij.rt.junit.JUnitStarter -ideVersion5 -junit5 com.acme.shop.OrderServiceTest"));
    }

    @Test
    public void testATestJvmOfABuildToolNamesItsOwnFilesFromTheStart() throws IOException {
        // several forks may run in one module, side by side or one after another, and each takes latest.log (#96)
        System.setProperty(COMMAND, "org.apache.maven.surefire.booter.ForkedBooter /home/me/shop/target/surefire " +
                                    "2026-11-19T17-16-05_286-jvmRun1 surefire-123tmp surefire_0-456tmp");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        getErrAndClear();

        RunLog.attach(new ConsoleLogger(), false, "LOG.init()");
        RunLog.current().start();

        // runs() sorts the names: <id>.errors.log, then <id>.log
        Path ownErrors = root.resolve("runs").resolve(runs().get(0)).toAbsolutePath();
        Path own = root.resolve("runs").resolve(runs().get(1)).toAbsolutePath();
        assertEquals("[NANOLOG] Run log: " + own + "; WARN and ERROR also in " + ownErrors + NL, getErrAndClear());
        assertTrue(Files.isSameFile(root.resolve("latest.log"), own), "latest.log is the newest run still");
        assertTrue(Files.isSameFile(root.resolve("latest.errors.log"), ownErrors));
    }

    @Test
    public void testLogWritesThroughTheRunLog() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, " ON ");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_FORMAT, "json");
        LOG.init();
        LOG.init(new CompositeLogger(new ConsoleLogger().setDebugEnabled(false), ((CompositeLogger) LOG.getLogger()).getLoggers().get(1)));

        LOG.debug("only in the file");

        assertEquals("", getOutAndClear(), "the console has DEBUG off");
        List<String> lines = lines(root.resolve("latest.log"));
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"message\":\"only in the file\""), lines.get(0));
    }

    private void runLogOn() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        LOG.init();
        getErrAndClear();
    }

    @Test
    public void testTheRunLogGoesOnAfterTheApplicationInstallsItsOwnLogger() throws IOException {
        runLogOn();
        LOG.info("before LOG.init");
        MemoryLogger application = new MemoryLogger();

        LOG.init(application);
        LOG.info("after LOG.init");
        LOG.warn("warning after LOG.init");

        List<String> log = lines(root.resolve("latest.log"));
        assertEquals(3, log.size(), String.join(NL, log));
        assertTrue(log.get(2).contains("warning after LOG.init"), log.get(2));
        assertEquals(1, lines(root.resolve("latest.errors.log")).size());
        assertEquals(2, application.getEntries().size(), "the logger of the application gets every entry as before");
    }

    @Test
    public void testALoggerThatWritesTheRunLogAlreadyIsInstalledAsItIs() throws IOException {
        runLogOn();
        ILogger withRunLog = LOG.getLogger();
        LOG.init(new MemoryLogger());

        LOG.init(withRunLog);
        LOG.info("once");

        assertSame(withRunLog, LOG.getLogger());
        assertEquals(1, lines(root.resolve("latest.log")).size(), "not written twice");
    }

    @Test
    public void testADecoratorOfTheInstalledLoggerWritesTheRunLogOnce() throws IOException {
        runLogOn();
        getOutAndClear();
        // the usual decorator: LOG.getLogger() writes the run log already, and LOG.init adds it to the decorator (#95)
        ILogger previous = LOG.getLogger();
        List<LogEntry> seen = new ArrayList<>();
        LOG.init(entry -> {
            seen.add(entry);
            previous.log(entry);
        });

        LOG.info("while the decorator is installed");
        LOG.warn("a warning");
        LOG.init(previous);
        LOG.info("after the decorator");

        List<String> log = lines(root.resolve("latest.log"));
        assertEquals(3, log.size(), String.join(NL, log));
        assertEquals(1, lines(root.resolve("latest.errors.log")).size());
        assertEquals(2, seen.size());
        String out = getOutAndClear() + getErrAndClear();
        assertEquals(1, out.split("while the decorator is installed", -1).length - 1, out);
    }

    @Test
    public void testAForwardingMemoryLoggerWritesTheRunLogOnce() throws IOException {
        runLogOn();

        try (MemoryLogger memory = MemoryLogger.installForwarding()) {
            LOG.info("captured and forwarded");
            assertEquals(1, memory.getEntries().size());
        }

        List<String> log = lines(root.resolve("latest.log"));
        assertEquals(1, log.size(), String.join(NL, log));
    }

    @Test
    public void testAnEntryLoggedTwiceIsWrittenTwice() throws IOException {
        runLogOn();
        LogEntry entry = entry(INFO, "twice", null);

        LOG.getLogger().log(entry);
        LOG.getLogger().log(entry);

        assertEquals(2, lines(root.resolve("latest.log")).size());
    }

    @Test
    public void testANullLoggerIsInstalledAsItIs() {
        runLogOn();

        LOG.init(null);

        assertEquals(null, LOG.getLogger(), "as without the run log");
        LOG.init();
    }

    @Test
    public void testTheEntriesOfAnInstalledMemoryLoggerGoToTheRunLogToo() throws IOException {
        runLogOn();

        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.info("captured");
            assertEquals(1, memory.getEntries().size());
        }
        LOG.info("after the test");

        List<String> log = lines(root.resolve("latest.log"));
        assertEquals(2, log.size(), String.join(NL, log));
        assertTrue(log.get(0).contains("captured"), log.get(0));
    }

    /**
     * A logger with levels of its own: WARN and above for every class, and INFO the lowest level it may take.
     */
    private static final class Leveled implements ILogger {

        final List<LogEntry> entries = new ArrayList<>();
        int asked;

        @Override
        public void log(LogEntry entry) {
            entries.add(entry);
        }

        @Override
        public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
            asked++;
            return level.ordinal() >= WARN.ordinal();
        }

        @Override
        public boolean mayBeEnabled(LogEntry.LogEntryLevel level) {
            return level.ordinal() >= INFO.ordinal();
        }
    }

    @Test
    public void testAnInstalledLoggerKeepsItsLevelsWithTheRunLog() throws IOException {
        runLogOn();
        Leveled application = new Leveled();
        AtomicInteger built = new AtomicInteger();

        LOG.init(application);

        assertFalse(LOG.isDebugEnabled());
        assertEquals(0, application.asked, "a level the logger never takes walks no stack");
        assertFalse(LOG.isInfoEnabled(), "the run log takes every level, the logger does not (#103)");
        assertFalse(LOG.isInfoEnabled(RunLogTest.class));
        assertTrue(LOG.isWarnEnabled());
        LOG.info("not built {}", LOG.lazy(built::incrementAndGet));
        assertEquals(0, built.get(), "nor is a lazy argument");
        LOG.warn("taken");

        assertEquals(1, application.entries.size());
        List<String> log = lines(root.resolve("latest.log"));
        assertEquals(1, log.size(), String.join(NL, log));
        assertTrue(log.get(0).contains("taken"), log.get(0));
    }

    @Test
    public void testWithoutAgentModeTheRunLogFollowsTheLevelsOfTheConsole() throws IOException {
        runLogOn();
        ((ConsoleLogger) ((CompositeLogger) LOG.getLogger()).getLoggers().get(0)).setDebugEnabled(false);
        getOutAndClear();

        assertFalse(LOG.isDebugEnabled());
        LOG.debug("not logged");
        LOG.info("logged");

        List<String> log = lines(root.resolve("latest.log"));
        assertEquals(1, log.size(), String.join(NL, log));
        assertTrue(log.get(0).contains("logged"), log.get(0));
    }

    @Test
    public void testAReplacementNamesTheLoggersTheCodeInstalled() {
        runLogOn();
        ConsoleLogger console = new ConsoleLogger();
        LOG.init(console);

        org.example.vendor.Sdk.install(new JsonLogger());
        String err = getErrAndClear();
        assertTrue(err.startsWith("[NANOLOG] Logger replaced: " + ConsoleLogger.class.getName() + " (installed at "), err);
        assertTrue(err.contains(" -> " + JsonLogger.class.getName() + " (installed at "), err);

        org.example.vendor.Sdk.install(console);
        getErrAndClear();
        LOG.init(console);
        assertEquals("", getErrAndClear(), "the same logger again");
    }

    @Test
    public void testTheSettingsOfTheRunLog() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_KEEP, "1");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_MAX_SIZE, "1");
        Files.createDirectories(root.resolve("runs"));
        Files.createFile(root.resolve("runs/0-old.log"));

        RunLog runLog = (RunLog) ((CompositeLogger) RunLog.attach(new ConsoleLogger(), false, "LOG.init()")).getLoggers().get(1);
        opened.add(runLog);
        runLog.start();

        assertEquals(2, runs().size(), "keep 1: only this run");
        assertEquals(MB, runLog.maxSize());
        assertFalse(runLog.isJson());
    }

    @Test
    public void testInvalidSettingsAreReportedAndGiveTheDefaults() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "yes");
        assertFalse(DefaultLoggerCreator.readChoice(DefaultLoggerCreator.PROPERTY_RUN, "off", "on").equals("on"));
        assertEquals("[NANOLOG] Invalid value \"yes\" of \"nanolog.run\": expected off or on; set as a system property" + NL, getErrAndClear());

        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_FORMAT, "xml");
        assertEquals("text", DefaultLoggerCreator.readChoice(DefaultLoggerCreator.PROPERTY_RUN_FORMAT, "text", "json"));
        assertEquals("[NANOLOG] Invalid value \"xml\" of \"nanolog.run.format\": expected text or json; set as a system property" + NL, getErrAndClear());
        assertEquals("on", DefaultLoggerCreator.readChoice("nanolog.test.choice", "on", "off", "fail"));
        System.setProperty("nanolog.test.choice", "x");
        assertEquals("on", DefaultLoggerCreator.readChoice("nanolog.test.choice", "on", "off", "fail"));
        assertEquals("[NANOLOG] Invalid value \"x\" of \"nanolog.test.choice\": expected on, off or fail; set as a system property" + NL, getErrAndClear());
        System.clearProperty("nanolog.test.choice");

        for (String value : new String[]{"0", "-1", "ten", ""}) {
            System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_KEEP, value);
            assertEquals(10, DefaultLoggerCreator.readPositive(DefaultLoggerCreator.PROPERTY_RUN_KEEP, 10));
            assertEquals("[NANOLOG] Invalid value \"" + value + "\" of \"nanolog.run.keep\": expected a positive number; set as a system property" + NL,
                         getErrAndClear());
        }
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_KEEP, " 3 ");
        assertEquals(3, DefaultLoggerCreator.readPositive(DefaultLoggerCreator.PROPERTY_RUN_KEEP, 10));
        System.clearProperty(DefaultLoggerCreator.PROPERTY_RUN_KEEP);
        assertEquals(10, DefaultLoggerCreator.readPositive(DefaultLoggerCreator.PROPERTY_RUN_KEEP, 10));
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testADirectoryThatCannotBeCreatedLeavesTheLoggerAlone() throws IOException {
        Path file = Files.createFile(root.resolve("not-a-directory"));
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, file.toString());
        getErrAndClear();

        ILogger console = new ConsoleLogger();
        ILogger attached = RunLog.attach(console, false, "LOG.init()");
        assertEquals("", getErrAndClear(), "nothing is opened before the first entry");

        attached.log(entry(INFO, "first", null));

        String err = getErrAndClear();
        assertTrue(err.startsWith("[NANOLOG] Cannot open the run log in " + file.toAbsolutePath() + ": "), err);
        assertTrue(err.endsWith("; it is off" + NL), err);
        assertNull(RunLog.started());
        assertSame(console, RunLog.attach(console, false, "LOG.init()"), "not retried in the same JVM");
        assertSame(console, RunLog.with(console));
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testQuietlyRestoresTheDiagnostics() {
        assertEquals("x", DefaultLoggerCreator.quietly(() -> {
            DefaultLoggerCreator.printDiagnostic("muted");
            return "x";
        }));
        assertThrows(IllegalStateException.class, () -> DefaultLoggerCreator.quietly(() -> {
            throw new IllegalStateException();
        }));
        DefaultLoggerCreator.printDiagnostic("heard");

        assertEquals("[NANOLOG] heard" + NL, getErrAndClear());
    }
}
