package com.nanolaba.logging;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Writes one JSON object per line, for tools and agents that parse the output: {@code jq}, log collectors, scripts.
 * <pre>{@code
 * {"ts":"2026-11-11T02:30:31.412Z","level":"WARN","logger":"com.acme.PaymentService","thread":"main",
 *  "message":"Charge of 42 failed","template":"Charge of {} failed","args":["42"],
 *  "caller":{"class":"com.acme.PaymentService","method":"charge","file":"PaymentService.java","line":87},
 *  "exception":{"class":"java.io.IOException","message":"timeout","stackTrace":"java.io.IOException: timeout\n\tat ..."}}
 * }</pre>
 * (shown on several lines here; the real output is a single line). Fields without a value are left out:
 * {@code template} and {@code args} appear only for parameterized messages, {@code caller} only when the source
 * class was detected, {@code exception} only with a throwable. Line breaks and control characters are escaped, so one
 * entry is always exactly one line.
 * <p>
 * Levels, their properties ({@code nanolog.level}, package rules) and the output streams are those of
 * {@link ConsoleLogger}: {@code ERROR} goes to {@code System.err}, everything else to {@code System.out}.
 * The display options of {@code ConsoleLogger} do not apply. Select it without code with
 * {@code -Dnanolog.logger=com.nanolaba.logging.JsonLogger}.
 */
public class JsonLogger extends ConsoleLogger {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * Creates a logger configured like {@link ConsoleLogger#ConsoleLogger()}.
     */
    public JsonLogger() {
        super();
    }

    /**
     * Builds the JSON object of an entry.
     *
     * @param entry the entry to write
     * @return one line of JSON without the final line break
     */
    @Override
    protected String createLogString(LogEntry entry) {
        StringBuilder out = new StringBuilder(256);
        out.append("{\"ts\":");
        appendString(out, formatTimestamp(entry.getTimestamp()));
        if (entry.getLevel() != null) {
            appendField(out, "level", entry.getLevel().name());
        }
        if (entry.getSourceClass() != null) {
            appendField(out, "logger", entry.getSourceClass().getName());
        }
        appendField(out, "thread", entry.getThreadName());
        if (entry.getMessage() != null) {
            appendField(out, "message", entry.getFormattedMessage());
        }
        Object[] args = entry.getArgs();
        if (args != null && args.length > 0) {
            if (entry.getMessage() != null) {
                appendField(out, "template", LogEntry.safeToString(entry.getMessage()));
            }
            out.append(",\"args\":[");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                appendString(out, LogEntry.argToString(args[i]));
            }
            out.append(']');
        }
        if (entry.getCaller() != null) {
            appendCaller(out, entry.getCaller());
        }
        if (entry.getThrowable() != null) {
            appendException(out, entry.getThrowable());
        }
        return out.append('}').toString();
    }

    private static void appendCaller(StringBuilder out, StackTraceElement caller) {
        out.append(",\"caller\":{\"class\":");
        appendString(out, caller.getClassName());
        appendField(out, "method", caller.getMethodName());
        if (caller.getFileName() != null) {
            appendField(out, "file", caller.getFileName());
        }
        if (caller.getLineNumber() >= 0) {
            out.append(",\"line\":").append(caller.getLineNumber());
        }
        out.append('}');
    }

    private static void appendException(StringBuilder out, Throwable throwable) {
        out.append(",\"exception\":{\"class\":");
        appendString(out, throwable.getClass().getName());
        String message;
        try {
            message = throwable.getMessage();
        } catch (Throwable t) {
            message = "[FAILED getMessage()]";
        }
        if (message != null) {
            appendField(out, "message", message);
        }
        appendField(out, "stackTrace", stackTraceOf(throwable));
        out.append('}');
    }

    private static void appendField(StringBuilder out, String name, String value) {
        out.append(",\"").append(name).append("\":");
        appendString(out, value);
    }

    /**
     * Appends a JSON string literal: quotes, backslashes and every control character are escaped, as are U+2028 and
     * U+2029, which JavaScript treats as line breaks. Everything else, characters outside the BMP included, is
     * written as is.
     */
    static void appendString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    /**
     * ISO-8601 in UTC with milliseconds, sortable as text: {@code 2026-11-11T02:30:31.412Z}.
     */
    static String formatTimestamp(long millis) {
        return TIMESTAMP.format(Instant.ofEpochMilli(millis));
    }
}
