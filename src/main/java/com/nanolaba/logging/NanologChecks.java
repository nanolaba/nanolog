package com.nanolaba.logging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checks of an application's sources, meant to run as tests of the application.
 */
public final class NanologChecks {

    private static final String LOG = "LOG";

    private NanologChecks() {/**/}

    /**
     * Fails while the sources still call {@code LOG.dbg}, which is meant for temporary debug output:
     * <pre>
     * &#64;Test
     * void noDebugPrintsLeft() {
     *     NanologChecks.assertNoDbgCalls("src/main/java");
     * }
     * </pre>
     * Reads every {@code .java} and {@code .kt} file under the directories, as text, without compiling it, and finds
     * {@code LOG.dbg(...)}, the rate-limited {@code LOG.once().dbg(...)} and {@code LOG.atMostEvery(...).dbg(...)}, the
     * method reference {@code LOG::dbg} and a static import of {@code LOG.dbg}, also split over lines. Comments,
     * strings, chars and text blocks are skipped, so a Javadoc that shows {@code LOG.dbg} does not count. A call
     * through a static import shows up as its import line.
     *
     * @param directory a source directory, relative to the working directory, which is the module directory in Maven
     *                  and Gradle test runs
     * @param more      further source directories, for example {@code "src/test/java"}
     * @throws AssertionError           listing the file, line and code of every call
     * @throws IllegalArgumentException if a directory does not exist
     * @throws UncheckedIOException     if a file cannot be read
     */
    public static void assertNoDbgCalls(String directory, String... more) {
        List<String> found = new ArrayList<>();
        scan(Paths.get(directory), found);
        for (String next : more) {
            scan(Paths.get(next), found);
        }
        if (!found.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append(found.size()).append(found.size() == 1 ? " LOG.dbg call left in the code; remove it:" :
                                                " LOG.dbg calls left in the code; remove them:");
            for (String call : found) {
                message.append(System.lineSeparator()).append(call);
            }
            throw new AssertionError(message.toString());
        }
    }

    /**
     * Adds the calls in the sources under a directory, in path order, as {@code path:line: code}.
     */
    private static void scan(Path directory, List<String> found) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("No source directory " + directory.toAbsolutePath());
        }
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(NanologChecks::isSource).sorted().collect(Collectors.toList())) {
                String text = DbgSource.readText(file);
                String[] lines = text.split("\n", -1);
                for (int line : dbgCalls(text)) {
                    found.add(file + ":" + line + ": " + lines[line - 1].trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isSource(Path file) {
        String name = file.getFileName().toString();
        return (name.endsWith(".java") || name.endsWith(".kt")) && Files.isRegularFile(file);
    }

    /**
     * The lines, from 1, where the {@code LOG} of a {@code dbg} call stands; a line with two calls appears twice.
     *
     * @param text the source text
     */
    static List<Integer> dbgCalls(String text) {
        List<String> tokens = new ArrayList<>();
        List<Integer> tokenLines = new ArrayList<>();
        int line = 1;
        int i = 0;
        while (i < text.length()) {
            int end = DbgSource.tokenEnd(text, i);
            if (!DbgSource.isBlank(text, i)) {
                tokens.add(text.substring(i, end));
                tokenLines.add(line);
            }
            // comments and text blocks span lines
            for (int c = i; c < end; c++) {
                if (text.charAt(c) == '\n') {
                    line++;
                }
            }
            i = end;
        }
        List<Integer> calls = new ArrayList<>();
        for (int t = 0; t < tokens.size(); t++) {
            if (LOG.equals(tokens.get(t)) && callsDbg(tokens, t + 1)) {
                calls.add(tokenLines.get(t));
            }
        }
        return calls;
    }

    /**
     * Whether the tokens after {@code LOG} make a {@code dbg} call: {@code .dbg}, {@code ::dbg} or
     * {@code .method(...).dbg}.
     */
    private static boolean callsDbg(List<String> tokens, int t) {
        if (is(tokens, t, ":") && is(tokens, t + 1, ":")) {
            return is(tokens, t + 2, DbgSource.CALL);
        } else if (!is(tokens, t, ".")) {
            return false;
        } else if (is(tokens, t + 1, DbgSource.CALL)) {
            return true;
        }
        int close = closing(tokens, t + 2);
        return close != -1 && is(tokens, close + 1, ".") && is(tokens, close + 2, DbgSource.CALL);
    }

    /**
     * The index of the parenthesis that closes the one at {@code open}; {@code -1} if there is none.
     */
    private static int closing(List<String> tokens, int open) {
        if (!is(tokens, open, "(")) {
            return -1;
        }
        int depth = 0;
        for (int t = open; t < tokens.size(); t++) {
            String token = tokens.get(t);
            if ("(".equals(token)) {
                depth++;
            } else if (")".equals(token) && --depth == 0) {
                return t;
            }
        }
        return -1;
    }

    private static boolean is(List<String> tokens, int t, String text) {
        return t < tokens.size() && tokens.get(t).equals(text);
    }
}
