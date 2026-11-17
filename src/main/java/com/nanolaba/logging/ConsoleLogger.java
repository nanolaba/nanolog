package com.nanolaba.logging;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_LEVEL;
import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_LEVELS;
import static com.nanolaba.logging.DefaultLoggerCreator.getProperty;
import static com.nanolaba.logging.DefaultLoggerCreator.printDiagnostic;
import static com.nanolaba.logging.DefaultLoggerCreator.quietly;
import static com.nanolaba.logging.DefaultLoggerCreator.readBoolean;
import static com.nanolaba.logging.DefaultLoggerCreator.readChoice;

/**
 * The default logger: one line per entry, {@code ERROR} to {@code System.err} and everything else to
 * {@code System.out} unless {@link #setStream(String)} says otherwise, followed by the stack trace if there is one.
 * <pre>
 * LEVEL dd.MM.yyyy HH:mm:ss [SourceClass] message
 * </pre>
 * The name of the thread ({@link #setShowThread(boolean)}) and the call site ({@link #setShowLocation(boolean)}) can
 * be added.
 * Every part is switched by a fluent setter, and the constructor reads the same settings from system properties,
 * environment variables and {@code nanolog.properties}: {@code nanolog.level}, per-package rules
 * ({@code nanolog.level.<package or class>}, {@code nanolog.levels}) and {@code nanolog.console.*}. All levels are
 * enabled by default. For a different layout extend it and override the protected {@code write*} methods.
 * <p>
 * Logging is thread-safe. The levels may change at any time, through the level setters or {@link ILevelControl}, and
 * every logging thread sees the change on its next call; the display setters are meant for startup, not for
 * concurrent reconfiguration.
 */
public class ConsoleLogger implements ILogger, ILevelControl {

    /**
     * Property for {@link #setShowLevel(boolean)}: {@code true} or {@code false}.
     */
    public static final String PROPERTY_SHOW_LEVEL = "nanolog.console.showLevel";
    /**
     * Property for {@link #setShowDate(boolean)}: {@code true} or {@code false}.
     */
    public static final String PROPERTY_SHOW_DATE = "nanolog.console.showDate";
    /**
     * Property for {@link #setShowSource(boolean)}: {@code true} or {@code false}.
     */
    public static final String PROPERTY_SHOW_SOURCE = "nanolog.console.showSource";
    /**
     * Property for {@link #setShowSourceFullName(boolean)}: {@code true} or {@code false}.
     */
    public static final String PROPERTY_SHOW_SOURCE_FULL_NAME = "nanolog.console.showSourceFullName";
    /**
     * Property for {@link #setShowLocation(boolean)}: {@code true} or {@code false}.
     */
    public static final String PROPERTY_SHOW_LOCATION = "nanolog.console.showLocation";
    /**
     * Property for {@link #setShowThread(boolean)}: {@code true} or {@code false}.
     */
    public static final String PROPERTY_SHOW_THREAD = "nanolog.console.showThread";
    /**
     * Property for {@link #setStream(String)}: {@code out}, {@code err} or {@code err@LEVEL}.
     */
    public static final String PROPERTY_STREAM = "nanolog.console.stream";
    /**
     * Property for {@link #setExceptionExclude(String...)}: comma-separated package or class prefixes.
     */
    public static final String PROPERTY_EXCEPTION_EXCLUDE = "nanolog.exception.exclude";
    /**
     * Property for {@link #setExceptionMaxFrames(int)}: a number of frames, {@code 0} for no limit.
     */
    public static final String PROPERTY_EXCEPTION_MAX_FRAMES = "nanolog.exception.maxFrames";
    /**
     * Property for {@link #setDateFormat(SimpleDateFormat)}: a {@code SimpleDateFormat} pattern.
     */
    public static final String PROPERTY_DATE_FORMAT = "nanolog.console.dateFormat";
    /**
     * Property for {@link #setMultiline(String)}: {@code keep}, {@code indent} or {@code escape}.
     */
    public static final String PROPERTY_MULTILINE = "nanolog.console.multiline";

    private static final String LEVEL_RULE_PREFIX = PROPERTY_LEVEL + ".";
    private static final String[] THRESHOLDS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF"};
    private static final int NO_RULE = -1;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    private boolean showLevel = true;
    private boolean showDate = true;
    private boolean showSource = true;
    private boolean showSourceFullName = false;
    private boolean showLocation = false;
    private boolean showThread = false;

    /**
     * The ordinal of the lowest level written to {@code System.err}; {@code Integer.MAX_VALUE} writes everything to
     * {@code System.out}.
     */
    private int errThreshold = LogEntry.LogEntryLevel.ERROR.ordinal();

    private List<String> exceptionExclude = Collections.emptyList();
    private int exceptionMaxFrames = 0;

    private static final String KEEP = "keep";
    private static final String INDENT = "indent";
    private static final String ESCAPE = "escape";
    private static final String[] MULTILINE_MODES = {KEEP, INDENT, ESCAPE};
    private static final String EXPECTED_MULTILINE = "keep, indent or escape";

    private String multiline = KEEP;

    /**
     * The level the switches hold without {@code nanolog.level}: {@code TRACE}, or {@code WARN} in agent mode.
     */
    private int defaultThreshold = LogEntry.LogEntryLevel.TRACE.ordinal();

    // volatile: the levels may change at run time, and every logging thread must see the change on its next call
    private volatile boolean traceEnabled = true;
    private volatile boolean debugEnabled = true;
    private volatile boolean infoEnabled = true;
    private volatile boolean warnEnabled = true;
    private volatile boolean errorEnabled = true;

    /**
     * The package and class rules, from {@code nanolog.levels}, {@code nanolog.level.<name>} and the level methods.
     * Never modified: a change builds new rules under the lock of {@link #replaceRule} and swaps the reference.
     */
    private volatile Rules rules;

    /**
     * Set for a subclass with its own {@code isEnabled}, whose answers {@link #mayBeEnabled} cannot predict.
     */
    private final boolean isEnabledOverridden;

    /**
     * Creates a logger configured from system properties, environment variables and {@code nanolog.properties}
     * (in this order of precedence); everything not configured keeps its default. Setters called afterwards override
     * the configuration, except that a package or class rule always beats the global level switches.
     */
    public ConsoleLogger() {
        applyGlobalLevel();
        showLevel = readBoolean(PROPERTY_SHOW_LEVEL, showLevel);
        showDate = readBoolean(PROPERTY_SHOW_DATE, showDate);
        showSource = readBoolean(PROPERTY_SHOW_SOURCE, showSource);
        showSourceFullName = readBoolean(PROPERTY_SHOW_SOURCE_FULL_NAME, showSourceFullName);
        showLocation = readBoolean(PROPERTY_SHOW_LOCATION, showLocation);
        showThread = readBoolean(PROPERTY_SHOW_THREAD, showThread);
        applyStream();
        multiline = readChoice(PROPERTY_MULTILINE, MULTILINE_MODES);
        applyExceptionSettings(Collections.<String>emptyList(), 0);
        applyDateFormat();
        rules = new Rules(readLevelRules());
        isEnabledOverridden = LoggerIntrospection.overridesIsEnabled(this, ConsoleLogger.class);
    }

    /**
     * The defaults of agent mode, for the console of the default logger when a coding agent runs the program: the
     * level {@code WARN} instead of {@code TRACE}, also for {@link #resetLevels()}, no date, one line per entry
     * ({@code multiline=escape}), and stack traces without the frames of {@link Agent#EXCLUDE}, at most
     * {@link Agent#MAX_FRAMES} of each throwable. A setting that is given
     * wins; an invalid one gives these defaults. The settings are read again quietly: the constructor has reported
     * the invalid ones.
     *
     * @return {@code true} if {@code nanolog.level} keeps the level
     */
    synchronized boolean useAgentDefaults() {
        defaultThreshold = LogEntry.LogEntryLevel.WARN.ordinal();
        return quietly(() -> {
            showDate = readBoolean(PROPERTY_SHOW_DATE, false);
            multiline = readChoice(PROPERTY_MULTILINE, ESCAPE, KEEP, INDENT);
            applyExceptionSettings(Agent.EXCLUDE, Agent.MAX_FRAMES);
            applyThreshold(defaultThreshold);
            return applyGlobalLevel();
        });
    }

    /**
     * Applies {@code nanolog.level}.
     *
     * @return {@code true} if it is set to a valid level
     */
    private boolean applyGlobalLevel() {
        String value = getProperty(PROPERTY_LEVEL);
        if (value != null) {
            int threshold = parseThreshold(PROPERTY_LEVEL, value);
            if (threshold != NO_RULE) {
                applyThreshold(threshold);
                return true;
            }
        }
        return false;
    }

    private void applyThreshold(int threshold) {
        traceEnabled = isAtLeast(LogEntry.LogEntryLevel.TRACE, threshold);
        debugEnabled = isAtLeast(LogEntry.LogEntryLevel.DEBUG, threshold);
        infoEnabled = isAtLeast(LogEntry.LogEntryLevel.INFO, threshold);
        warnEnabled = isAtLeast(LogEntry.LogEntryLevel.WARN, threshold);
        errorEnabled = isAtLeast(LogEntry.LogEntryLevel.ERROR, threshold);
    }

    private void applyStream() {
        String value = getProperty(PROPERTY_STREAM);
        if (value != null) {
            int threshold = parseStream(value);
            if (threshold == NO_RULE) {
                printDiagnostic("Invalid value \"" + value + "\" of \"" + PROPERTY_STREAM + "\": expected " + EXPECTED_STREAMS);
            } else {
                errThreshold = threshold;
            }
        }
    }

    private static final String EXPECTED_STREAMS = "out, err or err@<level>";
    private static final String ERR_AT = "err@";

    /**
     * The ordinal of the lowest level a stream setting sends to {@code System.err}; {@link #NO_RULE} if the setting is
     * invalid.
     */
    private static int parseStream(String stream) {
        String value = stream.trim();
        if ("out".equalsIgnoreCase(value)) {
            return Integer.MAX_VALUE;
        }
        if ("err".equalsIgnoreCase(value)) {
            // every level from the lowest one
            value = ERR_AT + LogEntry.LogEntryLevel.TRACE.name();
        }
        if (value.regionMatches(true, 0, ERR_AT, 0, ERR_AT.length())) {
            return thresholdNamed(value.substring(ERR_AT.length()));
        }
        return NO_RULE;
    }

    private void applyDateFormat() {
        String pattern = getProperty(PROPERTY_DATE_FORMAT);
        if (pattern != null) {
            try {
                dateFormat = new SimpleDateFormat(pattern);
            } catch (IllegalArgumentException e) {
                printDiagnostic("Invalid value \"" + pattern + "\" of \"" + PROPERTY_DATE_FORMAT + "\": " + e.getMessage());
            }
        }
    }

    private static Map<String, Integer> readLevelRules() {
        Map<String, Integer> levelRules = new HashMap<>();
        String list = getProperty(PROPERTY_LEVELS);
        if (list != null) {
            for (String item : list.split(",")) {
                String rule = item.trim();
                if (rule.isEmpty()) {
                    continue;
                }
                int separator = rule.indexOf('=');
                if (separator <= 0) {
                    printDiagnostic("Invalid item \"" + rule + "\" of \"" + PROPERTY_LEVELS + "\": expected <package or class>=<level>");
                    continue;
                }
                addLevelRule(levelRules, rule.substring(0, separator).trim(), PROPERTY_LEVELS, rule.substring(separator + 1));
            }
        }
        // single properties come last, so they override an entry of the list for the same name
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith(LEVEL_RULE_PREFIX)) {
                addLevelRule(levelRules, key.substring(LEVEL_RULE_PREFIX.length()), key, System.getProperty(key));
            }
        }
        return levelRules;
    }

    private static void addLevelRule(Map<String, Integer> levelRules, String name, String property, String value) {
        int threshold = parseThreshold(property, value);
        if (threshold != NO_RULE) {
            levelRules.put(name, threshold);
        }
    }

    private static int parseThreshold(String property, String value) {
        int threshold = thresholdNamed(value);
        if (threshold == NO_RULE) {
            printDiagnostic("Invalid value \"" + value + "\" of \"" + property + "\": expected " + EXPECTED_LEVELS);
        }
        return threshold;
    }

    private static final String EXPECTED_LEVELS = "TRACE, DEBUG, INFO, WARN, ERROR or OFF";

    /**
     * The threshold of a level name, ignoring case and surrounding spaces; {@link #NO_RULE} for anything else.
     */
    private static int thresholdNamed(String level) {
        String name = level.trim();
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (THRESHOLDS[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return NO_RULE;
    }

    private static boolean isAtLeast(LogEntry.LogEntryLevel level, int threshold) {
        return level.ordinal() >= threshold;
    }

    /**
     * One state of the package and class rules, never modified once built, with the thresholds already resolved for
     * classes. A change builds new rules, so a thread that resolved a class against the old rules can only fill the
     * cache of the old ones.
     */
    private static final class Rules {

        /**
         * Thresholds by package or class name: the ordinal of the lowest enabled level, {@code 5} for {@code OFF}.
         */
        final Map<String, Integer> thresholds;

        /**
         * The lowest of the thresholds, {@code Integer.MAX_VALUE} without rules: a level below it is enabled for no
         * class unless its switch is on.
         */
        final int lowest;

        private final Map<Class, Integer> resolved = new ConcurrentHashMap<>();

        Rules(Map<String, Integer> thresholds) {
            this.thresholds = thresholds;
            int min = Integer.MAX_VALUE;
            for (int threshold : thresholds.values()) {
                min = Math.min(min, threshold);
            }
            this.lowest = min;
        }

        int thresholdOf(Class sourceClass) {
            return resolved.computeIfAbsent(sourceClass, this::findThreshold);
        }

        /**
         * The threshold of the most specific rule for the class: the class itself, then its enclosing classes, then
         * its packages from the innermost outwards. {@link #NO_RULE} if no rule matches.
         */
        private int findThreshold(Class sourceClass) {
            String name = sourceClass.getName();
            while (true) {
                Integer threshold = thresholds.get(name);
                if (threshold != null) {
                    return threshold;
                }
                int cut = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
                if (cut == -1) {
                    return NO_RULE;
                }
                name = name.substring(0, cut);
            }
        }
    }

    /**
     * Prints {@link #createLogString(LogEntry)} as one line to {@link #getOutputStream(LogEntry)}.
     *
     * @param entry the entry to write
     */
    @Override
    public void log(LogEntry entry) {
        getOutputStream(entry).println(createLogString(entry));
    }

    /**
     * A package or class rule, from the properties or set at run time, decides first; without one, the switch of the
     * level does.
     *
     * @param level       the level of the entry
     * @param sourceClass the source class; {@code null} follows the switches
     * @return {@code true} if the entry is logged
     */
    @Override
    public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        if (sourceClass != null) {
            int threshold = rules.thresholdOf(sourceClass);
            if (threshold != NO_RULE) {
                return isAtLeast(level, threshold);
            }
        }
        return isSwitchedOn(level);
    }

    /**
     * {@code false} only when the switch of the level is off and no package or class rule enables it, so {@code LOG}
     * skips such a call before it walks the stack. A subclass that overrides {@link #isEnabled} without overriding
     * this method always gets {@code true}.
     *
     * @param level the level
     * @return {@code false} if no entry of the level is logged, whatever its source class
     */
    @Override
    public boolean mayBeEnabled(LogEntry.LogEntryLevel level) {
        return isEnabledOverridden || isAtLeast(level, rules.lowest) || isSwitchedOn(level);
    }

    private boolean isSwitchedOn(LogEntry.LogEntryLevel level) {
        switch (level) {
            case TRACE:
                return isTraceEnabled();
            case DEBUG:
                return isDebugEnabled();
            case INFO:
                return isInfoEnabled();
            case WARN:
                return isWarnEnabled();
            default:
//            case ERROR:
                return isErrorEnabled();
        }
    }

    /**
     * Sets the five level switches at once: this level and above on, the levels below off, every level off for
     * {@code OFF}. Packages and classes with a rule of their own keep it. Takes effect on the next call in every
     * thread.
     *
     * @param level {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR} or {@code OFF}, ignoring
     *              case and surrounding spaces; anything else prints a diagnostic and changes nothing
     * @return this logger, for chaining
     */
    @Override
    public synchronized ConsoleLogger setLevel(String level) {
        int threshold = parseLevel("setLevel", null, level);
        if (threshold != NO_RULE) {
            applyThreshold(threshold);
        }
        return this;
    }

    /**
     * Sets the five level switches at once, as {@link #setLevel(String)} does.
     *
     * @param level the lowest level to log; {@code null} prints a diagnostic and changes nothing
     * @return this logger, for chaining
     */
    @Override
    public ConsoleLogger setLevel(LogEntry.LogEntryLevel level) {
        return setLevel(Objects.toString(level, null));
    }

    /**
     * Sets the level of a package or class, as {@code nanolog.level.<name>} does, and replaces its rule if it has
     * one. Takes effect on the next call in every thread.
     *
     * @param name  the package or class; surrounding spaces are ignored
     * @param level {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR} or {@code OFF}, ignoring
     *              case and surrounding spaces
     * @return this logger, for chaining
     */
    @Override
    public ConsoleLogger setLevel(String name, String level) {
        int threshold = parseRule("setLevel", name, level);
        if (threshold != NO_RULE) {
            replaceRule(name.trim(), threshold);
        }
        return this;
    }

    /**
     * Sets the level of a package or class, as {@link #setLevel(String, String)} does.
     *
     * @param name  the package or class; surrounding spaces are ignored
     * @param level the lowest level to log for it
     * @return this logger, for chaining
     */
    @Override
    public ConsoleLogger setLevel(String name, LogEntry.LogEntryLevel level) {
        return setLevel(name, Objects.toString(level, null));
    }

    /**
     * Removes the rule of a package or class, whether it was configured or set at run time.
     *
     * @param name the package or class; a name without a rule is not an error
     * @return this logger, for chaining
     */
    @Override
    public ConsoleLogger clearLevel(String name) {
        if (isValidName("clearLevel", name)) {
            replaceRule(name.trim(), null);
        }
        return this;
    }

    /**
     * Restores the level switches and the package and class rules from the settings, as the constructor read them,
     * but with the current values of the system properties and environment variables. Undoes the level setters and
     * every rule set at run time; the display options stay as they are. Without {@code nanolog.level} every level is
     * on again, or {@code WARN} and above for the console of agent mode.
     *
     * @return this logger, for chaining
     */
    @Override
    public synchronized ConsoleLogger resetLevels() {
        applyThreshold(defaultThreshold);
        applyGlobalLevel();
        rules = new Rules(readLevelRules());
        return this;
    }

    /**
     * Sets the level of a package or class until the returned override is closed, as
     * {@link #withLevel(String, String)} does.
     *
     * @param name  the package or class
     * @param level the lowest level to log for it while the override is open
     * @return the override to close
     */
    @Override
    public LOG.LevelOverride withLevel(String name, LogEntry.LogEntryLevel level) {
        return withLevel(name, Objects.toString(level, null));
    }

    /**
     * Sets the level of a package or class until the returned override is closed, then restores the rule it had.
     *
     * @param name  the package or class
     * @param level {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR} or {@code OFF}, ignoring
     *              case and surrounding spaces
     * @return the override to close
     */
    @Override
    public LOG.LevelOverride withLevel(String name, String level) {
        int threshold = parseRule("withLevel", name, level);
        if (threshold == NO_RULE) {
            return () -> {
            };
        }
        String rule = name.trim();
        Integer previous = replaceRule(rule, threshold);
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                replaceRule(rule, previous);
            }
        };
    }

    /**
     * Swaps in rules that differ in one name. Synchronized, so two concurrent changes cannot both start from the same
     * rules and lose one of them.
     *
     * @param name      the package or class
     * @param threshold the new threshold; {@code null} removes the rule
     * @return the threshold the name had; {@code null} if it had no rule
     */
    private synchronized Integer replaceRule(String name, Integer threshold) {
        Map<String, Integer> thresholds = new HashMap<>(rules.thresholds);
        Integer previous = threshold == null ? thresholds.remove(name) : thresholds.put(name, threshold);
        rules = new Rules(thresholds);
        return previous;
    }

    /**
     * Checks a global level given to a level method, here and in {@link CompositeLogger}.
     */
    static boolean isValidLevel(String operation, String level) {
        return parseLevel(operation, null, level) != NO_RULE;
    }

    /**
     * Checks a package or class name and its level given to a level method.
     */
    static boolean isValidRule(String operation, String name, String level) {
        return parseRule(operation, name, level) != NO_RULE;
    }

    /**
     * Checks a package or class name given to a level method.
     */
    static boolean isValidName(String operation, String name) {
        if (name == null || name.trim().isEmpty()) {
            printDiagnostic("Invalid package or class name " + (name == null ? "null" : "\"" + name + "\"") +
                            " in " + operation + "; nothing changed");
            return false;
        }
        return true;
    }

    /**
     * The threshold of a package or class rule given to a level method; {@link #NO_RULE}, after a diagnostic, if the
     * name or the level is invalid.
     */
    private static int parseRule(String operation, String name, String level) {
        return isValidName(operation, name) ? parseLevel(operation, name, level) : NO_RULE;
    }

    /**
     * The threshold of a level given to a level method; {@link #NO_RULE}, after a diagnostic, if it is invalid. The
     * name is the package or class the level is for, {@code null} for the global level.
     */
    private static int parseLevel(String operation, String name, String level) {
        String target = name == null ? "" : " for \"" + name.trim() + "\"";
        if (level == null) {
            printDiagnostic("Invalid level null" + target + " in " + operation + "; nothing changed");
            return NO_RULE;
        }
        int threshold = thresholdNamed(level);
        if (threshold == NO_RULE) {
            printDiagnostic("Invalid level \"" + level + "\"" + target + " in " + operation + ": expected " + EXPECTED_LEVELS +
                            "; nothing changed");
        }
        return threshold;
    }

    /**
     * The stream of an entry, as {@link #setStream(String)} chooses it: by default {@code System.err} for
     * {@code ERROR} and {@code System.out} otherwise. An entry without a level goes to {@code System.out}.
     *
     * @param entry the entry to write
     * @return the stream
     */
    protected PrintStream getOutputStream(LogEntry entry) {
        LogEntry.LogEntryLevel level = entry.getLevel();
        return level != null && level.ordinal() >= errThreshold ? System.err : System.out;
    }

    /**
     * Builds the text of an entry from the {@code write*} methods, in this order: level, date, thread, source,
     * message, skipped count, stack trace.
     *
     * @param entry the entry to write
     * @return the text without the final line break
     */
    protected String createLogString(LogEntry entry) {

        StringWriter out = new StringWriter();
        writeLevel(out, entry);
        writeDate(out, entry);
        writeThread(out, entry);
        writeSource(out, entry);
        writeMessage(out, entry);
        writeSkipped(out, entry);
        writeThrowable(out, entry);

        return applyMultiline(out.toString());
    }

    /**
     * Writes the stack trace of the entry's exception, if there is one.
     *
     * @param out   the text being built
     * @param entry the entry to write
     */
    protected void writeThrowable(StringWriter out, LogEntry entry) {
        if (entry.getThrowable() != null) {
            addDelimiter(out);
            out.append(stackTraceOf(entry.getThrowable()));
        }
    }

    /**
     * The stack trace of a throwable as this logger writes it: the output of {@code printStackTrace} when neither
     * {@code exceptionExclude} nor {@code exceptionMaxFrames} is set, a shortened one otherwise. A failure while
     * shortening falls back to {@code printStackTrace}.
     */
    String stackTraceOf(Throwable throwable) {
        if (exceptionExclude.isEmpty() && exceptionMaxFrames == 0) {
            return printedStackTrace(throwable);
        }
        try {
            return ShortStackTrace.of(throwable, exceptionExclude, exceptionMaxFrames);
        } catch (Throwable t) {
            return printedStackTrace(throwable);
        }
    }

    /**
     * Returns the package and class prefixes whose stack frames are excluded, as an unmodifiable list.
     *
     * @return the package and class prefixes whose stack frames are excluded; empty by default
     */
    public List<String> getExceptionExclude() {
        return exceptionExclude;
    }

    /**
     * Shortens stack traces by the frames of some packages or classes, such as test runners, reflection or proxies:
     * a run of two or more consecutive frames that match becomes one line, {@code ... 14 frames excluded}. The first
     * frame of each throwable is always kept. A prefix matches whole name segments, like a level rule:
     * {@code org.junit} covers {@code org.junit.runners.ParentRunner}, not {@code org.junitx}. Also set by
     * {@code nanolog.exception.exclude}; applies to the {@code stackTrace} field of {@link JsonLogger} too.
     *
     * @param prefixes package or class names; blank and {@code null} ones are ignored, none leaves traces unchanged
     * @return this logger, for chaining
     */
    public ConsoleLogger setExceptionExclude(String... prefixes) {
        this.exceptionExclude = prefixes == null ? Collections.<String>emptyList() : parsePrefixes(prefixes);
        return this;
    }

    /**
     * Returns the most stack frames printed per throwable.
     *
     * @return the most stack frames printed per throwable; {@code 0}, the default, for no limit
     */
    public int getExceptionMaxFrames() {
        return exceptionMaxFrames;
    }

    /**
     * Prints at most this many frames of each throwable, its causes and suppressed exceptions counted separately;
     * the rest becomes {@code ... N more}. Also set by {@code nanolog.exception.maxFrames}; applies to the
     * {@code stackTrace} field of {@link JsonLogger} too.
     *
     * @param maxFrames the most frames per throwable; zero or less for no limit
     * @return this logger, for chaining
     */
    public ConsoleLogger setExceptionMaxFrames(int maxFrames) {
        this.exceptionMaxFrames = Math.max(0, maxFrames);
        return this;
    }

    private static List<String> parsePrefixes(String[] items) {
        List<String> prefixes = new ArrayList<>();
        for (String item : items) {
            if (item != null && !item.trim().isEmpty()) {
                prefixes.add(item.trim());
            }
        }
        return Collections.unmodifiableList(prefixes);
    }

    private void applyExceptionSettings(List<String> defaultExclude, int defaultMaxFrames) {
        exceptionExclude = defaultExclude;
        exceptionMaxFrames = defaultMaxFrames;
        String exclude = getProperty(PROPERTY_EXCEPTION_EXCLUDE);
        if (exclude != null) {
            exceptionExclude = parsePrefixes(exclude.split(","));
        }
        String maxFrames = getProperty(PROPERTY_EXCEPTION_MAX_FRAMES);
        if (maxFrames != null) {
            int frames = -1;
            try {
                frames = Integer.parseInt(maxFrames.trim());
            } catch (NumberFormatException e) {
                // reported below, like a negative number
            }
            if (frames < 0) {
                printDiagnostic("Invalid value \"" + maxFrames + "\" of \"" + PROPERTY_EXCEPTION_MAX_FRAMES +
                                "\": expected a number of frames, 0 for no limit");
            } else {
                exceptionMaxFrames = frames;
            }
        }
    }

    /**
     * The output of {@code printStackTrace}, or {@code [FAILED printStackTrace()]} if the throwable's own
     * {@code toString()} or stack trace code throws: logging never throws.
     */
    static String printedStackTrace(Throwable throwable) {
        StringWriter out = new StringWriter();
        try (PrintWriter writer = new PrintWriter(out)) {
            throwable.printStackTrace(writer);
        } catch (Throwable t) {
            return "[FAILED printStackTrace()]";
        }
        return out.toString();
    }

    /**
     * Writes {@code [skipped N]}, the number of calls a rate-limited statement skipped before this entry, if any.
     *
     * @param out   the text being built
     * @param entry the entry to write
     */
    protected void writeSkipped(StringWriter out, LogEntry entry) {
        if (entry.getSkippedCount() > 0) {
            addDelimiter(out);
            out.append("[skipped ").append(String.valueOf(entry.getSkippedCount())).append(']');
        }
    }

    /**
     * Writes the formatted message, unless the entry has no message or it is empty.
     *
     * @param out   the text being built
     * @param entry the entry to write
     */
    protected void writeMessage(StringWriter out, LogEntry entry) {
        if (entry.getMessage() != null) {
            String formattedMessage = entry.getFormattedMessage();
            if (!formattedMessage.isEmpty()) {
                addDelimiter(out);
                out.append(formattedMessage);
            }
        }
    }

    /**
     * Writes {@code [thread name]} when {@code showThread} is on.
     *
     * @param out   the text being built
     * @param entry the entry to write
     */
    protected void writeThread(StringWriter out, LogEntry entry) {
        if (isShowThread()) {
            addDelimiter(out);
            out.append('[').append(entry.getThreadName()).append(']');
        }
    }

    /**
     * Writes {@code [SourceClass]}, with the call site inside the brackets when {@code showLocation} is on and always
     * for an entry of {@code LOG.dbg(...)}.
     *
     * @param out   the text being built
     * @param entry the entry to write
     */
    protected void writeSource(StringWriter out, LogEntry entry) {
        Class clz = entry.getSourceClass();
        if (isShowSource() && clz != null) {
            addDelimiter(out);
            out.append("[");
            out.append(getSourceName(clz));
            StackTraceElement caller = entry.getCaller();
            if ((isShowLocation() || entry.isDbg()) && caller != null) {
                out.append(getLocation(caller));
            }
            out.append("]");
        }
    }

    /**
     * The name of the source class as printed: simple or fully qualified. Anonymous and local classes, which have no
     * canonical name and an empty or partial simple name, are printed by their binary name ({@code Foo$1}).
     *
     * @param sourceClass the source class, never {@code null}
     * @return the name
     */
    protected String getSourceName(Class sourceClass) {
        return isShowSourceFullName() ? LogEntry.fullName(sourceClass) : LogEntry.simpleName(sourceClass);
    }

    /**
     * The call site appended to the source name: {@code .method(File.java:line)}, the form stack traces use, so IDEs
     * and terminals turn it into a link.
     *
     * @param caller the frame of the logging call
     * @return the text to append
     */
    protected String getLocation(StackTraceElement caller) {
        String file;
        if (caller.getFileName() == null) {
            file = "Unknown Source";
        } else if (caller.getLineNumber() >= 0) {
            file = caller.getFileName() + ":" + caller.getLineNumber();
        } else {
            file = caller.getFileName();
        }
        return "." + caller.getMethodName() + "(" + file + ")";
    }

    /**
     * Writes the timestamp of {@link #getCurrentDate()} in the date format.
     *
     * @param out   the text being built
     * @param entry the entry to write
     */
    protected void writeDate(StringWriter out, LogEntry entry) {
        SimpleDateFormat format = getDateFormat();
        if (isShowDate() && format != null) {
            addDelimiter(out);
            String date;
            // SimpleDateFormat is not thread-safe and the instance is shared by all logging threads
            synchronized (format) {
                date = format.format(getCurrentDate());
            }
            out.append(date);
        }
    }

    /**
     * The date written for an entry; the time of writing by default. Tests override it for a fixed date.
     *
     * @return the date to write
     */
    protected Date getCurrentDate() {
        return new Date();
    }

    /**
     * Writes the level name, or {@code DBG} for an entry of {@code LOG.dbg(...)}.
     *
     * @param out   the text being built
     * @param entry the entry to write
     */
    protected void writeLevel(StringWriter out, LogEntry entry) {
        if (isShowLevel() && entry.getLevel() != null) {
            out.append(entry.isDbg() ? "DBG" : entry.getLevel().name());
        }
    }

    /**
     * Writes the space between two parts, unless nothing has been written yet.
     *
     * @param out the text being built
     */
    protected void addDelimiter(StringWriter out) {
        if (out.getBuffer().length() != 0) {
            out.append(' ');
        }
    }

    /**
     * Returns the format of the timestamp; {@code null} prints no timestamp.
     *
     * @return the format of the timestamp; {@code null} prints no timestamp
     */
    public SimpleDateFormat getDateFormat() {
        return dateFormat;
    }

    /**
     * Sets the format of the timestamp, {@code dd.MM.yyyy HH:mm:ss} by default; also set by
     * {@code nanolog.console.dateFormat}. Formatting holds the format's monitor, so one instance may be shared.
     *
     * @param dateFormat the new format; {@code null} prints no timestamp
     * @return this logger, for chaining
     */
    public ConsoleLogger setDateFormat(SimpleDateFormat dateFormat) {
        this.dateFormat = dateFormat;
        return this;
    }

    /**
     * Returns {@code true} if the level name is printed.
     *
     * @return {@code true} if the level name is printed
     */
    public boolean isShowLevel() {
        return showLevel;
    }

    /**
     * Whether to print the level name. On by default. Also set by {@code nanolog.console.showLevel}.
     *
     * @param showLevel the new value
     * @return this logger, for chaining
     */
    public ConsoleLogger setShowLevel(boolean showLevel) {
        this.showLevel = showLevel;
        return this;
    }

    /**
     * Returns {@code true} if the timestamp is printed.
     *
     * @return {@code true} if the timestamp is printed
     */
    public boolean isShowDate() {
        return showDate;
    }

    /**
     * Whether to print the timestamp. On by default. Also set by {@code nanolog.console.showDate}.
     *
     * @param showDate the new value
     * @return this logger, for chaining
     */
    public ConsoleLogger setShowDate(boolean showDate) {
        this.showDate = showDate;
        return this;
    }

    /**
     * Returns {@code true} if the source class is printed.
     *
     * @return {@code true} if the source class is printed
     */
    public boolean isShowSource() {
        return showSource;
    }

    /**
     * Whether to print the source class in square brackets. On by default. Also set by {@code nanolog.console.showSource}.
     *
     * @param showSource the new value
     * @return this logger, for chaining
     */
    public ConsoleLogger setShowSource(boolean showSource) {
        this.showSource = showSource;
        return this;
    }

    /**
     * Returns {@code true} if the source class is printed with its fully qualified name.
     *
     * @return {@code true} if the source class is printed with its fully qualified name
     */
    public boolean isShowSourceFullName() {
        return showSourceFullName;
    }

    /**
     * Whether to print the fully qualified name of the source class instead of the simple one. Off by default. Also set by {@code nanolog.console.showSourceFullName}.
     *
     * @param showSourceFullName the new value
     * @return this logger, for chaining
     */
    public ConsoleLogger setShowSourceFullName(boolean showSourceFullName) {
        this.showSourceFullName = showSourceFullName;
        return this;
    }

    /**
     * Returns {@code true} if the call site is printed after the source class.
     *
     * @return {@code true} if the call site is printed after the source class
     */
    public boolean isShowLocation() {
        return showLocation;
    }

    /**
     * Appends the call site, {@code .method(File.java:line)}, to the source class. Off by default; also set by
     * {@code nanolog.console.showLocation}. Has no effect on entries logged with an explicit source class, since
     * those carry no caller frame.
     *
     * @param showLocation the new value
     * @return this logger, for chaining
     */
    public ConsoleLogger setShowLocation(boolean showLocation) {
        this.showLocation = showLocation;
        return this;
    }

    /**
     * Returns what is done with line breaks and control characters: {@code keep}, {@code indent} or {@code escape}.
     *
     * @return the mode; {@code keep} by default
     */
    public String getMultiline() {
        return multiline;
    }

    /**
     * Chooses what an entry does with line breaks and control characters, in its message, its stack trace and any
     * other part. {@code keep}, the default, writes them as they are. {@code indent} starts every line of an entry
     * after the first with a tab, as stack frames are, so a line that does not start with a tab begins an entry and a
     * message cannot forge one. {@code escape} writes {@code \r} and {@code \n} as these two characters, so an entry is
     * one physical line, ready for {@code grep} and {@code tail}. Both modes write other control characters, such as the
     * escape sequences of a terminal, as {@code \}{@code u001b}, keep the tab and drop the line breaks at the end. Also set by
     * {@code nanolog.console.multiline}; {@link JsonLogger} escapes its lines anyway.
     *
     * @param multiline {@code keep}, {@code indent} or {@code escape}, ignoring case and surrounding spaces; anything
     *                  else prints a diagnostic and changes nothing
     * @return this logger, for chaining
     */
    public ConsoleLogger setMultiline(String multiline) {
        String mode = multilineNamed(multiline);
        if (mode == null) {
            printDiagnostic("Invalid value " + (multiline == null ? "null" : "\"" + multiline + "\"") +
                            " in setMultiline: expected " + EXPECTED_MULTILINE + "; nothing changed");
        } else {
            this.multiline = mode;
        }
        return this;
    }

    private static String multilineNamed(String value) {
        if (value != null) {
            for (String mode : MULTILINE_MODES) {
                if (mode.equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
        }
        return null;
    }

    /**
     * The entry as {@link #getMultiline()} says: unchanged for {@code keep}.
     */
    private String applyMultiline(String text) {
        String mode = multiline;
        if (KEEP.equals(mode)) {
            return text;
        }
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
            end--;
        }
        boolean indent = INDENT.equals(mode);
        StringBuilder result = new StringBuilder(end + 16);
        // the line breaks at the end are gone, so a line break is always followed by another character
        for (int i = 0; i < end; i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                if (indent) {
                    if (c == '\r' && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    result.append(System.lineSeparator());
                    if (text.charAt(i + 1) != '\t') {
                        result.append('\t');
                    }
                } else {
                    result.append(c == '\r' ? "\\r" : "\\n");
                }
            } else if (c != '\t' && (Character.isISOControl(c) || c == ' ' || c == ' ')) {
                result.append(String.format("\\u%04x", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Returns which levels go to {@code System.err}: {@code out}, {@code err} or {@code err@LEVEL}, in that form.
     *
     * @return the stream setting; {@code err@ERROR} by default
     */
    public String getStream() {
        if (errThreshold == LogEntry.LogEntryLevel.TRACE.ordinal()) {
            return "err";
        }
        if (errThreshold > LogEntry.LogEntryLevel.ERROR.ordinal()) {
            return "out";
        }
        return ERR_AT + THRESHOLDS[errThreshold];
    }

    /**
     * Chooses the stream of each entry: {@code out} writes everything to {@code System.out}, {@code err} everything to
     * {@code System.err}, {@code err@LEVEL} that level and above to {@code System.err} and the rest to
     * {@code System.out}. The default {@code err@ERROR} sends only errors to {@code System.err}. One stream keeps
     * errors in a pipe ({@code java -jar app.jar | jq}) and keeps the order of the lines when the two streams are
     * captured apart. Also set by {@code nanolog.console.stream}; applies to {@link JsonLogger} too.
     *
     * @param stream {@code out}, {@code err} or {@code err@LEVEL}, ignoring case and spaces around the parts; anything
     *               else prints a diagnostic and changes nothing
     * @return this logger, for chaining
     */
    public ConsoleLogger setStream(String stream) {
        int threshold = stream == null ? NO_RULE : parseStream(stream);
        if (threshold == NO_RULE) {
            printDiagnostic("Invalid stream " + (stream == null ? "null" : "\"" + stream + "\"") + " in setStream: expected " +
                            EXPECTED_STREAMS + "; nothing changed");
        } else {
            errThreshold = threshold;
        }
        return this;
    }

    /**
     * Returns {@code true} if the name of the thread that logged is printed.
     *
     * @return {@code true} if the name of the thread that logged is printed
     */
    public boolean isShowThread() {
        return showThread;
    }

    /**
     * Whether to print the name of the thread that logged, as {@code [main]} between the date and the source class, so
     * the interleaved lines of a thread pool can be told apart. Off by default; also set by
     * {@code nanolog.console.showThread}. {@link JsonLogger} always writes the thread.
     *
     * @param showThread the new value
     * @return this logger, for chaining
     */
    public ConsoleLogger setShowThread(boolean showThread) {
        this.showThread = showThread;
        return this;
    }

    /**
     * Returns {@code true} if {@code TRACE} entries are logged.
     *
     * @return {@code true} if {@code TRACE} entries are logged
     */
    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    /**
     * Whether to log {@code TRACE} entries. Packages and classes with a rule of their own ({@code nanolog.level.<name>}) follow that rule instead. On by default, or as set by {@code nanolog.level}.
     *
     * @param traceEnabled the new value
     * @return this logger, for chaining
     */
    public ConsoleLogger setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
        return this;
    }

    /**
     * Returns {@code true} if {@code DEBUG} entries are logged.
     *
     * @return {@code true} if {@code DEBUG} entries are logged
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Whether to log {@code DEBUG} entries. Packages and classes with a rule of their own ({@code nanolog.level.<name>}) follow that rule instead. On by default, or as set by {@code nanolog.level}.
     *
     * @param debugEnabled the new value
     * @return this logger, for chaining
     */
    public ConsoleLogger setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        return this;
    }

    /**
     * Returns {@code true} if {@code INFO} entries are logged.
     *
     * @return {@code true} if {@code INFO} entries are logged
     */
    public boolean isInfoEnabled() {
        return infoEnabled;
    }

    /**
     * Whether to log {@code INFO} entries. Packages and classes with a rule of their own ({@code nanolog.level.<name>}) follow that rule instead. On by default, or as set by {@code nanolog.level}.
     *
     * @param infoEnabled the new value
     * @return this logger, for chaining
     */
    public ConsoleLogger setInfoEnabled(boolean infoEnabled) {
        this.infoEnabled = infoEnabled;
        return this;
    }

    /**
     * Returns {@code true} if {@code WARN} entries are logged.
     *
     * @return {@code true} if {@code WARN} entries are logged
     */
    public boolean isWarnEnabled() {
        return warnEnabled;
    }

    /**
     * Whether to log {@code WARN} entries. Packages and classes with a rule of their own ({@code nanolog.level.<name>}) follow that rule instead. On by default, or as set by {@code nanolog.level}.
     *
     * @param warnEnabled the new value
     * @return this logger, for chaining
     */
    public ConsoleLogger setWarnEnabled(boolean warnEnabled) {
        this.warnEnabled = warnEnabled;
        return this;
    }

    /**
     * Returns {@code true} if {@code ERROR} entries are logged.
     *
     * @return {@code true} if {@code ERROR} entries are logged
     */
    public boolean isErrorEnabled() {
        return errorEnabled;
    }

    /**
     * Whether to log {@code ERROR} entries. Packages and classes with a rule of their own ({@code nanolog.level.<name>}) follow that rule instead. On by default, or as set by {@code nanolog.level}.
     *
     * @param errorEnabled the new value
     * @return this logger, for chaining
     */
    public ConsoleLogger setErrorEnabled(boolean errorEnabled) {
        this.errorEnabled = errorEnabled;
        return this;
    }
}
