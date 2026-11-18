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

import static com.nanolaba.logging.DefaultLoggerCreator.*;

/**
 * The run log, {@code nanolog.run=on}: every entry of the run also goes to a file at a predictable place, so a coding
 * agent reads the output of the last run again instead of running it again. Warnings and errors go to a second file as
 * well, one line each.
 * <p>
 * Each run writes {@code runs/<time>-<pid>.log} and {@code runs/<time>-<pid>.errors.log} in the directory;
 * {@code latest.log} and {@code latest.errors.log} are hard links to them, so they are written once and show the run
 * live. The last {@code nanolog.run.keep} runs are kept. Each file stops at {@code nanolog.run.maxSize} with a note.
 * <p>
 * The run log is a {@link ConsoleLogger} with its own levels, read from the same settings, so a console kept short
 * leaves the file complete. Text lines show the thread and the call site. The file is opened once per JVM and is
 * written without a buffer, so what was logged is on disk even if the JVM is killed. An I/O error prints one
 * diagnostic and turns the run log off; logging goes on.
 */
final class RunLog extends ConsoleLogger {

    static final String LATEST = "latest.log";
    static final String LATEST_ERRORS = "latest.errors.log";
    static final String RUNS = "runs";
    static final String LOG_SUFFIX = ".log";
    static final String ERRORS_SUFFIX = ".errors.log";

    private static final String[] GRADLE_FILES = {"build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"};
    private static final String TRUNCATED = "The run log reached " + PROPERTY_RUN_MAX_SIZE + "; later entries are not written";
    private static final long MB = 1024 * 1024;

    /**
     * Creates {@code latest.log} as a second name of the file of the run. Tests replace it to make it fail and must
     * restore it afterwards.
     */
    interface Linker {

        void link(Path link, Path target) throws IOException;
    }

    static Linker linker = Files::createLink;

    /**
     * The run log of this JVM, opened by the first {@link #attach} with {@code nanolog.run=on}; {@code null} if it
     * could not be opened. Guarded by the class.
     */
    private static RunLog current;
    private static boolean opened;

    private final Path file;
    private final Path errorsFile;
    private final JsonLogger json;
    private final long maxSize;
    private Path shownFile;
    private Path shownErrorsFile;

    // guarded by this
    private OutputStream out;
    private OutputStream errors;
    private long written;
    private long errorsWritten;
    private boolean brief;

    /**
     * Writes to open streams. Reads the settings of {@link ConsoleLogger} like any other logger, so call it
     * {@link DefaultLoggerCreator#quietly quietly}: the console logger has reported an invalid one already.
     */
    RunLog(Path file, OutputStream out, Path errorsFile, OutputStream errors, boolean json, long maxSize) {
        this.file = file;
        this.out = out;
        this.errorsFile = errorsFile;
        this.errors = errors;
        this.json = json ? new JsonLogger() : null;
        this.maxSize = maxSize;
        this.shownFile = file;
        this.shownErrorsFile = errorsFile;
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

    // OPENING

    /**
     * Opens the files of a run, points {@code latest.log} and {@code latest.errors.log} to them and deletes the runs
     * beyond {@code keep}. A directory created here gets a {@code .gitignore}, so the log never goes into version
     * control.
     */
    static RunLog open(Path directory, boolean json, int keep, long maxSize, String id) throws IOException {
        if (!Files.isDirectory(directory)) {
            Files.createDirectories(directory);
            Files.write(directory.resolve(".gitignore"), "*\n".getBytes(StandardCharsets.US_ASCII));
        }
        Path runs = Files.createDirectories(directory.resolve(RUNS));
        Path file = runs.resolve(id + LOG_SUFFIX);
        Path errorsFile = runs.resolve(id + ERRORS_SUFFIX);
        OutputStream out = Files.newOutputStream(file);
        OutputStream errors = Files.newOutputStream(errorsFile);
        RunLog log = quietly(() -> new RunLog(file, out, errorsFile, errors, json, maxSize));
        log.shownFile = link(directory.resolve(LATEST), file);
        log.shownErrorsFile = link(directory.resolve(LATEST_ERRORS), errorsFile);
        prune(runs, keep);
        return log;
    }

    /**
     * Makes {@code link} a second name of {@code target}. Without hard links (FAT), or when another process holds the
     * old link, the file of the run is shown instead.
     *
     * @return the path to show
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
     * Deletes the oldest runs beyond {@code keep}. A file another process still holds is left for a later run.
     */
    private static void prune(Path runs, int keep) throws IOException {
        // in the order of the names, which start with the time; a directory lists them in any order
        Set<Path> sorted = new TreeSet<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(runs, "*" + LOG_SUFFIX)) {
            for (Path file : files) {
                if (!file.getFileName().toString().endsWith(ERRORS_SUFFIX)) {
                    sorted.add(file);
                }
            }
        }
        List<Path> logs = new ArrayList<>(sorted);
        for (int i = 0; i < logs.size() - keep; i++) {
            String name = logs.get(i).getFileName().toString();
            deleteQuietly(logs.get(i));
            deleteQuietly(runs.resolve(name.substring(0, name.length() - LOG_SUFFIX.length()) + ERRORS_SUFFIX));
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
     * Adds the run log to the default logger when {@code nanolog.run=on}, the default in agent mode. It is opened by
     * the first call, which prints where it is unless agent mode says so in its own line, and reused by every later
     * {@code LOG.init()} of the JVM, as is a failure to open it.
     *
     * @param logger the default logger
     * @param agent  whether agent mode is on
     * @return {@code logger}, or a {@link CompositeLogger} of it and the run log
     */
    static synchronized ILogger attach(ILogger logger, boolean agent) {
        String run = agent ? readChoice(PROPERTY_RUN, "on", "off") : readChoice(PROPERTY_RUN, "off", "on");
        if ("off".equals(run)) {
            return logger;
        }
        if (!opened) {
            opened = true;
            Path directory = directory(getProperty(PROPERTY_RUN_DIR), Paths.get(System.getProperty("user.dir")));
            try {
                current = open(directory, "json".equals(readChoice(PROPERTY_RUN_FORMAT, "text", "json")),
                               readPositive(PROPERTY_RUN_KEEP, 10), readPositive(PROPERTY_RUN_MAX_SIZE, 100) * MB,
                               id(System.currentTimeMillis(), pid()));
                if (!agent) {
                    DefaultLoggerCreator.printNotice("Run log: " + current.shownFile.toAbsolutePath() + "; WARN and ERROR also in " +
                                    current.shownErrorsFile.toAbsolutePath());
                }
            } catch (IOException | RuntimeException e) {
                printDiagnostic("Cannot open the run log in " + directory.toAbsolutePath() + ": " + e + "; it is off");
            }
        }
        return current == null ? logger : new CompositeLogger(logger, current);
    }

    /**
     * The run log of this JVM.
     *
     * @return the run log; {@code null} if it is off or could not be opened
     */
    static synchronized RunLog current() {
        return current;
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
        opened = false;
    }

    Path shownFile() {
        return shownFile;
    }

    Path shownErrorsFile() {
        return shownErrorsFile;
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
        if (out != null) {
            Path failed = file;
            try {
                written = write(out, json == null ? createLogString(entry) : json.createLogString(entry), written);
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
            String note = json == null ? "[NANOLOG] " + TRUNCATED : "{\"nanolog\":\"" + TRUNCATED + "\"}";
            stream.write((note + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            return maxSize + 1;
        }
        stream.write(bytes);
        return size + bytes.length;
    }

    /**
     * A {@code WARN} or {@code ERROR} entry as one line, cheap to follow with {@code tail -f}: in text, the exception
     * by its {@code toString()} and line breaks as {@code \n}; a JSON line is one line already.
     */
    private String errorLine(LogEntry entry) {
        if (json != null) {
            return json.createLogString(entry);
        }
        brief = true;
        try {
            return createLogString(entry).replace("\r", "\\r").replace("\n", "\\n");
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
     * Closes both files; later entries are not written.
     */
    synchronized void close() {
        closeQuietly(out);
        closeQuietly(errors);
        out = null;
        errors = null;
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
