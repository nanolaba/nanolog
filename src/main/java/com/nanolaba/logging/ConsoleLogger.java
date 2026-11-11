package com.nanolaba.logging;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_LEVEL;
import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_LEVELS;
import static com.nanolaba.logging.DefaultLoggerCreator.getProperty;
import static com.nanolaba.logging.DefaultLoggerCreator.printDiagnostic;
import static com.nanolaba.logging.DefaultLoggerCreator.readBoolean;

public class ConsoleLogger implements ILogger {

    public static final String PROPERTY_SHOW_LEVEL = "nanolog.console.showLevel";
    public static final String PROPERTY_SHOW_DATE = "nanolog.console.showDate";
    public static final String PROPERTY_SHOW_SOURCE = "nanolog.console.showSource";
    public static final String PROPERTY_SHOW_SOURCE_FULL_NAME = "nanolog.console.showSourceFullName";
    public static final String PROPERTY_SHOW_LOCATION = "nanolog.console.showLocation";
    public static final String PROPERTY_DATE_FORMAT = "nanolog.console.dateFormat";

    private static final String LEVEL_RULE_PREFIX = PROPERTY_LEVEL + ".";
    private static final String[] THRESHOLDS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF"};
    private static final int NO_RULE = -1;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    private boolean showLevel = true;
    private boolean showDate = true;
    private boolean showSource = true;
    private boolean showSourceFullName = false;
    private boolean showLocation = false;

    private boolean traceEnabled = true;
    private boolean debugEnabled = true;
    private boolean infoEnabled = true;
    private boolean warnEnabled = true;
    private boolean errorEnabled = true;

    /**
     * Thresholds by package or class name, from {@code nanolog.levels} and {@code nanolog.level.<name>}; fixed at
     * construction. The value is the ordinal of the lowest enabled level, {@code 5} for {@code OFF}.
     */
    private final Map<String, Integer> levelRules = new HashMap<>();
    private final Map<Class, Integer> thresholdCache = new ConcurrentHashMap<>();

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
        applyDateFormat();
        readLevelRules();
    }

    private void applyGlobalLevel() {
        String value = getProperty(PROPERTY_LEVEL);
        if (value != null) {
            int threshold = parseThreshold(PROPERTY_LEVEL, value);
            if (threshold != NO_RULE) {
                traceEnabled = isAtLeast(LogEntry.LogEntryLevel.TRACE, threshold);
                debugEnabled = isAtLeast(LogEntry.LogEntryLevel.DEBUG, threshold);
                infoEnabled = isAtLeast(LogEntry.LogEntryLevel.INFO, threshold);
                warnEnabled = isAtLeast(LogEntry.LogEntryLevel.WARN, threshold);
                errorEnabled = isAtLeast(LogEntry.LogEntryLevel.ERROR, threshold);
            }
        }
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

    private void readLevelRules() {
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
                addLevelRule(rule.substring(0, separator).trim(), PROPERTY_LEVELS, rule.substring(separator + 1));
            }
        }
        // single properties come last, so they override an entry of the list for the same name
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith(LEVEL_RULE_PREFIX)) {
                addLevelRule(key.substring(LEVEL_RULE_PREFIX.length()), key, System.getProperty(key));
            }
        }
    }

    private void addLevelRule(String name, String property, String value) {
        int threshold = parseThreshold(property, value);
        if (threshold != NO_RULE) {
            levelRules.put(name, threshold);
        }
    }

    private static int parseThreshold(String property, String value) {
        String name = value.trim();
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (THRESHOLDS[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        printDiagnostic("Invalid value \"" + value + "\" of \"" + property + "\": expected TRACE, DEBUG, INFO, WARN, ERROR or OFF");
        return NO_RULE;
    }

    private static boolean isAtLeast(LogEntry.LogEntryLevel level, int threshold) {
        return level.ordinal() >= threshold;
    }

    /**
     * The threshold of the most specific rule for the class: the class itself, then its enclosing classes, then
     * its packages from the innermost outwards. {@link #NO_RULE} if no rule matches.
     */
    private int findThreshold(Class sourceClass) {
        String name = sourceClass.getName();
        while (true) {
            Integer threshold = levelRules.get(name);
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

    @Override
    public void log(LogEntry entry) {
        getOutputStream(entry).println(createLogString(entry));
    }

    @Override
    public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        if (sourceClass != null) {
            int threshold = thresholdCache.computeIfAbsent(sourceClass, this::findThreshold);
            if (threshold != NO_RULE) {
                return isAtLeast(level, threshold);
            }
        }
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

    protected PrintStream getOutputStream(LogEntry entry) {
        return entry.getLevel() == LogEntry.LogEntryLevel.ERROR ? System.err : System.out;
    }

    protected String createLogString(LogEntry entry) {

        StringWriter out = new StringWriter();
        writeLevel(out, entry);
        writeDate(out, entry);
        writeSource(out, entry);
        writeMessage(out, entry);
        writeThrowable(out, entry);

        return out.toString();
    }

    protected void writeThrowable(StringWriter out, LogEntry entry) {
        if (entry.getThrowable() != null) {
            addDelimiter(out);
            out.append(stackTraceOf(entry.getThrowable()));
        }
    }

    /**
     * The output of {@code printStackTrace}, or {@code [FAILED printStackTrace()]} if the throwable's own
     * {@code toString()} or stack trace code throws: logging never throws.
     */
    static String stackTraceOf(Throwable throwable) {
        StringWriter out = new StringWriter();
        try (PrintWriter writer = new PrintWriter(out)) {
            throwable.printStackTrace(writer);
        } catch (Throwable t) {
            return "[FAILED printStackTrace()]";
        }
        return out.toString();
    }

    protected void writeMessage(StringWriter out, LogEntry entry) {
        if (entry.getMessage() != null) {
            String formattedMessage = entry.getFormattedMessage();
            if (!formattedMessage.isEmpty()) {
                addDelimiter(out);
                out.append(formattedMessage);
            }
        }
    }

    protected void writeSource(StringWriter out, LogEntry entry) {
        Class clz = entry.getSourceClass();
        if (isShowSource() && clz != null) {
            addDelimiter(out);
            out.append("[");
            out.append(getSourceName(clz));
            StackTraceElement caller = entry.getCaller();
            if (isShowLocation() && caller != null) {
                out.append(getLocation(caller));
            }
            out.append("]");
        }
    }

    protected String getSourceName(Class sourceClass) {
        return isShowSourceFullName() ? sourceClass.getCanonicalName() : sourceClass.getSimpleName();
    }

    /**
     * The call site appended to the source name: {@code .method(File.java:line)}, the form stack traces use, so IDEs
     * and terminals turn it into a link.
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

    protected Date getCurrentDate() {
        return new Date();
    }

    protected void writeLevel(StringWriter out, LogEntry entry) {
        if (isShowLevel() && entry.getLevel() != null) {
            out.append(entry.getLevel().name());
        }
    }

    protected void addDelimiter(StringWriter out) {
        if (out.getBuffer().length() != 0) {
            out.append(' ');
        }
    }

    public SimpleDateFormat getDateFormat() {
        return dateFormat;
    }

    public ConsoleLogger setDateFormat(SimpleDateFormat dateFormat) {
        this.dateFormat = dateFormat;
        return this;
    }

    public boolean isShowLevel() {
        return showLevel;
    }

    public ConsoleLogger setShowLevel(boolean showLevel) {
        this.showLevel = showLevel;
        return this;
    }

    public boolean isShowDate() {
        return showDate;
    }

    public ConsoleLogger setShowDate(boolean showDate) {
        this.showDate = showDate;
        return this;
    }

    public boolean isShowSource() {
        return showSource;
    }

    public ConsoleLogger setShowSource(boolean showSource) {
        this.showSource = showSource;
        return this;
    }

    public boolean isShowSourceFullName() {
        return showSourceFullName;
    }

    public ConsoleLogger setShowSourceFullName(boolean showSourceFullName) {
        this.showSourceFullName = showSourceFullName;
        return this;
    }

    public boolean isShowLocation() {
        return showLocation;
    }

    /**
     * Appends the call site, {@code .method(File.java:line)}, to the source class. Off by default. Has no effect on
     * entries logged with an explicit source class, since those carry no caller frame.
     */
    public ConsoleLogger setShowLocation(boolean showLocation) {
        this.showLocation = showLocation;
        return this;
    }

    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    public ConsoleLogger setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
        return this;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public ConsoleLogger setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        return this;
    }

    public boolean isInfoEnabled() {
        return infoEnabled;
    }

    public ConsoleLogger setInfoEnabled(boolean infoEnabled) {
        this.infoEnabled = infoEnabled;
        return this;
    }

    public boolean isWarnEnabled() {
        return warnEnabled;
    }

    public ConsoleLogger setWarnEnabled(boolean warnEnabled) {
        this.warnEnabled = warnEnabled;
        return this;
    }

    public boolean isErrorEnabled() {
        return errorEnabled;
    }

    public ConsoleLogger setErrorEnabled(boolean errorEnabled) {
        this.errorEnabled = errorEnabled;
        return this;
    }
}
