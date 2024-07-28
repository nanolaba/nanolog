package com.nanolaba.logging;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ConsoleLogger implements ILogger {

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    private boolean showLevel = true;
    private boolean showDate = true;
    private boolean showSource = true;
    private boolean showSourceFullName = false;

    private boolean traceEnabled = true;
    private boolean debugEnabled = true;
    private boolean infoEnabled = true;
    private boolean warnEnabled = true;
    private boolean errorEnabled = true;

    @Override
    public void log(LogEntry entry) {
        getOutputStream(entry).println(createLogString(entry));
    }

    @Override
    public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
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
            try (PrintWriter writer = new PrintWriter(out)) {
                entry.getThrowable().printStackTrace(writer);
            }
        }
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
            out.append("]");
        }
    }

    protected String getSourceName(Class sourceClass) {
        return isShowSourceFullName() ? sourceClass.getCanonicalName() : sourceClass.getSimpleName();
    }

    protected void writeDate(StringWriter out, LogEntry entry) {
        if (isShowDate() && getDateFormat() != null) {
            addDelimiter(out);
            out.append(getDateFormat().format(getCurrentDate()));
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
