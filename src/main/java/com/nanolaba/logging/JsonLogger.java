package com.nanolaba.logging;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Writes one JSON object per line, for tools and agents that parse the output: {@code jq}, log collectors, scripts.
 * <pre>{@code
 * {"ts":"2026-11-11T02:30:31.412Z","level":"WARN","logger":"com.acme.PaymentService","thread":"main",
 *  "message":"Charge of 42 failed","template":"Charge of {} failed","args":["42"],"fields":{"amount":42},
 *  "context":{"requestId":"r-17"},
 *  "caller":{"class":"com.acme.PaymentService","method":"charge","file":"PaymentService.java","line":87},
 *  "exception":{"class":"java.io.IOException","message":"timeout","stackTrace":"java.io.IOException: timeout\n\tat ..."}}
 * }</pre>
 * (shown on several lines here; the real output is a single line). Fields without a value are left out:
 * {@code template} and {@code args} appear only for parameterized messages, {@code dbg} (always {@code true}) only for
 * entries of {@code LOG.dbg(...)}, {@code breadcrumb} (always {@code true}) only for entries held back by
 * {@code nanolog.buffer}, {@code fields} only with arguments of {@link LOG#kv(String, Object)},
 * {@code context} only within {@link LOG#context(String, Object)}, {@code caller} only when the source class was
 * detected, with a {@code source} line when {@link #setShowSourceLine(boolean)} is on, {@code exception} only with a
 * throwable. {@code args} leaves out the fields that no placeholder takes,
 * and {@code fields} has them all. The values of {@code fields} and {@code context} keep their type: integers, {@code BigDecimal} and finite floating-point numbers are JSON numbers, booleans are
 * booleans, {@code null} is {@code null}, and everything else is a string, written like a {@code {}} argument. Line breaks and control characters are escaped, so one
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
     * The classes whose values are written as JSON numbers as they are. Exact classes: a subclass of
     * {@code BigDecimal} may print anything.
     */
    private static final List<Class<?>> NUMBERS =
            Arrays.<Class<?>>asList(Byte.class, Short.class, Integer.class, Long.class, BigInteger.class, BigDecimal.class);

    /**
     * Creates a logger configured like {@link ConsoleLogger#ConsoleLogger()}.
     */
    public JsonLogger() {
        super();
    }

    /**
     * Never: every entry is one line, which a tool counts itself.
     */
    @Override
    boolean collapsesRepeats() {
        return false;
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
        if (entry.isDbg()) {
            out.append(",\"dbg\":true");
        }
        if (entry.isBreadcrumb()) {
            out.append(",\"breadcrumb\":true");
        }
        if (entry.getSourceClass() != null) {
            appendField(out, "logger", entry.getSourceClass().getName());
        }
        appendField(out, "thread", entry.getThreadName());
        if (entry.getTag() != null) {
            out.append(",\"tags\":[");
            appendString(out, entry.getTag());
            out.append(']');
        }
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
        appendObject(out, "fields", entry.getFields());
        appendObject(out, "context", entry.getContext());
        if (entry.getSkippedCount() > 0) {
            out.append(",\"skipped\":").append(entry.getSkippedCount());
        }
        if (entry.getCaller() != null) {
            appendCaller(out, entry.getCaller(), sourceLineOf(entry));
        }
        if (entry.getThrowable() != null) {
            appendException(out, entry.getThrowable());
        }
        return out.append('}').toString();
    }

    private static void appendObject(StringBuilder out, String name, Map<String, Object> pairs) {
        if (!pairs.isEmpty()) {
            out.append(",\"").append(name).append("\":{");
            String separator = "";
            for (Map.Entry<String, Object> pair : pairs.entrySet()) {
                out.append(separator);
                appendString(out, pair.getKey());
                out.append(':');
                appendValue(out, pair.getValue());
                separator = ",";
            }
            out.append('}');
        }
    }

    /**
     * Appends a value with its JSON type: numbers of the JDK's number classes and booleans as they are, {@code null}
     * as {@code null}, a hidden payload of {@code LOG.content(...)} as an object with its length and hash, everything
     * else as a string written like a {@code {}} argument. {@code NaN} and the
     * infinities, which JSON has no numbers for, become strings.
     */
    static void appendValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof LOG.Content && ((LOG.Content) value).limit() == Contents.HIDE) {
            String text = ((LOG.Content) value).text();
            out.append("{\"content_len\":").append(text.length()).append(",\"content_hash\":");
            appendString(out, Contents.hash(text));
            out.append('}');
        } else if (value instanceof Boolean || isNumber(value)) {
            out.append(value);
        } else {
            appendString(out, LogEntry.argToString(value));
        }
    }

    /**
     * Whether a value is written as a JSON number: an exact number class of the JDK, or a finite {@code Double} or
     * {@code Float}. Such values cannot change.
     */
    static boolean isNumber(Object value) {
        if (value instanceof Double || value instanceof Float) {
            return Double.isFinite(((Number) value).doubleValue());
        }
        return NUMBERS.contains(value.getClass());
    }

    private static void appendCaller(StringBuilder out, StackTraceElement caller, String source) {
        out.append(",\"caller\":{\"class\":");
        appendString(out, caller.getClassName());
        appendField(out, "method", caller.getMethodName());
        if (caller.getFileName() != null) {
            appendField(out, "file", caller.getFileName());
        }
        if (caller.getLineNumber() >= 0) {
            out.append(",\"line\":").append(caller.getLineNumber());
        }
        if (source != null) {
            appendField(out, "source", source);
        }
        out.append('}');
    }

    private void appendException(StringBuilder out, Throwable throwable) {
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
     * U+2029, which JavaScript treats as line breaks, and the bidi controls and tags that make a line read other than
     * it holds ({@link DefaultLoggerCreator#isMisleadingAt}); a JSON parser gives them back as they were (#113).
     * Everything else, characters outside the BMP included, is written as is.
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
                    if (c < 0x20 || DefaultLoggerCreator.isMisleadingAt(value, i) || c == '\u2028' || c == '\u2029') {
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
