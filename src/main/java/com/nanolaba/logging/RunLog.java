package com.nanolaba.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static com.nanolaba.logging.DefaultLoggerCreator.*;

/**
 * The run log, {@code nanolog.run=on}: every entry of the run also goes to a file at a predictable place, so a coding
 * agent reads the output of the last run again instead of running it again. Warnings and errors go to a second file as
 * well, one line each.
 * <p>
 * Each run writes {@code runs/<time>-<pid>.log} and {@code runs/<time>-<pid>.errors.log} in the directory;
 * {@code latest.log} and {@code latest.errors.log} are hard links to them, so they are written once and show the run
 * live. A run names {@code latest.log} while it is a name of its file, and its own file once a later run in the same
 * directory has taken {@code latest.log} (#93); a test JVM of Maven or Gradle names its own file from the start, since
 * another fork of the same module may take {@code latest.log} at any moment (#96). At the end of the run the run
 * summary writes every kind of warning and error to {@code runs/<time>-<pid>.summary.log}, with
 * {@code latest.summary.log} as a second name while {@code latest.log} is the log of the run (#99). The last
 * {@code nanolog.run.keep} runs are kept. Each file stops at {@code nanolog.run.maxSize} with a note.
 * <p>
 * The run log is a {@link ConsoleLogger} with its own levels, read from the same settings, so a console kept short
 * leaves the file complete. Text lines show the thread and the call site. The files are opened once per JVM, with the
 * first entry, so a run that logs nothing leaves the files of the run before alone (#111), and are written without a
 * buffer, so what was logged is on disk even if the JVM is killed. An I/O error prints one diagnostic and turns the
 * run log off; logging goes on.
 */
final class RunLog extends ConsoleLogger {

    static final String LATEST = "latest.log";
    static final String LATEST_ERRORS = "latest.errors.log";
    static final String RUNS = "runs";
    static final String LOG_SUFFIX = ".log";
    static final String ERRORS_SUFFIX = ".errors.log";
    static final String LATEST_SUMMARY = "latest.summary.log";
    static final String SUMMARY_SUFFIX = ".summary.log";

    private static final String[] GRADLE_FILES = {"build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"};
    private static final String TRUNCATED = "The run log reached " + PROPERTY_RUN_MAX_SIZE + "; later entries are not written";
    private static final long MB = 1024 * 1024;
    private static final String SUREFIRE_BOOTER = "org.apache.maven.surefire.booter.ForkedBooter";
    private static final String GRADLE_WORKER = "worker.org.gradle.process.internal.worker.GradleWorkerMain";
    // the manifest-only jar of Surefire, target/surefire/surefirebooter-20261119171605286_3.jar, and its arguments
    private static final Pattern SUREFIRE_JAR = Pattern.compile("(?s).*[\\\\/]surefirebooter[^\\\\/]*\\.jar(\\s.*)?");

    /**
     * Creates {@code latest.log} as a second name of the file of the run. Tests replace it to make it fail and must
     * restore it afterwards.
     */
    interface Linker {

        void link(Path link, Path target) throws IOException;
    }

    static Linker linker = Files::createLink;

    /**
     * The run log of this JVM, made by the first {@link #attach} with {@code nanolog.run=on}; {@code null} if it is
     * off. Guarded by the class, which is taken before an instance and never inside one.
     */
    private static RunLog current;
    private static boolean attached;
    // whether agent mode was on at the last attach, which tells what stops the run log at the next one
    private static boolean agentMode;

    private final Path file;
    private final Path errorsFile;
    private final JsonLogger json;
    private final long maxSize;
    // where the files go and how many runs stay, for a run log that opens them with its first entry
    private final Path directory;
    private final int keep;
    // whether the run log leaves its path to the line of agent mode
    private boolean quiet;
    // latest.log and latest.errors.log, or the files of the run without hard links
    private Path latest;
    private Path latestErrors;
    // a test JVM of a build tool, which names the files of the run from the start
    private boolean own;
    // the list of the run summary, written at the end of the run, and latest.summary.log or the list itself
    private Path summaryFile;
    private Path latestSummary;

    // guarded by this
    private OutputStream out;
    private OutputStream errors;
    private long written;
    private long errorsWritten;
    // the entries it was given while its files were open, for the line that stops it
    private long entries;
    private boolean brief;
    // whether opening the files was tried, and whether they are open or were
    private boolean started;
    private boolean filesOpened;

    /**
     * Writes to open streams. Reads the settings of {@link ConsoleLogger} like any other logger, so call it
     * {@link DefaultLoggerCreator#quietly quietly}: the console logger has reported an invalid one already.
     */
    RunLog(Path file, OutputStream out, Path errorsFile, OutputStream errors, boolean json, long maxSize) {
        this(file, errorsFile, null, 0, json, maxSize);
        this.out = out;
        this.errors = errors;
        started = true;
        filesOpened = true;
    }

    /**
     * A run log of a directory that opens its files with {@link #start()}; called {@link DefaultLoggerCreator#quietly
     * quietly}, as the other constructor.
     */
    RunLog(Path directory, String id, boolean json, int keep, long maxSize) {
        this(directory.resolve(RUNS).resolve(id + LOG_SUFFIX), directory.resolve(RUNS).resolve(id + ERRORS_SUFFIX),
             directory, keep, json, maxSize);
        summaryFile = directory.resolve(RUNS).resolve(id + SUMMARY_SUFFIX);
        latestSummary = directory.resolve(LATEST_SUMMARY);
    }

    private RunLog(Path file, Path errorsFile, Path directory, int keep, boolean json, long maxSize) {
        this.file = file;
        this.errorsFile = errorsFile;
        this.directory = directory;
        this.keep = keep;
        this.json = json ? new JsonLogger() : null;
        this.maxSize = maxSize;
        this.latest = file;
        this.latestErrors = errorsFile;
        setShowThread(true);
        setShowLocation(true);
    }

    // WHERE

    /**
     * The directory of the run log: {@code nanolog.run.dir}, relative to the working directory, or {@code target/nanolog}
     * in a Maven module, {@code build/nanolog} in a Gradle one and {@code .nanolog} otherwise.
     *
     * @param configured       the value of {@code nanolog.run.dir}; {@code null} or blank if not set
     * @param workingDirectory the working directory, which is the module directory in Maven and Gradle runs
     */
    static Path directory(String configured, Path workingDirectory) {
        if (configured != null && !configured.trim().isEmpty()) {
            return workingDirectory.resolve(configured.trim());
        } else if (Files.exists(workingDirectory.resolve("pom.xml"))) {
            return workingDirectory.resolve("target/nanolog");
        }
        for (String gradle : GRADLE_FILES) {
            if (Files.exists(workingDirectory.resolve(gradle))) {
                return workingDirectory.resolve("build/nanolog");
            }
        }
        return workingDirectory.resolve(".nanolog");
    }

    /**
     * The name of a run: its start time, sortable, and the process id, {@code 20261117-134435-123-4567}.
     */
    static String id(long millis, String pid) {
        return new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date(millis)) + "-" + pid;
    }

    /**
     * The id of this process; Java 8 has it only in the name of the runtime, {@code 4567@host}.
     */
    static String pid() {
        return pid("java.lang.management.ManagementFactory");
    }

    /**
     * The id of this process from the name of the runtime, read through reflection: {@code java.management} is
     * missing from a runtime image of {@code java.base} alone, and a module that does not read it cannot see it.
     * Without it a random id stands in, {@code r3f2a}, so two runs of one millisecond still get files of their own.
     *
     * @param managementFactory the class that gives the runtime bean; another one in tests
     */
    static String pid(String managementFactory) {
        try {
            Object runtime = Class.forName(managementFactory).getMethod("getRuntimeMXBean").invoke(null);
            String name = (String) Class.forName("java.lang.management.RuntimeMXBean").getMethod("getName").invoke(runtime);
            return name.substring(0, name.indexOf('@'));
        } catch (Throwable t) {
            return "r" + Integer.toHexString(0x10000 + ThreadLocalRandom.current().nextInt(0x10000)).substring(1);
        }
    }

    /**
     * Whether the JVM is one in which a build tool runs tests: a fork of Maven Surefire or Failsafe, or a test worker of
     * Gradle. Several of them may run in one module, side by side ({@code forkCount} above 1, {@code maxParallelForks})
     * or one after another ({@code reuseForks=false}), and each takes {@code latest.log} from the one before, so a path
     * printed at the start of a fork would soon lead to the entries of another one (#96).
     *
     * @param command the main class or jar of the JVM and its arguments, {@code sun.java.command}; {@code null} if not
     *                known
     */
    static boolean testWorker(String command) {
        return command != null && (command.startsWith(SUREFIRE_BOOTER) || command.startsWith(GRADLE_WORKER) ||
                                   SUREFIRE_JAR.matcher(command).matches());
    }

    // OPENING

    /**
     * Opens the files of a run, points {@code latest.log} and {@code latest.errors.log} to them and deletes the runs
     * beyond {@code keep}. A directory created here gets a {@code .gitignore}, so the log never goes into version
     * control.
     */
    static RunLog open(Path directory, boolean json, int keep, long maxSize, String id) throws IOException {
        RunLog log = quietly(() -> new RunLog(directory, id, json, keep, maxSize));
        synchronized (log) {
            log.started = true;
            log.openFiles();
        }
        return log;
    }

    // guarded by this
    private void openFiles() throws IOException {
        if (!Files.isDirectory(directory)) {
            Files.createDirectories(directory);
            Files.write(directory.resolve(".gitignore"), "*\n".getBytes(StandardCharsets.US_ASCII));
        }
        Path runs = Files.createDirectories(directory.resolve(RUNS));
        out = Files.newOutputStream(file);
        errors = Files.newOutputStream(errorsFile);
        latest = link(directory.resolve(LATEST), file);
        latestErrors = link(directory.resolve(LATEST_ERRORS), errorsFile);
        // the list of the run before would not be the list of the run in latest.log
        deleteQuietly(latestSummary);
        prune(runs, keep);
        filesOpened = true;
    }

    /**
     * Opens the files the first time it is called, by the first entry or by the line of agent mode that names them, and
     * says where they are unless agent mode does; a failure prints one diagnostic and leaves the run log off.
     *
     * @return whether the files were opened
     */
    synchronized boolean start() {
        if (!started) {
            started = true;
            try {
                openFiles();
                if (!quiet) {
                    DefaultLoggerCreator.printNotice("Run log: " + shownFile().toAbsolutePath() + "; WARN and ERROR also in " +
                                                     shownErrorsFile().toAbsolutePath());
                }
            } catch (IOException | RuntimeException e) {
                printDiagnostic("Cannot open the run log in " + directory.toAbsolutePath() + ": " + e + "; it is off");
            }
        }
        return filesOpened;
    }

    /**
     * Whether the run log still takes entries: its files are open or not tried yet.
     */
    private synchronized boolean usable() {
        return !started || out != null;
    }

    /**
     * Makes {@code link} a second name of {@code target}. Without hard links (FAT), or when another process holds the
     * old link, the file of the run is shown instead.
     *
     * @return {@code link}, or {@code target} if it could not be made
     */
    private static Path link(Path link, Path target) {
        try {
            Files.deleteIfExists(link);
            linker.link(link, target);
            return link;
        } catch (IOException | RuntimeException e) {
            return target;
        }
    }

    /**
     * Deletes the oldest runs beyond {@code keep}, with their errors files and lists. A file another process still holds
     * is left for a later run.
     */
    private static void prune(Path runs, int keep) throws IOException {
        // in the order of the names, which start with the time; a directory lists them in any order
        Set<Path> sorted = new TreeSet<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(runs, "*" + LOG_SUFFIX)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!name.endsWith(ERRORS_SUFFIX) && !name.endsWith(SUMMARY_SUFFIX)) {
                    sorted.add(file);
                }
            }
        }
        List<Path> logs = new ArrayList<>(sorted);
        for (int i = 0; i < logs.size() - keep; i++) {
            String name = logs.get(i).getFileName().toString();
            String id = name.substring(0, name.length() - LOG_SUFFIX.length());
            deleteQuietly(logs.get(i));
            deleteQuietly(runs.resolve(id + ERRORS_SUFFIX));
            deleteQuietly(runs.resolve(id + SUMMARY_SUFFIX));
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // still open in a running JVM on Windows: a later run deletes it
        }
    }

    /**
     * Adds the run log to the default logger when {@code nanolog.run=on}, the default in agent mode. It is made by the
     * first call and reused by every later {@code LOG.init()} of the JVM, as is a failure to open it. A call whose
     * settings no longer turn it on stops it for the rest of the JVM ({@link #stop}).
     *
     * @param logger the default logger
     * @param agent  whether agent mode is on
     * @param by     the call that reads the settings, {@code LOG.init() at Tool.main(Tool.java:6)}, which the line
     *               that stops the run log names
     * @return {@code logger}, or a {@link Tee} of it and the run log
     */
    static synchronized ILogger attach(ILogger logger, boolean agent, String by) {
        String run = agent ? readChoice(PROPERTY_RUN, "on", "off") : readChoice(PROPERTY_RUN, "off", "on");
        boolean agentBefore = agentMode;
        agentMode = agent;
        if ("off".equals(run)) {
            // a LOG.init() whose settings no longer turn it on stops it for the rest of the JVM (#111)
            if (current != null) {
                // the run log of agent mode ends with it unless nanolog.run keeps it, which it does not here
                current.stop(agentBefore && getProperty(PROPERTY_RUN) == null
                             ? "Agent mode turned off by " + by + " (" + PROPERTY_AGENT + "=" +
                               quietly(() -> readChoice(PROPERTY_AGENT, "auto", "on", "off")) + "): the run log ends here"
                             : "Run log turned off by " + by + " (" + PROPERTY_RUN + "=off): it ends here");
                current = null;
            }
            return logger;
        }
        if (!attached) {
            attached = true;
            boolean json = "json".equals(readChoice(PROPERTY_RUN_FORMAT, "text", "json"));
            int keep = readPositive(PROPERTY_RUN_KEEP, 10);
            long maxSize = readPositive(PROPERTY_RUN_MAX_SIZE, 100) * MB;
            String id = id(System.currentTimeMillis(), pid());
            Path directory = directory(getProperty(PROPERTY_RUN_DIR), Paths.get(System.getProperty("user.dir")));
            current = quietly(() -> new RunLog(directory, id, json, keep, maxSize));
            current.own = testWorker(System.getProperty("sun.java.command"));
            current.quiet = agent;
        }
        // only the console that agent mode shortened leaves levels to the run log
        return current == null || !current.usable() ? logger : new Tee(logger, current, agent && Agent.isConsole(logger));
    }

    /**
     * A logger together with the run log of this JVM. {@link LOG} installs one for every logger while the run log is
     * open, so the file keeps every entry of the run whichever logger the application installs (#90).
     * <p>
     * The run log writes the entries its logger takes, and the levels are those of the logger: {@code LOG.isXxxEnabled}
     * and the lazy arguments of a call answer as they would without the run log, also under an agent (#103). Only the
     * console that agent mode shortened adds the levels of the run log, which reads the same settings: the file keeps
     * what the short console leaves out, and the levels are those the console has without agent mode.
     * <p>
     * A Tee may reach another one through its logger: a decorator passes each entry on to {@link LOG#getLogger()}, the
     * Tee it replaced, and so does {@link MemoryLogger#installForwarding()}. The inner Tee then passes the entry to its
     * logger only, and the outer one writes it to the run log, once (#95).
     */
    static final class Tee extends CompositeLogger {

        // the entry that a Tee is passing on in this thread
        private static final ThreadLocal<LogEntry> PASSING = new ThreadLocal<>();

        private final ILogger logger;
        // whether the levels of the run log count too: for the console that agent mode shortened
        private final boolean widened;

        Tee(ILogger logger, RunLog runLog, boolean widened) {
            super(logger, runLog);
            this.logger = logger;
            this.widened = widened;
        }

        @Override
        public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass, String tag) {
            return widened ? super.isEnabled(level, sourceClass, tag) : CompositeLogger.isEnabled(logger, level, sourceClass, tag);
        }

        @Override
        public boolean mayBeEnabled(LogEntry.LogEntryLevel level) {
            return widened ? super.mayBeEnabled(level) : CompositeLogger.mayBeEnabled(logger, level);
        }

        @Override
        public void log(LogEntry entry) {
            LogEntry outer = PASSING.get();
            if (outer == entry) {
                pass(logger, entry);
                return;
            }
            PASSING.set(entry);
            try {
                super.log(entry);
            } finally {
                PASSING.set(outer);
            }
        }
    }

    /**
     * Adds the run log of this JVM to a logger that does not write it yet, for {@link LOG#init(ILogger)} and
     * {@link MemoryLogger#install()}: a logger the application installs at startup, Spring's {@code Slf4jLogger} for
     * one, would otherwise leave the run log behind with what was logged before it.
     *
     * @param logger the logger to install; {@code null} is installed as it is
     * @return {@code logger}, or a {@link Tee} of it and the run log
     */
    static ILogger with(ILogger logger) {
        RunLog runLog = current();
        if (runLog == null || !runLog.usable() || logger == null || writes(logger, runLog)) {
            return logger;
        }
        return new Tee(logger, runLog, false);
    }

    private static boolean writes(ILogger logger, RunLog runLog) {
        return logger == runLog || logger instanceof CompositeLogger && ((CompositeLogger) logger).getLoggers().contains(runLog);
    }

    /**
     * The logger that code installed, without the run log that {@link #with} or {@link #attach} added to it.
     *
     * @param logger an installed logger
     * @return the logger inside a {@link Tee}, otherwise {@code logger}
     */
    static ILogger unwrap(ILogger logger) {
        return logger instanceof Tee ? ((Tee) logger).logger : logger;
    }

    /**
     * The run log of this JVM, whose files may not be open yet.
     *
     * @return the run log; {@code null} if it is off
     */
    static synchronized RunLog current() {
        return current;
    }

    /**
     * The run log of this JVM with its files open, for a line that names them: the run summary, which does not open
     * them for a run that logged nothing to them.
     *
     * @return the run log; {@code null} if it is off or its files were not opened
     */
    static synchronized RunLog opened() {
        return current != null && current.filesOpened() ? current : null;
    }

    /**
     * The run log of this JVM, its files opened now if they are not yet: for the line of agent mode, printed before the
     * first entry reaches them.
     *
     * @return the run log; {@code null} if it is off or its files cannot be opened
     */
    static RunLog started() {
        RunLog runLog = current();
        return runLog != null && runLog.start() ? runLog : null;
    }

    private synchronized boolean filesOpened() {
        return filesOpened;
    }

    /**
     * Writes entries that were held back from a logger to the run log, if that logger writes the run log: a composite
     * of the default logger with it. For {@link NanologExtension}, which keeps the log of a passing test off the
     * console but not out of the file.
     *
     * @param logger  the logger the entries were held back from
     * @param entries the entries, oldest first
     */
    static void replay(ILogger logger, List<LogEntry> entries) {
        RunLog runLog = current();
        if (runLog != null && logger instanceof CompositeLogger && ((CompositeLogger) logger).getLoggers().contains(runLog)) {
            for (LogEntry entry : entries) {
                if (runLog.isEnabled(entry.getLevel(), entry.getSourceClass())) {
                    runLog.log(entry);
                }
            }
        }
    }

    /**
     * Closes the run log of this JVM and lets the next {@link #attach} open a new one; for tests.
     */
    static synchronized void reset() {
        if (current != null) {
            current.close();
        }
        current = null;
        attached = false;
        agentMode = false;
    }

    /**
     * The path that names the log of this run: {@code latest.log} while it is a name of the file of this run, otherwise
     * the file itself. Another fork of Surefire that starts later in the same directory takes {@code latest.log}, and
     * the summary of this fork would send the reader to the entries of the other one (#93). A {@link #testWorker test
     * JVM} names the file itself from the start: a path printed at the start must stay true for the whole run, and any
     * other fork may take {@code latest.log} later (#96).
     */
    Path shownFile() {
        return own ? file : shown(latest, file);
    }

    /**
     * The path that names the warnings and errors of this run, as {@link #shownFile()}.
     */
    Path shownErrorsFile() {
        return own ? errorsFile : shown(latestErrors, errorsFile);
    }

    private static Path shown(Path link, Path target) {
        try {
            return Files.isSameFile(link, target) ? link : target;
        } catch (IOException | SecurityException e) {
            // deleted, or not to be read
            return target;
        }
    }

    /**
     * Writes the list of the run summary, one kind of warning or error a line, to {@code runs/<id>.summary.log}, and
     * makes {@code latest.summary.log} a second name of it while {@code latest.log} is the log of this run: a run that
     * ends after a later one has started leaves {@code latest.summary.log} to that one (#99). An I/O error prints one
     * diagnostic.
     *
     * @param kinds the lines of the list
     * @return the path that names the list, as {@link #shownFile()} names the log; {@code null} if it could not be
     * written
     */
    synchronized Path writeSummary(List<String> kinds) {
        try {
            Files.write(summaryFile, kinds, StandardCharsets.UTF_8);
        } catch (IOException e) {
            printDiagnostic("Cannot write the list of the run summary " + summaryFile + ": " + e);
            return null;
        }
        if (shown(latest, file) == latest) {
            latestSummary = link(latestSummary, summaryFile);
        }
        return own ? summaryFile : shown(latestSummary, summaryFile);
    }

    long maxSize() {
        return maxSize;
    }

    boolean isJson() {
        return json != null;
    }

    // WRITING

    /**
     * Writes the entry to the file of the run and, for {@code WARN} and {@code ERROR}, a one-line form of it to the
     * errors file.
     */
    @Override
    public synchronized void log(LogEntry entry) {
        if (start() && out != null) {
            entries++;
            Path failed = file;
            try {
                // read back by agents, so no line reads other than it holds, also with keep (#113)
                written = write(out, json == null ? escapeMisleading(createLogString(entry)) : json.createLogString(entry), written);
                if (entry.getLevel() != null && entry.getLevel().ordinal() >= LogEntry.LogEntryLevel.WARN.ordinal()) {
                    failed = errorsFile;
                    errorsWritten = write(errors, errorLine(entry), errorsWritten);
                }
            } catch (IOException e) {
                close();
                printDiagnostic("Cannot write the run log " + failed + ": " + e + "; it is off for the rest of the run");
            }
        }
    }

    /**
     * Writes a line unless the file is full; the line that would overflow it is replaced by a note, and nothing
     * follows.
     *
     * @return the size of the file, or more than {@link #maxSize} once it is full
     */
    private long write(OutputStream stream, String line, long size) throws IOException {
        if (size > maxSize) {
            return size;
        }
        byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        if (size + bytes.length > maxSize) {
            stream.write((note(TRUNCATED) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            return maxSize + 1;
        }
        stream.write(bytes);
        return size + bytes.length;
    }

    /**
     * A line of Nanolog's own in the run log, escaped as the console escapes it: {@code [NANOLOG] text}, or
     * {@code {"nanolog":"text"}} in JSON.
     */
    private String note(String text) {
        if (json == null) {
            return "[NANOLOG] " + escapeControlCharacters(text);
        }
        StringBuilder line = new StringBuilder("{\"nanolog\":");
        JsonLogger.appendString(line, text);
        return line.append('}').toString();
    }

    /**
     * A {@code WARN} or {@code ERROR} entry as one line, cheap to follow with {@code tail -f}: in text, the exception
     * by its {@code toString()} and the rest as {@code nanolog.console.multiline=escape} writes it, whatever the run log
     * reads, so the escape sequences of a value do not reach the terminal (#113); a JSON line is one line already.
     */
    private String errorLine(LogEntry entry) {
        if (json != null) {
            return json.createLogString(entry);
        }
        brief = true;
        try {
            return applyMultiline(createLogString(entry), ESCAPE);
        } finally {
            brief = false;
        }
    }

    @Override
    protected void writeThrowable(StringWriter out, LogEntry entry) {
        if (brief && entry.getThrowable() != null) {
            addDelimiter(out);
            out.append(LogEntry.safeToString(entry.getThrowable()));
        } else {
            super.writeThrowable(out, entry);
        }
    }

    /**
     * Stops the run log for the rest of the JVM. While its files are open, a line says why and how many entries it
     * holds, last in both files and on the console: the line of agent mode or of the run log has sent the reader to
     * them, and a file that just stops reads as a run that crashed or hung there (#115). A run log that no entry has
     * opened stops without a word (#111), and so does one that an I/O error closed, which said so then.
     *
     * @param reason what stopped it, {@code Run log turned off by LOG.init() at ... (nanolog.run=off): it ends here}
     */
    synchronized void stop(String reason) {
        if (out != null) {
            String line = reason + ", after " + entries + (entries == 1 ? " entry" : " entries");
            try {
                write(out, note(line), written);
                write(errors, note(line), errorsWritten);
            } catch (IOException e) {
                // the console says it all the same
            }
            DefaultLoggerCreator.printNotice(line);
        }
        close();
    }

    /**
     * Closes both files; later entries are not written, and a run log closed before its first entry never opens them.
     */
    synchronized void close() {
        closeQuietly(out);
        closeQuietly(errors);
        out = null;
        errors = null;
        started = true;
    }

    private static void closeQuietly(OutputStream stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            // nothing more to write
        }
    }
}
