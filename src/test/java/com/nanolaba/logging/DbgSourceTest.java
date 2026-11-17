package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading the text of the arguments of a {@code dbg(...)} call from the source file.
 */
class DbgSourceTest {

    @TempDir
    Path temp;

    @BeforeEach
    @AfterEach
    public void forget() {
        DbgSource.clearCache();
        System.clearProperty(DefaultLoggerCreator.PROPERTY_SOURCES);
    }

    private static String args(int line, String... source) {
        return DbgSource.argumentsOnLine(Arrays.asList(source), line);
    }

    // the call on a line

    @Test
    public void testSimpleCall() {
        assertEquals("order.total()", args(1, "BigDecimal total = LOG.dbg(order.total());"));
        assertEquals("a, b", args(1, "dbg(a, b);"));
        assertEquals("x", args(1, "LOG.dbg (x);"), "spaces before the parenthesis");
        assertEquals("", args(1, "LOG.dbg();"));
    }

    @Test
    public void testOnlyTheReportedLineIsSearched() {
        assertEquals("y", args(2, "LOG.dbg(x);", "LOG.dbg(y);", "LOG.dbg(z);"));
        assertEquals("x", args(1, "LOG.dbg(x);"));
        assertNull(args(0, "LOG.dbg(x);"), "no line number");
        assertNull(args(2, "LOG.dbg(x);"), "beyond the end of the file");
    }

    @Test
    public void testMultiLineArguments() {
        assertEquals("first, second.third()", args(1,
                                                    "LOG.dbg(first,",
                                                    "        second",
                                                    "            .third());"));
        assertEquals("order.items().size()", args(1, "LOG.dbg(order.", "    items()", "    .size());"), "no space around the dots of a chain");
        assertEquals("c", args(2, "LOG.dbg(a,", "  b); LOG.dbg(c);"), "a call after the end of a multi-line one");
    }

    @Test
    public void testNestedParentheses() {
        assertEquals("f(g(a), (b + c))", args(1, "LOG.dbg(f(g(a), (b + c)));"));
    }

    @Test
    public void testLiteralsAndCommentsDoNotConfuseTheScanner() {
        assertEquals("\"a) dbg(\" + ')'", args(1, "LOG.dbg(\"a) dbg(\" + ')');"));
        assertEquals("\"say \\\"hi)\\\"\"", args(1, "LOG.dbg(\"say \\\"hi)\\\"\");"));
        assertEquals("'\\''", args(1, "LOG.dbg('\\'');"));
        assertEquals("a, b", args(1, "LOG.dbg(a, /* ) dbg( */ b); // dbg(c)"));
        assertEquals("a, b", args(1, "LOG.dbg(a, /* )", " dbg( */ b);"), "a comment over two lines");
        assertEquals("a, b", args(1, "LOG.dbg(a, // first )", "        b);"));
        assertEquals("a / b", args(1, "LOG.dbg(a / b);"), "a division is not a comment");
        assertEquals("x", args(1, "c = /* a *//* b */ LOG.dbg(x);"), "two comments in a row");
        assertEquals("x", args(1, "c = /*/ dbg( */ LOG.dbg(x);"), "/*/ opens a comment and does not close it");
        assertEquals("\"\"\"\n)  \\\"\"\" \"\"\"", args(1, "LOG.dbg(\"\"\"", ")  \\\"\"\" \"\"\");"),
                     "a text block keeps its content, escapes included");
    }

    @Test
    public void testLiteralsAndCommentsOnTheLineOfTheCall() {
        assertEquals("x", args(1, "String s = \"dbg(\"; char c = '('; /* dbg( */ LOG.dbg(x); // dbg("));
        assertEquals("x", args(1, "String s = \"say \\\"dbg(\\\"\"; LOG.dbg(x);"), "an escaped quote does not end a string");
        assertEquals("x", args(1, "char q = '\\''; LOG.dbg(x);"), "an escaped quote does not end a char");
        assertNull(args(1, "String s = \"\"\""), "a text block starts: no call on this line");
        assertNull(args(1, "s = \"open dbg("), "an unterminated string hides the rest of the line");
        assertNull(args(1, "c = /* LOG.dbg(x)"), "so does an unterminated comment");
    }

    @Test
    public void testUnterminatedLiteralEndsAtTheLineEnd() {
        assertEquals("\"a b", args(1, "LOG.dbg(\"a", "b);"), "invalid Java, but the scan goes on");
        assertEquals("'a b", args(1, "LOG.dbg('a", "b);"));
    }

    @Test
    public void testWhitespaceOutsideLiteralsIsCollapsed() {
        assertEquals("a + \"two  spaces\"", args(1, "LOG.dbg(  a   +", "  \t   \"two  spaces\"  );"));
        assertEquals("'x' + y", args(1, "LOG.dbg('x'   + y);"));
    }

    @Test
    public void testNoSingleCallOnTheLine() {
        assertNull(args(1, "int x = compute(a);"), "no dbg( on the line, for example after an edit");
        assertNull(args(1, "LOG.dbg(a) + LOG.dbg(b);"), "two calls on one line cannot be told apart");
        assertNull(args(2, "LOG.dbg(a,", "  b); LOG.dbg(c); LOG.dbg(d);"), "also after the end of an earlier call");
        assertNull(args(1, "mydbg(a); dbgx(b); LOG.dbg"), "dbg must be a whole name followed by a parenthesis");
        assertEquals("b", args(1, "mydbg(a); LOG.dbg(b);"));
        assertNull(args(1, "LOG.dbg(a, b"), "the call never closes");
    }

    @Test
    public void testScanStopsAfterFiftyLines() {
        String[] source = new String[60];
        source[0] = "LOG.dbg(a,";
        for (int i = 1; i < source.length; i++) {
            source[i] = "b,";
        }
        source[59] = "c);";
        assertNull(args(1, source));

        source[51] = "c);";
        assertNull(args(1, source));

        source[50] = "c);";
        assertTrue(args(1, source).endsWith("b, b, c"));
    }

    // splitting into arguments

    @Test
    public void testSplitArguments() {
        assertEquals(Collections.singletonList("x"), DbgSource.splitArguments("x"));
        assertEquals(Arrays.asList("a", "b", "c"), DbgSource.splitArguments("a, b,c"));
        assertEquals(Arrays.asList("f(a, b)", "c"), DbgSource.splitArguments("f(a, b), c"));
        assertEquals(Arrays.asList("new int[]{1, 2}", "m[1]"), DbgSource.splitArguments("new int[]{1, 2}, m[1]"));
        assertEquals(Arrays.asList("\"a, b\"", "','", "x"), DbgSource.splitArguments("\"a, b\", ',', x"));
        assertEquals(Arrays.asList("\"\\\", \"", "y"), DbgSource.splitArguments("\"\\\", \", y"), "an escaped quote in a string");
        assertEquals(Arrays.asList("'\\''", "z"), DbgSource.splitArguments("'\\'', z"), "an escaped quote in a char");
        assertEquals(Arrays.asList("\"\"\"\n, \"\"\"", "w"), DbgSource.splitArguments("\"\"\"\n, \"\"\", w"), "a text block");
        assertEquals(Collections.singletonList("(p, q) -> p + q"), DbgSource.splitArguments("(p, q) -> p + q"));
    }

    // where the sources are

    @Test
    public void testRootsFollowTheBuildLayout() {
        Path work = Paths.get("work");
        Path module = Paths.get("project", "module");

        assertEquals(Arrays.asList(module.resolve("src/main/java"), module.resolve("src/main/kotlin")),
                     DbgSource.derivedRoots(module.resolve("target/classes"), work));
        assertEquals(Arrays.asList(module.resolve("src/test/java"), module.resolve("src/test/kotlin")),
                     DbgSource.derivedRoots(module.resolve("target/test-classes"), work));
        assertEquals(Collections.singletonList(module.resolve("src/main/java")),
                     DbgSource.derivedRoots(module.resolve("build/classes/java/main"), work));
        assertEquals(Collections.singletonList(module.resolve("src/test/java")),
                     DbgSource.derivedRoots(module.resolve("build/classes/java/test"), work));
        assertEquals(Collections.singletonList(module.resolve("src/main/kotlin")),
                     DbgSource.derivedRoots(module.resolve("build/classes/kotlin/main"), work));
        assertEquals(Collections.singletonList(module.resolve("src/test/kotlin")),
                     DbgSource.derivedRoots(module.resolve("build/classes/kotlin/test"), work));
        assertEquals(Arrays.asList(Paths.get("src/main/java"), Paths.get("src/main/kotlin")),
                     DbgSource.derivedRoots(Paths.get("target/classes"), work), "a module in the current directory");

        List<Path> fallback = Arrays.asList(work.resolve("src/main/java"), work.resolve("src/test/java"), work.resolve("src/main/kotlin"),
                                            work.resolve("src/test/kotlin"), work.resolve("src"));
        assertEquals(fallback, DbgSource.derivedRoots(Paths.get("somewhere", "else"), work),
                     "an unknown layout falls back to the working directory");
        assertEquals(fallback, DbgSource.derivedRoots(module.resolve("classes"), work), "a part of a layout is not the layout");
        assertEquals(fallback, DbgSource.derivedRoots(null, work), "no location");
    }

    @Test
    public void testConfiguredRootsComeAlone() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_SOURCES, " " + temp.resolve("a") + " ,, " + temp.resolve("b") + " ");

        assertEquals(Arrays.asList(temp.resolve("a"), temp.resolve("b")), DbgSource.sourceRoots(DbgSourceTest.class));
    }

    @Test
    public void testRootsOfThisTestComeFromItsClassesDirectory() {
        List<Path> roots = DbgSource.sourceRoots(DbgSourceTest.class);

        assertTrue(roots.get(0).endsWith(Paths.get("src", "test", "java")), roots.toString());
        assertTrue(Files.isRegularFile(roots.get(0).resolve("com/nanolaba/logging/DbgSourceTest.java")));
    }

    @Test
    public void testLocationOfAClass() throws Exception {
        assertTrue(DbgSource.location(DbgSourceTest.class).endsWith(Paths.get("target", "test-classes")));
        assertNull(DbgSource.location(String.class), "a JDK class has no code source");
        assertNull(DbgSource.location(definedAt(null)), "a code source without a location");
        assertNull(DbgSource.location(definedAt(new URL("http://example.com/app.jar"))), "a location that is not a file");

        assertEquals(DbgSource.derivedRoots(null, Paths.get(System.getProperty("user.dir"))), DbgSource.sourceRoots(String.class),
                     "without a location only the working directory is tried");
    }

    /**
     * A class to define again, in a class loader of its own.
     */
    static final class Plain {
    }

    private static Class<?> definedAt(URL location) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(Plain.class.getResource("DbgSourceTest$Plain.class").toURI()));
        ProtectionDomain domain = new ProtectionDomain(new CodeSource(location, (Certificate[]) null), null);
        return new ClassLoader(DbgSourceTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(Plain.class.getName(), bytes, 0, bytes.length, domain);
            }
        }.define();
    }

    // reading the file

    private Path writeSource(String relative, String... lines) throws IOException {
        Path file = temp.resolve("src").resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
        return file;
    }

    private void writeClass(String relative, long modified) throws IOException {
        Path file = temp.resolve("classes").resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1});
        Files.setLastModifiedTime(file, FileTime.fromMillis(modified));
    }

    private String read(StackTraceElement frame) {
        return DbgSource.read(frame, Arrays.asList(temp.resolve("missing"), temp.resolve("src")), temp.resolve("classes"));
    }

    @Test
    public void testTextOfACallInASourceFile() throws IOException {
        Path source = writeSource("pkg/Shop.java", "package pkg;", "class Shop {", "  int t = LOG.dbg(cart.total());", "}");
        Files.setLastModifiedTime(source, FileTime.fromMillis(1_000_000));
        writeClass("pkg/Shop$Inner.class", 2_000_000);

        assertEquals("cart.total()", read(new StackTraceElement("pkg.Shop$Inner", "m", "Shop.java", 3)),
                     "a nested class is found in the file of its top-level class, in the second root");
    }

    @Test
    public void testSourceNewerThanTheClassIsNotTrusted() throws IOException {
        Path source = writeSource("pkg/Shop.java", "class Shop {", "  int t = LOG.dbg(cart.total());", "}");
        writeClass("pkg/Shop.class", 2_000_000);
        StackTraceElement frame = new StackTraceElement("pkg.Shop", "m", "Shop.java", 2);

        Files.setLastModifiedTime(source, FileTime.fromMillis(2_000_001));
        assertNull(read(frame), "edited after compilation");

        Files.setLastModifiedTime(source, FileTime.fromMillis(2_000_000));
        assertEquals("cart.total()", read(frame), "compiled in the same instant");
    }

    @Test
    public void testSourceWithoutAClassFileIsReadAsIs() throws IOException {
        writeSource("Top.java", "LOG.dbg(x);");

        assertEquals("x", read(new StackTraceElement("Top", "m", "Top.java", 1)), "a class in the default package, no class file");
        assertEquals("x", DbgSource.read(new StackTraceElement("Top", "m", "Top.java", 1), Collections.singletonList(temp.resolve("src")), null),
                     "the class is not in a directory, for example in a jar");
    }

    @Test
    public void testMissingSourceOrLineGivesNoText() throws IOException {
        writeSource("pkg/Shop.java", "LOG.dbg(x);");

        assertNull(read(new StackTraceElement("pkg.Gone", "m", "Gone.java", 1)), "no such file");
        assertNull(read(new StackTraceElement("pkg.Shop", "m", "Shop.java", -1)), "compiled without line numbers");
        assertEquals("x", read(new StackTraceElement("pkg.Shop$Inner", "m", null, 1)),
                     "compiled without the file name (javac -g:lines): the top-level class names the file");
        assertNull(DbgSource.read(new StackTraceElement("pkg.Shop", "m", "Shop.java", 1), Collections.emptyList(), null), "no roots");
    }

    @Test
    public void testDirectoryInPlaceOfTheSourceGivesNoText() throws IOException {
        Files.createDirectories(temp.resolve("src/pkg/Dir.java"));

        assertNull(read(new StackTraceElement("pkg.Dir", "m", "Dir.java", 1)));
    }

    @Test
    public void testInvalidPathGivesNoText() {
        assertNull(read(new StackTraceElement("pkg.Bad", "m", "Bad\u0000.java", 1)), "a file name that is no valid path");
    }

    @Test
    public void testLineEndings() throws IOException {
        Path file = temp.resolve("src/pkg/Ends.java");
        Files.createDirectories(file.getParent());
        Files.write(file, "a();\r\nb();\rc();\nLOG.dbg(d);\r\n".getBytes(StandardCharsets.UTF_8));

        assertEquals("d", read(new StackTraceElement("pkg.Ends", "m", "Ends.java", 4)), "CR LF, CR and LF each end a line");
    }

    @Test
    public void testSourceThatIsNotUtf8IsReadAsLatin1() throws IOException {
        Path file = temp.resolve("src/pkg/Latin.java");
        Files.createDirectories(file.getParent());
        Files.write(file, "// café\nLOG.dbg(\"é\");\n".getBytes(StandardCharsets.ISO_8859_1));

        assertEquals("\"é\"", read(new StackTraceElement("pkg.Latin", "m", "Latin.java", 2)));
    }

    // the cache

    @Test
    public void testTextIsCachedPerCallSite() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_SOURCES, temp.resolve("src").toString());
        Path source = writeSource("pkg/Shop.java", "LOG.dbg(first);");
        StackTraceElement frame = new StackTraceElement("pkg.Shop", "m", "Shop.java", 1);
        assertEquals("first", DbgSource.argumentsText(frame, DbgSourceTest.class));

        Files.write(source, Collections.singletonList("LOG.dbg(second);"), StandardCharsets.UTF_8);
        assertEquals("first", DbgSource.argumentsText(frame, DbgSourceTest.class), "the file is read once per call site");

        StackTraceElement absent = new StackTraceElement("pkg.Absent", "m", "Absent.java", 1);
        assertNull(DbgSource.argumentsText(absent, DbgSourceTest.class));
        writeSource("pkg/Absent.java", "LOG.dbg(late);");
        assertNull(DbgSource.argumentsText(absent, DbgSourceTest.class), "a missing text is remembered too");

        DbgSource.clearCache();
        assertEquals("second", DbgSource.argumentsText(frame, DbgSourceTest.class));
        assertEquals("late", DbgSource.argumentsText(absent, DbgSourceTest.class));
    }

    @Test
    public void testInvalidConfiguredRootGivesNoText() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_SOURCES, "bad\u0000root");

        assertNull(DbgSource.argumentsText(new StackTraceElement("pkg.Shop", "m", "Shop.java", 1), DbgSourceTest.class));
    }
}
