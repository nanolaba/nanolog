package com.nanolaba.logging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.nanolaba.logging.DefaultLoggerCreator.*;

/**
 * Agent mode, {@code nanolog.agent}: when a coding agent runs the program, Nanolog keeps its own console short and
 * writes the full log to the run log, because an agent reads output through a narrow window and rarely asks for more.
 * <p>
 * {@code auto}, the default, detects an agent from the variables agents set: {@code AI_AGENT} and {@code AGENT}, the
 * convention proposed for every agent, then the variables of particular agents. Environment variables are lost across
 * {@code sudo}, {@code docker run} and {@code ssh}, so detection is a hint: {@code on} and {@code off} decide instead.
 * Only Nanolog's own output changes: {@link ConsoleLogger#useAgentDefaults()} for the console, the run log switched on
 * by default, {@code LOG.dbg} printed whatever the levels, and one line that says what happened.
 */
final class Agent {

    /**
     * The variables that turn agent mode on, in the order they are named: the convention first.
     */
    private static final String[] VARIABLES = {"AI_AGENT", "AGENT", "CLAUDECODE", "GEMINI_CLI", "CODEX_SANDBOX",
            "CODEX_CI", "CURSOR_AGENT", "VSCODE_AGENT", "CLINE_ACTIVE"};

    /**
     * Values that leave a variable unset, ignoring case and surrounding spaces; so does an empty one.
     */
    private static final List<String> NO = Arrays.asList("0", "false", "no", "off");

    /**
     * The frames the console of agent mode collapses: the JDK, Kotlin, test runners, build tools, frameworks and
     * servers. The first frame of each throwable stays, and the run log keeps the whole trace.
     */
    static final List<String> EXCLUDE = Collections.unmodifiableList(Arrays.asList(
            "java", "javax", "jdk", "sun", "com.sun", "kotlin", "kotlinx",
            "org.junit", "junit", "org.testng", "org.apache.maven.surefire", "org.gradle", "worker.org.gradle",
            "org.springframework", "org.apache.catalina", "org.apache.coyote", "org.apache.tomcat",
            "org.eclipse.jetty", "io.netty", "org.hibernate"));

    /**
     * The most frames of each throwable the console of agent mode prints.
     */
    static final int MAX_FRAMES = 20;

    /**
     * The most characters of the value of a variable that the line of agent mode shows: enough for the name of an
     * agent, {@code claude-code}, or a flag, {@code 1}.
     */
    static final int MAX_VALUE = 64;

    private static final String OFF_HINT = PROPERTY_AGENT + "=off turns it off";

    // guarded by the class
    private static boolean announced;

    private Agent() {
    }

    /**
     * Reads {@code nanolog.agent} and, for {@code auto}, the environment.
     *
     * @return what turned agent mode on, {@code AI_AGENT=claude-code} or {@code nanolog.agent=on}, with the value
     * {@link #shown}; {@code null} if it is off
     */
    static String detect() {
        String mode = readChoice(PROPERTY_AGENT, "auto", "on", "off");
        if ("on".equals(mode)) {
            return PROPERTY_AGENT + "=on";
        } else if ("off".equals(mode)) {
            return null;
        }
        for (String name : VARIABLES) {
            String value = environment.apply(name);
            if (value != null && !value.trim().isEmpty() && !NO.contains(value.trim().toLowerCase(Locale.ROOT))) {
                return name + "=" + shown(value);
            }
        }
        return null;
    }

    /**
     * The value of a variable as the line of agent mode shows it: whole up to {@link #MAX_VALUE} characters, otherwise
     * its start and {@code ... [+4936 chars]}, and in double quotes when it would not read as one value, as a pair of
     * the context. Anything in the process tree may set the variable, and the agent reads the line back; its control
     * and format characters are escaped when it is printed.
     */
    private static String shown(String value) {
        if (value.length() <= MAX_VALUE) {
            return ConsoleLogger.quoted(value);
        }
        // a character outside the BMP stays whole
        int end = Character.isHighSurrogate(value.charAt(MAX_VALUE - 1)) ? MAX_VALUE - 1 : MAX_VALUE;
        return ConsoleLogger.quoted(value.substring(0, end) + "... [+" + Contents.chars(value.length() - end) + "]");
    }

    /**
     * Gives Nanolog's console the defaults of agent mode; any other logger is left alone.
     *
     * @return what the console shows, for the line of {@link #announce}; {@code null} for another logger
     */
    static String shorten(ILogger logger) {
        if (!isConsole(logger)) {
            return null;
        }
        return ((ConsoleLogger) logger).useAgentDefaults()
               ? "the console keeps " + PROPERTY_LEVEL + " and shows every LOG.dbg"
               : "the console shows WARN, ERROR and every LOG.dbg";
    }

    /**
     * Whether a logger is the console of Nanolog, which {@link #shorten} shortens: a {@link ConsoleLogger} or a
     * subclass, except a {@link FileLogger}, since a file keeps every entry.
     */
    static boolean isConsole(ILogger logger) {
        return logger instanceof ConsoleLogger && !(logger instanceof FileLogger);
    }

    /**
     * Prints, once per JVM, that agent mode is on, what turned it on, what the console shows and where the full log
     * is, so the agent knows what it does not see and where to read it.
     *
     * @param trigger what turned agent mode on
     * @param console what the console shows; {@code null} for a logger of its own
     * @param runLog  the run log; {@code null} if it is off
     */
    static synchronized void announce(String trigger, String console, RunLog runLog) {
        if (announced) {
            return;
        }
        announced = true;
        List<String> parts = new ArrayList<>();
        if (console != null) {
            parts.add(console);
        }
        if (runLog != null) {
            parts.add("the full log is in " + runLog.shownFile().toAbsolutePath() + ", WARN and ERROR also in " +
                      runLog.shownErrorsFile().toAbsolutePath());
        }
        parts.add(OFF_HINT);
        DefaultLoggerCreator.printNotice("Agent mode (" + trigger + "): " + String.join("; ", parts));
    }

    /**
     * Lets the next {@link #announce} print again; for tests.
     */
    static synchronized void reset() {
        announced = false;
    }
}
