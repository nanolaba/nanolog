package com.nanolaba.logging;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;

/**
 * The run summary, {@code nanolog.summary}: a few lines at the end of the run with the counts by level, each kind of
 * warning and error with its call site and count, the trace of the first error, the {@code LOG.dbg} calls that ran
 * and the path of the run log. A coding agent often keeps only the end of a long output, so the summary is printed
 * last: when JUnit has run the tests ({@link SummaryListener}), or else by a shutdown hook. Surefire drops what a
 * shutdown hook of its forked JVM prints, so in a Maven test run only the listener is heard.
 * <p>
 * Counting keeps no entry: a counter per level, and per kind (level, call site and message template) a counter, of at
 * most {@link #MAX_KEPT} kinds. When the summary is off nothing is counted, and the end of the run lists only the
 * {@code LOG.dbg} calls that ran, as before.
 */
final class Summary {

    /**
     * What the end of the run prints.
     */
    enum Mode {
        /**
         * Only the {@code LOG.dbg} calls that ran; the default without an agent.
         */
        OFF,
        /**
         * The summary; the default in agent mode.
         */
        ON,
        /**
         * The summary if a {@code WARN} or {@code ERROR} was logged, otherwise as {@link #OFF}.
         */
        ERRORS
    }

    /**
     * The most kinds of warnings and errors the summary shows.
     */
    static final int MAX_SHOWN = 10;
    /**
     * The most kinds counted; entries of later kinds count in the totals only.
     */
    static final int MAX_KEPT = 1000;
    /**
     * The most characters of a message template shown.
     */
    static final int MAX_TEXT = 200;
    /**
     * The most lines of the trace of the first error shown.
     */
    static final int MAX_TRACE_LINES = 15;

    private static final int MAX_TRACE_FRAMES = 10;
    private static final String INDENT = "  ";
    private static final String TRACE_INDENT = "      ";

    /**
     * Read by {@link LOG#init()}; {@code volatile} like the logger, since every logging call reads it.
     */
    static volatile Mode mode = Mode.OFF;

    /**
     * Registers the hook that prints at exit. Tests replace it to run the hook themselves and must restore it
     * afterwards.
     */
    static Consumer<Thread> shutdownHooks = Runtime.getRuntime()::addShutdownHook;

    private static final LogEntry.LogEntryLevel[] LEVELS = LogEntry.LogEntryLevel.values();
    private static final AtomicLong[] COUNTS = new AtomicLong[LEVELS.length];
    private static final AtomicLong TOTAL = new AtomicLong();
    private static final Map<String, Kind> KINDS = new ConcurrentHashMap<>();
    private static final AtomicInteger ORDER = new AtomicInteger();
    /**
     * The key of the kind of the first error with an exception, and its trace.
     */
    private static final AtomicReference<String[]> FIRST_ERROR = new AtomicReference<>();
    private static final AtomicBoolean HOOK_REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean PRINTED = new AtomicBoolean();

    static {
        for (int i = 0; i < COUNTS.length; i++) {
            COUNTS[i] = new AtomicLong();
        }
    }

    private Summary() {
    }

    /**
     * One kind of warning or error: its level, where it was logged and its template, with a count.
     */
    private static final class Kind {

        final String key;
        final LogEntry.LogEntryLevel level;
        /**
         * What follows the count: {@code at OrderService.java:87: Charge of {} failed}.
         */
        final String place;
        final int order;
        final AtomicLong count = new AtomicLong();

        Kind(String key, LogEntry.LogEntryLevel level, String place, int order) {
            this.key = key;
            this.level = level;
            this.place = place;
            this.order = order;
        }
    }

    /**
     * Counts an entry that is written; called by {@code LOG} for every entry while the summary is on.
     */
    static void count(LogEntry entry) {
        TOTAL.incrementAndGet();
        LogEntry.LogEntryLevel level = entry.getLevel();
        if (level == null) {
            return;
        }
        COUNTS[level.ordinal()].incrementAndGet();
        if (level.ordinal() < WARN.ordinal()) {
            return;
        }
        String place = " at " + where(entry) + ": " + template(entry);
        String key = level.name() + place;
        Kind kind = KINDS.get(key);
        if (kind == null && KINDS.size() < MAX_KEPT) {
            kind = KINDS.computeIfAbsent(key, k -> new Kind(k, level, place, ORDER.getAndIncrement()));
        }
        if (kind != null) {
            kind.count.incrementAndGet();
        }
        if (level == ERROR && entry.getThrowable() != null && FIRST_ERROR.get() == null) {
            FIRST_ERROR.compareAndSet(null, new String[]{key, trace(entry.getThrowable())});
        }
    }

    /**
     * The call site, {@code OrderService.java:87}, or for a call with an explicit class the name of the class.
     */
    static String where(LogEntry entry) {
        if (entry.getCaller() == null && entry.getSourceClass() != null) {
            return entry.getSourceClass().getName();
        }
        return Dbg.site(entry.getCaller());
    }

    /**
     * The message as written, with its {@code {}}, or the exception of an entry without one; cut to
     * {@link #MAX_TEXT}. The line of a unit, whose template every unit shares, is {@code unit <name>}.
     */
    static String template(LogEntry entry) {
        if (entry.isUnitLine()) {
            return "unit " + entry.getFields().get("unit");
        }
        Object message = entry.getMessage() != null ? entry.getMessage() : entry.getThrowable();
        String text = message == null ? "" : LogEntry.safeToString(message);
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) + "..." : text;
    }

    /**
     * The trace without the frames of agent mode, at most {@link #MAX_TRACE_FRAMES} per throwable, every cause kept.
     */
    private static String trace(Throwable throwable) {
        try {
            return ShortStackTrace.of(throwable, Agent.EXCLUDE, MAX_TRACE_FRAMES);
        } catch (Throwable e) {
            return ConsoleLogger.printedStackTrace(throwable);
        }
    }

    /**
     * The lines of the summary, without the {@code [NANOLOG]} prefix.
     */
    static List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(header());
        List<Kind> kinds = new ArrayList<>(KINDS.values());
        // errors first, each level in the order its kinds first appeared
        kinds.sort(Comparator.comparingInt((Kind kind) -> -kind.level.ordinal()).thenComparingInt(kind -> kind.order));
        String[] firstError = FIRST_ERROR.get();
        for (Kind kind : kinds.subList(0, Math.min(MAX_SHOWN, kinds.size()))) {
            lines.add(INDENT + kind.level.name() + " x" + kind.count.get() + kind.place);
            if (firstError != null && firstError[0].equals(kind.key)) {
                addTrace(lines, firstError[1]);
            }
        }
        if (kinds.size() > MAX_SHOWN) {
            lines.add(INDENT + "... and " + (kinds.size() - MAX_SHOWN) + " more kinds of WARN and ERROR");
        }
        String calls = Dbg.callsLine();
        if (calls != null) {
            lines.add(INDENT + calls);
        }
        return lines;
    }

    private static String header() {
        StringBuilder header = new StringBuilder("Run summary: ");
        long total = TOTAL.get();
        if (total == 0) {
            header.append("no entries");
        } else {
            header.append(total).append(total == 1 ? " entry:" : " entries:");
            String separator = " ";
            for (int i = LEVELS.length - 1; i >= 0; i--) {
                long count = COUNTS[i].get();
                if (count > 0) {
                    header.append(separator).append(count).append(' ').append(LEVELS[i].name());
                    separator = ", ";
                }
            }
        }
        RunLog runLog = RunLog.current();
        if (runLog != null) {
            header.append("; the full log is in ").append(runLog.shownFile().toAbsolutePath());
        }
        return header.toString();
    }

    private static void addTrace(List<String> lines, String trace) {
        String[] traceLines = trace.replace("\r", "").replace("\t", "    ").split("\n");
        for (int i = 0; i < Math.min(MAX_TRACE_LINES, traceLines.length); i++) {
            lines.add(TRACE_INDENT + traceLines[i]);
        }
        if (traceLines.length > MAX_TRACE_LINES) {
            lines.add(TRACE_INDENT + "... " + (traceLines.length - MAX_TRACE_LINES) + " more lines");
        }
    }

    /**
     * Registers the hook that prints at exit, once per JVM. A JVM that is already shutting down, or a security manager
     * that forbids hooks, leaves the end of the run unprinted.
     */
    static void registerHook() {
        if (HOOK_REGISTERED.compareAndSet(false, true)) {
            try {
                shutdownHooks.accept(new Thread(Summary::printOnce, "nanolog-summary"));
            } catch (RuntimeException e) {
                // IllegalStateException during shutdown, SecurityException: logging goes on
            }
        }
    }

    /**
     * Prints the end of the run, the first time it is called: the units still open, as {@code interrupted}, then the
     * summary when it is on, or else the {@code LOG.dbg} calls that ran. Called by {@link SummaryListener} and by the
     * shutdown hook, whichever comes first; {@link Units} registers the hook as well.
     */
    static void printOnce() {
        if (PRINTED.compareAndSet(false, true)) {
            // the lines of the open units come first, so the summary counts them
            Units.interruptOpen();
            Mode current = mode;
            boolean warned = COUNTS[WARN.ordinal()].get() + COUNTS[ERROR.ordinal()].get() > 0;
            if (current == Mode.ON || current == Mode.ERRORS && warned) {
                for (String line : lines()) {
                    DefaultLoggerCreator.printDiagnostic(line);
                }
            } else {
                Dbg.printCalls();
            }
        }
    }

    /**
     * Forgets the counts, the {@code LOG.dbg} calls, the hook and that the end was printed; for tests.
     */
    static void reset() {
        for (AtomicLong count : COUNTS) {
            count.set(0);
        }
        TOTAL.set(0);
        KINDS.clear();
        FIRST_ERROR.set(null);
        HOOK_REGISTERED.set(false);
        PRINTED.set(false);
        Dbg.clearCalls();
    }
}
