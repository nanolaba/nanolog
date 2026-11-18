package com.nanolaba.logging;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Finds the text of the arguments of a {@code dbg(...)} call in the source file of its call site, the way Python's
 * icecream does: the stack frame gives the file and the line, the line gives the call. The reported line is the one
 * holding {@code dbg(}, also when the arguments continue on the following lines, so the scan starts there and follows
 * balanced parentheses, skipping string, char and text block literals and comments.
 * <p>
 * Every failure gives {@code null}, and the caller prints the values without their text: no source directory (a
 * production deployment, a fat jar), a source file newer than its class file, a line that no longer holds exactly one
 * {@code dbg(} call (two calls on one line cannot be told apart, since Java reports no column), a frame without a line
 * number ({@code javac -g:none}), an I/O error. Each call site is looked up once.
 */
final class DbgSource {

    /**
     * How many lines after the line of the call its arguments may span.
     */
    static final int MAX_LINES = 50;

    static final String CALL = "dbg";

    /**
     * The most characters of a source line written by {@code nanolog.console.showSourceLine}.
     */
    static final int MAX_LINE = 200;

    /**
     * The most source lines kept in their cache: the frames of real stacks are bounded by the code, but an exception
     * may carry made-up frames. Past it, lines are read each time. Tests lower it.
     */
    static int maxCachedLines = 10_000;

    /**
     * The text of each call site, empty when it could not be found. Call sites are lines of code, so the map stays
     * as small as the code.
     */
    private static final Map<StackTraceElement, Optional<String>> CACHE = new ConcurrentHashMap<>();

    /**
     * The source line of each frame, for {@code nanolog.console.showSourceLine}; empty when it could not be found.
     */
    private static final Map<StackTraceElement, Optional<String>> LINES = new ConcurrentHashMap<>();

    /**
     * Build layouts: a class output directory, relative to its module, followed by the source directories of the
     * module that compile into it.
     */
    private static final String[][] LAYOUTS = {
            {"target/classes", "src/main/java", "src/main/kotlin"},
            {"target/test-classes", "src/test/java", "src/test/kotlin"},
            {"build/classes/java/main", "src/main/java"},
            {"build/classes/java/test", "src/test/java"},
            {"build/classes/kotlin/main", "src/main/kotlin"},
            {"build/classes/kotlin/test", "src/test/kotlin"},
    };

    /**
     * The source directories tried in the working directory when the class output is not in a known layout.
     */
    private static final String[] FALLBACK = {"src/main/java", "src/test/java", "src/main/kotlin", "src/test/kotlin", "src"};

    private DbgSource() {/**/}

    /**
     * The text of the arguments of the call at a frame, as written in the source: comments removed, whitespace outside
     * literals collapsed to single spaces. Never throws.
     *
     * @param frame       the frame of the call
     * @param sourceClass the class of the frame, whose location leads to the sources
     * @return the text, or {@code null} if it cannot be found
     */
    static String argumentsText(StackTraceElement frame, Class sourceClass) {
        return CACHE.computeIfAbsent(frame, f -> Optional.ofNullable(find(f, sourceClass, DbgSource::argumentsOnLine))).orElse(null);
    }

    /**
     * The source line of a frame, trimmed and cut at {@link #MAX_LINE} characters, for
     * {@code nanolog.console.showSourceLine}: from the same sources, with the same fallbacks, as the text of a
     * {@code dbg} call. Never throws.
     *
     * @param frame       the frame
     * @param sourceClass the class of the frame, whose location leads to the sources
     * @return the line, or {@code null} if it cannot be found or is blank
     */
    static String lineText(StackTraceElement frame, Class sourceClass) {
        Optional<String> cached = LINES.get(frame);
        if (cached != null) {
            return cached.orElse(null);
        }
        String line = find(frame, sourceClass, DbgSource::lineOf);
        if (LINES.size() < maxCachedLines) {
            LINES.put(frame, Optional.ofNullable(line));
        }
        return line;
    }

    /**
     * The number of source lines in their cache; for tests.
     */
    static int cachedLines() {
        return LINES.size();
    }

    /**
     * The trimmed line, cut at {@link #MAX_LINE} characters.
     *
     * @return {@code null} if there is no such line or it is blank
     */
    static String lineOf(List<String> lines, int line) {
        if (line < 1 || line > lines.size()) {
            return null;
        }
        String text = lines.get(line - 1).trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > MAX_LINE ? text.substring(0, MAX_LINE) + "..." : text;
    }

    /**
     * Forgets the texts and lines found so far; for tests.
     */
    static void clearCache() {
        CACHE.clear();
        LINES.clear();
    }

    private static String find(StackTraceElement frame, Class sourceClass, BiFunction<List<String>, Integer, String> extract) {
        try {
            return read(frame, sourceRoots(sourceClass), location(sourceClass), extract);
        } catch (RuntimeException e) {
            // an invalid path in nanolog.sources or user.dir
            return null;
        }
    }

    /**
     * Reads the text of a call from the first source root holding its file.
     *
     * @param classes the directory of the compiled class, to tell a source edited after compilation; {@code null}
     *                if unknown
     */
    static String read(StackTraceElement frame, List<Path> roots, Path classes) {
        return read(frame, roots, classes, DbgSource::argumentsOnLine);
    }

    /**
     * Reads what {@code extract} takes from the lines of the source file of a frame, from the first source root
     * holding it. The frame may come from an exception made up elsewhere, so its file must be a plain name and the
     * file must lie inside the root: a frame cannot lead to a file outside the sources.
     */
    private static String read(StackTraceElement frame, List<Path> roots, Path classes, BiFunction<List<String>, Integer, String> extract) {
        try {
            String file = fileName(frame);
            if (file.indexOf('/') != -1 || file.indexOf('\\') != -1) {
                return null;
            }
            String className = frame.getClassName();
            String relative = className.substring(0, className.lastIndexOf('.') + 1).replace('.', '/') + file;
            for (Path root : roots) {
                Path base = root.normalize();
                Path source = base.resolve(relative).normalize();
                if (source.startsWith(base) && Files.isRegularFile(source)) {
                    return isStale(source, classes, className) ? null : extract.apply(readLines(source), frame.getLineNumber());
                }
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * The file of a frame; without one (code compiled with {@code -g:lines}), the file named after the top-level
     * class.
     */
    private static String fileName(StackTraceElement frame) {
        if (frame.getFileName() != null) {
            return frame.getFileName();
        }
        String className = frame.getClassName();
        return className.substring(className.lastIndexOf('.') + 1).split("\\$")[0] + ".java";
    }

    /**
     * A source file changed after its class was compiled no longer matches the running code.
     */
    private static boolean isStale(Path source, Path classes, String className) throws IOException {
        if (classes == null) {
            return false;
        }
        Path compiled = classes.resolve(className.replace('.', '/') + ".class");
        return Files.isRegularFile(compiled) && Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(compiled)) > 0;
    }

    /**
     * The text of a source file: UTF-8, or Latin-1 if it is not valid UTF-8.
     */
    static String readText(Path source) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            // javac before JDK 18 reads the platform encoding; Latin-1 at least keeps the code, which is ASCII, intact
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static List<String> readLines(Path source) throws IOException {
        List<String> lines = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new StringReader(readText(source)));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            lines.add(line);
        }
        return lines;
    }

    // WHERE THE SOURCES ARE

    /**
     * The directories to look for sources in: those of {@code nanolog.sources}, or the ones the location of the class
     * leads to.
     */
    static List<Path> sourceRoots(Class sourceClass) {
        String configured = DefaultLoggerCreator.getProperty(DefaultLoggerCreator.PROPERTY_SOURCES);
        if (configured == null) {
            return derivedRoots(location(sourceClass), Paths.get(System.getProperty("user.dir")));
        }
        List<Path> roots = new ArrayList<>();
        for (String root : configured.split(",")) {
            if (!root.trim().isEmpty()) {
                roots.add(Paths.get(root.trim()));
            }
        }
        return roots;
    }

    /**
     * The source directories of a module whose classes are compiled into {@code location} by Maven or Gradle; the
     * usual source directories of the working directory if the layout is unknown.
     */
    static List<Path> derivedRoots(Path location, Path workingDirectory) {
        List<Path> roots = new ArrayList<>();
        if (location != null) {
            for (String[] layout : LAYOUTS) {
                Path output = Paths.get(layout[0]);
                if (location.endsWith(output)) {
                    Path module = location;
                    for (int i = 0; i < output.getNameCount(); i++) {
                        module = module.resolve("..");
                    }
                    for (int i = 1; i < layout.length; i++) {
                        roots.add(module.normalize().resolve(layout[i]));
                    }
                    return roots;
                }
            }
        }
        for (String root : FALLBACK) {
            roots.add(workingDirectory.resolve(root));
        }
        return roots;
    }

    /**
     * The directory or jar a class was loaded from; {@code null} for JDK classes and for locations that are not files.
     */
    static Path location(Class type) {
        try {
            CodeSource source = type.getProtectionDomain().getCodeSource();
            URL url = source == null ? null : source.getLocation();
            return url == null ? null : Paths.get(url.toURI());
        } catch (Exception e) {
            // not a file system location (a nested jar, a remote URL), or a security manager forbids the lookup
            return null;
        }
    }

    // SCANNING THE SOURCE

    /**
     * The text of the arguments of the only {@code dbg(} call on a line.
     *
     * @param lines the lines of the source file
     * @param line  the line of the call, from 1
     * @return the text, or {@code null} if the line holds no call or more than one, or the call does not close within
     * {@link #MAX_LINES} lines
     */
    static String argumentsOnLine(List<String> lines, int line) {
        if (line < 1 || line > lines.size()) {
            return null;
        }
        int open = onlyCall(lines.get(line - 1));
        if (open == -1) {
            return null;
        }
        StringBuilder text = new StringBuilder(lines.get(line - 1).substring(open));
        for (int i = line; i < Math.min(lines.size(), line + MAX_LINES); i++) {
            text.append('\n').append(lines.get(i));
        }
        return arguments(text.toString());
    }

    /**
     * The position after the parenthesis of the only {@code dbg(} call on a line; {@code -1} if there is none or more
     * than one.
     */
    private static int onlyCall(String line) {
        int found = -1;
        int i = 0;
        while (i < line.length()) {
            int end = tokenEnd(line, i);
            if (CALL.equals(line.substring(i, end))) {
                int open = skipBlank(line, end);
                if (open < line.length() && line.charAt(open) == '(') {
                    if (found != -1) {
                        return -1;
                    }
                    found = open + 1;
                }
            }
            i = end;
        }
        return found;
    }

    private static int skipBlank(String text, int from) {
        int i = from;
        while (i < text.length() && isBlank(text, i)) {
            i = tokenEnd(text, i);
        }
        return i;
    }

    /**
     * The text up to the parenthesis that closes the call, which is open at the start; {@code null} if it does not
     * close.
     */
    private static String arguments(String text) {
        StringBuilder out = new StringBuilder();
        boolean blank = false;
        int depth = 1;
        int i = 0;
        while (i < text.length()) {
            int end = tokenEnd(text, i);
            if (isBlank(text, i)) {
                blank = true;
            } else {
                char c = text.charAt(i);
                if (c == ')' && --depth == 0) {
                    return out.toString();
                }
                if (c == '(') {
                    depth++;
                }
                // a chain split over lines reads as one: no space around its dots
                if (blank && out.length() > 0 && c != '.' && out.charAt(out.length() - 1) != '.') {
                    out.append(' ');
                }
                blank = false;
                out.append(text, i, end);
            }
            i = end;
        }
        return null;
    }

    /**
     * Splits the text of the arguments at the commas outside parentheses, brackets, braces and literals.
     *
     * @param text the text of at least one argument
     */
    static List<String> splitArguments(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
            i = tokenEnd(text, i);
        }
        parts.add(text.substring(start).trim());
        return parts;
    }

    /**
     * Whitespace or the start of a comment: both separate tokens and are dropped from the text.
     */
    static boolean isBlank(String text, int i) {
        return Character.isWhitespace(text.charAt(i)) || text.startsWith("//", i) || text.startsWith("/*", i);
    }

    /**
     * The end of the token at {@code i}: a string, char or text block literal, a comment, an identifier, or a single
     * character.
     */
    static int tokenEnd(String text, int i) {
        char c = text.charAt(i);
        if (c == '"' || c == '\'') {
            return literalEnd(text, i);
        } else if (text.startsWith("//", i)) {
            int lineEnd = text.indexOf('\n', i);
            return lineEnd == -1 ? text.length() : lineEnd;
        } else if (text.startsWith("/*", i)) {
            int close = text.indexOf("*/", i + 2);
            return close == -1 ? text.length() : close + 2;
        } else if (Character.isJavaIdentifierStart(c)) {
            int end = i + 1;
            while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
                end++;
            }
            return end;
        }
        return i + 1;
    }

    /**
     * The end of the literal at {@code start}. A text block may span lines; a string or char literal ends at the end
     * of its line even if it is not closed, as javac would report it.
     */
    private static int literalEnd(String text, int start) {
        if (text.startsWith("\"\"\"", start)) {
            int i = start + 3;
            while (i < text.length()) {
                if (text.charAt(i) == '\\') {
                    i += 2;
                } else if (text.startsWith("\"\"\"", i)) {
                    return i + 3;
                } else {
                    i++;
                }
            }
            return text.length();
        }
        char quote = text.charAt(start);
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else if (c == '\n') {
                return i;
            } else {
                i++;
            }
        }
        return text.length();
    }
}
