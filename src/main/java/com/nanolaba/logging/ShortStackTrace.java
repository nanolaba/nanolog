package com.nanolaba.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Prints a throwable with the layout of {@code printStackTrace} ({@code Caused by:}, {@code Suppressed:} with its
 * indentation, {@code ... N more} for the frames shared with the enclosing trace, circular references marked) but
 * shorter: a run of two or more excluded frames becomes one {@code ... N frames excluded} line, the first frame of each
 * throwable is always kept, and at most {@code maxFrames} frames of each throwable are printed.
 * <p>
 * It lets every failure through: {@link ConsoleLogger} catches it and falls back to {@code printStackTrace}.
 */
final class ShortStackTrace {

    private static final StackTraceElement[] NO_FRAMES = new StackTraceElement[0];

    private final List<String> exclude;
    private final int maxFrames;
    private final StringWriter text = new StringWriter();
    private final PrintWriter out = new PrintWriter(text);
    private final Set<Throwable> printed = Collections.newSetFromMap(new IdentityHashMap<>());

    private ShortStackTrace(List<String> exclude, int maxFrames) {
        this.exclude = exclude;
        this.maxFrames = maxFrames;
    }

    /**
     * Prints a throwable.
     *
     * @param exclude   package or class prefixes whose frames are excluded, matched by whole name segments
     * @param maxFrames the most frames printed per throwable; {@code 0} for no limit
     */
    static String of(Throwable throwable, List<String> exclude, int maxFrames) {
        ShortStackTrace trace = new ShortStackTrace(exclude, maxFrames);
        trace.print(throwable, NO_FRAMES, "", "");
        // a PrintWriter on a StringWriter buffers nothing, so the text is complete without a flush
        return trace.text.toString();
    }

    private void print(Throwable throwable, StackTraceElement[] enclosing, String caption, String prefix) {
        if (!printed.add(throwable)) {
            out.println(prefix + caption + "[CIRCULAR REFERENCE: " + throwable + "]");
            return;
        }
        StackTraceElement[] trace = throwable.getStackTrace();
        int common = framesInCommon(trace, enclosing);
        out.println(prefix + caption + throwable);
        printFrames(trace, trace.length - common, common, prefix);
        for (Throwable suppressed : throwable.getSuppressed()) {
            print(suppressed, trace, "Suppressed: ", prefix + "\t");
        }
        Throwable cause = throwable.getCause();
        if (cause != null) {
            print(cause, trace, "Caused by: ", prefix);
        }
    }

    /**
     * The number of frames at the end of a trace that the enclosing trace ends with as well.
     */
    private static int framesInCommon(StackTraceElement[] trace, StackTraceElement[] enclosing) {
        int m = trace.length - 1;
        int n = enclosing.length - 1;
        while (m >= 0 && n >= 0 && trace[m].equals(enclosing[n])) {
            m--;
            n--;
        }
        return trace.length - 1 - m;
    }

    private void printFrames(StackTraceElement[] trace, int own, int common, String prefix) {
        int frames = 0;
        int i = 0;
        while (i < own && (maxFrames == 0 || frames < maxFrames)) {
            int run = i == 0 ? 0 : excludedRun(trace, i, own);
            if (run >= 2) {
                out.println(prefix + "\t... " + run + " frames excluded");
                i += run;
            } else {
                out.println(prefix + "\tat " + trace[i]);
                frames++;
                i++;
            }
        }
        int more = own - i + common;
        if (more > 0) {
            out.println(prefix + "\t... " + more + " more");
        }
    }

    /**
     * The number of consecutive excluded frames from a position on.
     */
    private int excludedRun(StackTraceElement[] trace, int from, int own) {
        int end = from;
        while (end < own && isExcluded(trace[end].getClassName())) {
            end++;
        }
        return end - from;
    }

    private boolean isExcluded(String className) {
        for (String prefix : exclude) {
            if (className.startsWith(prefix) &&
                (className.length() == prefix.length() || isSeparator(className.charAt(prefix.length())))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSeparator(char c) {
        return c == '.' || c == '$';
    }
}
