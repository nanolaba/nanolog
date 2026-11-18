package com.nanolaba.logging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import static com.nanolaba.logging.DefaultLoggerCreator.*;

/**
 * The settings Nanolog reads, and the check that reports those it does not know. A mistyped key is otherwise ignored
 * without a word, and the output it should have changed looks plausible.
 * <p>
 * {@code nanolog.console.*}, {@code nanolog.exception.*}, {@code nanolog.level.*}, {@code nanolog.run.*},
 * {@code nanolog.unit.*}, {@code nanolog.buffer.*}, {@code nanolog.content.*} and the keys directly under
 * {@code nanolog.} belong to Nanolog, so an unknown key there is reported. A custom logger keeps settings of its own
 * under a namespace of its own, such as {@code nanolog.mylogger.url}; such a key is reported only when it is one or two
 * edits away from a known one.
 */
final class Settings {

    /**
     * Every key Nanolog reads; the package rules {@code nanolog.level.<name>} come on top. {@code SettingsTest} keeps
     * the list in step with the {@code PROPERTY_} constants and the tables of the README.
     */
    static final List<String> KNOWN = Collections.unmodifiableList(Arrays.asList(
            PROPERTY_LOGGER_CLASS, PROPERTY_LOGGER_CONFIG_FILE, PROPERTY_LEVEL, PROPERTY_LEVELS, PROPERTY_STRICT,
            PROPERTY_SOURCES, PROPERTY_DUMP, PROPERTY_DBG, PROPERTY_AGENT, PROPERTY_SUMMARY, PROPERTY_JUNIT_OUTPUT, PROPERTY_DIAGNOSTICS,
            PROPERTY_RUN, PROPERTY_RUN_DIR, PROPERTY_RUN_FORMAT, PROPERTY_RUN_KEEP, PROPERTY_RUN_MAX_SIZE,
            PROPERTY_UNIT_SAMPLE, PROPERTY_UNIT_SLOW_MS, PROPERTY_BUFFER, PROPERTY_BUFFER_SIZE, PROPERTY_CONTENT, PROPERTY_REDACT,
            PROPERTY_CALLER_SKIP, PROPERTY_CONTEXT_PROVIDER, PROPERTY_TAGS,
            ConsoleLogger.PROPERTY_SHOW_LEVEL, ConsoleLogger.PROPERTY_SHOW_DATE, ConsoleLogger.PROPERTY_SHOW_SOURCE,
            ConsoleLogger.PROPERTY_SHOW_SOURCE_FULL_NAME, ConsoleLogger.PROPERTY_SHOW_LOCATION,
            ConsoleLogger.PROPERTY_SHOW_THREAD, ConsoleLogger.PROPERTY_SHOW_CONTEXT, ConsoleLogger.PROPERTY_SHOW_SOURCE_LINE,
            ConsoleLogger.PROPERTY_STREAM,
            ConsoleLogger.PROPERTY_DATE_FORMAT, ConsoleLogger.PROPERTY_TIME_ZONE, ConsoleLogger.PROPERTY_SOURCE_LENGTH,
            ConsoleLogger.PROPERTY_MULTILINE, ConsoleLogger.PROPERTY_REPEATS,
            ConsoleLogger.PROPERTY_COLOR,
            ConsoleLogger.PROPERTY_EXCEPTION_EXCLUDE, ConsoleLogger.PROPERTY_EXCEPTION_MAX_FRAMES));

    /**
     * The namespaces whose every key belongs to Nanolog.
     */
    private static final List<String> NAMESPACES = Arrays.asList("buffer", "caller", "console", "content", "context", "exception", "junit", "level", "run", "unit");

    private static final String LEVEL = "level";
    private static final String LEVEL_RULE = PROPERTY_LEVEL + ".";
    private static final String CONTENT_RULE = PROPERTY_CONTENT + ".";
    private static final String VARIABLE_PREFIX = toEnvironmentName(PROPERTY_PREFIX);
    private static final String LEVEL_VARIABLE = toEnvironmentName(LEVEL_RULE);

    /**
     * The known keys without {@code nanolog.}, and the names of their environment variables without {@code NANOLOG_}.
     */
    private static final List<String> KEYS = new ArrayList<>();
    private static final List<String> VARIABLES = new ArrayList<>();

    static {
        for (String key : KNOWN) {
            KEYS.add(key.substring(PROPERTY_PREFIX.length()));
            VARIABLES.add(toEnvironmentName(key).substring(VARIABLE_PREFIX.length()));
        }
    }

    private Settings() {/**/}

    /**
     * The settings Nanolog does not know, one message each: system properties first, then environment variables, then
     * the keys of {@code nanolog.properties}, each group in alphabetical order.
     *
     * @param system      the system properties
     * @param environment the environment variables
     * @param file        the keys and values of {@code nanolog.properties}; its {@code nanolog.} keys are also system
     *                    properties, and are reported with the file
     * @param location    where the file was found
     */
    static List<String> findings(Map<String, String> system, Map<String, String> environment, Map<String, String> file,
                                 String location) {
        List<String> findings = new ArrayList<>();
        for (String key : new TreeSet<>(system.keySet())) {
            if (!Objects.equals(system.get(key), file.get(key))) {
                property(key, "system property", findings);
            }
        }
        for (String name : new TreeSet<>(environment.keySet())) {
            variable(name, environment.get(name), findings);
        }
        for (String key : new TreeSet<>(file.keySet())) {
            if (key.startsWith(PROPERTY_PREFIX)) {
                property(key, location, findings);
            } else {
                String prefixed = PROPERTY_PREFIX + key;
                String suggestion = isKnown(prefixed) ? prefixed : suggestion(prefixed);
                findings.add("\"" + key + "\" in " + location + " is ignored: keys must start with \"" + PROPERTY_PREFIX + "\"" +
                             (suggestion == null ? "" : " (did you mean \"" + suggestion + "\"?)"));
            }
        }
        return findings;
    }

    private static void property(String key, String source, List<String> findings) {
        if (key.startsWith(PROPERTY_PREFIX) && !isKnown(key)) {
            String suggestion = suggestion(key);
            if (suggestion != null || isNanologNamespace(key.substring(PROPERTY_PREFIX.length()))) {
                findings.add("Unknown setting \"" + key + "\" (" + source + ")" +
                             (suggestion == null ? "" : "; did you mean \"" + suggestion + "\"?"));
            }
        }
    }

    private static void variable(String name, String value, List<String> findings) {
        if (name.startsWith(LEVEL_VARIABLE)) {
            // the dots of a package cannot be told from underscores in its name, so package rules have a list of their own
            String target = name.substring(LEVEL_VARIABLE.length()).toLowerCase(Locale.ROOT).replace('_', '.');
            findings.add("Unknown environment variable " + name + "; per-package levels go into " +
                         toEnvironmentName(PROPERTY_LEVELS) + "=" + target + "=" + value);
        } else if (name.startsWith(VARIABLE_PREFIX)) {
            String rest = name.substring(VARIABLE_PREFIX.length());
            if (!VARIABLES.contains(rest)) {
                String suggestion = closest(rest, VARIABLES);
                if (suggestion != null || isNanologNamespace(rest.toLowerCase(Locale.ROOT).replace('_', '.'))) {
                    findings.add("Unknown environment variable " + name +
                                 (suggestion == null ? "" : "; did you mean " + VARIABLE_PREFIX + suggestion + "?"));
                }
            }
        }
    }

    private static boolean isKnown(String key) {
        return KNOWN.contains(key) || key.startsWith(LEVEL_RULE) || key.startsWith(CONTENT_RULE);
    }

    /**
     * Whether a key, without {@code nanolog.}, has no namespace or one of Nanolog's own.
     */
    private static boolean isNanologNamespace(String rest) {
        int dot = rest.indexOf('.');
        return dot == -1 || NAMESPACES.contains(rest.substring(0, dot));
    }

    /**
     * The known key closest to an unknown one; a mistyped namespace of package rules, {@code nanolog.levels.com.acme},
     * gives {@code nanolog.level.com.acme}. {@code null} if nothing is close.
     */
    private static String suggestion(String key) {
        String rest = key.substring(PROPERTY_PREFIX.length());
        String closest = closest(rest, KEYS);
        if (closest != null) {
            return PROPERTY_PREFIX + closest;
        }
        int dot = rest.indexOf('.');
        if (dot != -1 && closest(rest.substring(0, dot), Collections.singletonList(LEVEL)) != null) {
            return LEVEL_RULE + rest.substring(dot + 1);
        }
        return null;
    }

    /**
     * The candidate fewest edits away, the first of equals: at most one edit for a name shorter than six characters,
     * two for a longer one, so {@code log} does not turn into {@code dbg}. {@code null} if none is that close.
     */
    private static String closest(String name, List<String> candidates) {
        int allowed = name.length() < 6 ? 1 : 2;
        String closest = null;
        for (String candidate : candidates) {
            int distance = distance(name, candidate);
            if (distance <= allowed) {
                closest = candidate;
                allowed = distance - 1;
            }
        }
        return closest;
    }

    /**
     * The number of insertions, deletions, substitutions and transpositions of adjacent characters that turn one
     * string into the other (the optimal string alignment distance): {@code levle} is one edit from {@code level}.
     */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
