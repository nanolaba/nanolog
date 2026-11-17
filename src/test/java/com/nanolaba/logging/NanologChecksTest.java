package com.nanolaba.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link NanologChecks#assertNoDbgCalls}: the {@code LOG.dbg} calls left in the sources, found by a scan of the text
 * that skips comments and literals.
 */
class NanologChecksTest {

    private static final String NL = System.lineSeparator();

    @TempDir
    Path root;

    private static List<Integer> lines(String code) {
        return NanologChecks.dbgCalls(code);
    }

    private static List<Integer> none() {
        return Collections.emptyList();
    }

    private Path write(String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // WHAT COUNTS AS A CALL

    @Test
    public void testCalls() {
        assertEquals(Arrays.asList(1), lines("LOG.dbg(x);"));
        assertEquals(Arrays.asList(3), lines("class A {\n  void run() {\n    BigDecimal total = LOG.dbg(order.total());\n"));
        assertEquals(Arrays.asList(1, 1), lines("LOG.dbg(a); LOG.dbg(b);"));
        assertEquals(Arrays.asList(1), lines("com.nanolaba.logging.LOG.dbg();"));
        assertEquals(Arrays.asList(1), lines("LOG . dbg (x);"));
        assertEquals(Arrays.asList(1), lines("LOG\n    .dbg(x);"), "the line of LOG");
        assertEquals(Arrays.asList(1), lines("LOG./* why */dbg(x);"));
    }

    @Test
    public void testLimitedCalls() {
        assertEquals(Arrays.asList(1), lines("LOG.once().dbg(x);"));
        assertEquals(Arrays.asList(1), lines("LOG.atMostEvery(seconds(5), TimeUnit.SECONDS).dbg();"));
        assertEquals(Arrays.asList(1), lines("LOG.atMostEvery(f(g(1)), unit)\n    .dbg(x);"));
        assertEquals(Arrays.asList(1), lines("LOG.atMostEvery(\")\", unit).dbg(x);"), "a parenthesis in a string does not count");
    }

    @Test
    public void testMethodReferenceAndStaticImport() {
        assertEquals(Arrays.asList(1), lines("values.forEach(LOG::dbg);"));
        assertEquals(Arrays.asList(1), lines("import static com.nanolaba.logging.LOG.dbg;"));
    }

    @Test
    public void testOtherCallsAreNotCounted() {
        assertEquals(none(), lines("LOG.debug(x); LOG.dbgx(x); LOG.info(\"dbg\");"));
        assertEquals(none(), lines("XLOG.dbg(x); LOGGER.dbg(x); log.dbg(x); dbg(x);"));
        assertEquals(none(), lines("LOG.once().info(x); LOG.once().debug(x);"));
        assertEquals(none(), lines("LOG.once.dbg(x);"));
        // the first tokens of the text are not taken for the end of a chain that does not close
        assertEquals(none(), lines("a.dbg; LOG.once.dbg(x);"));
        assertEquals(none(), lines("a.dbg; LOG.once(x"));
        assertEquals(none(), lines("LOG.once()dbg(x);"));
        assertEquals(none(), lines("LOG.once().(x);"));
        assertEquals(none(), lines("LOG::info; LOG:dbg; LOG:.dbg;"));
        assertEquals(none(), lines("LOG.once("));
        assertEquals(none(), lines("LOG.once()"));
        assertEquals(none(), lines("LOG.once()."));
        assertEquals(none(), lines("LOG."));
        assertEquals(none(), lines("LOG::"));
        assertEquals(none(), lines("LOG:"));
        assertEquals(none(), lines("LOG"));
        assertEquals(none(), lines(""));
    }

    @Test
    public void testCommentsAndLiteralsAreSkipped() {
        assertEquals(none(), lines("// LOG.dbg(x);"));
        assertEquals(none(), lines("/* LOG.dbg(x); */"));
        assertEquals(none(), lines("/** {@code LOG.dbg(order.total())} */"));
        assertEquals(none(), lines("String s = \"LOG.dbg(x)\";"));
        assertEquals(none(), lines("String s = \"\"\"\n    LOG.dbg(x)\n    \"\"\";"));
        assertEquals(Arrays.asList(1), lines("char c = '\"'; LOG.dbg(c);"), "a quote in a char does not open a string");
    }

    @Test
    public void testLinesAreCountedThroughCommentsAndTextBlocks() {
        assertEquals(Arrays.asList(4), lines("/*\n\n*/\nLOG.dbg(x);"));
        assertEquals(Arrays.asList(3), lines("String s = \"\"\"\n    text\n    \"\"\" + LOG.dbg(x);"));
        assertEquals(Arrays.asList(2), lines("// first\r\nLOG.dbg(x);"));
    }

    // SCANNING DIRECTORIES

    @Test
    public void testCleanSourcesPass() throws IOException {
        write("com/shop/OrderService.java", "class OrderService {\n    // LOG.dbg(order) helped here\n}\n");

        NanologChecks.assertNoDbgCalls(root.toString());
    }

    @Test
    public void testCallsAreListedWithFileLineAndCode() throws IOException {
        Path order = write("com/shop/OrderService.java", "class OrderService {\n    BigDecimal total = LOG.dbg(order.total());\n}\n");
        Path cart = write("com/shop/web/Cart.kt", "class Cart {\n\n\tfun add() = LOG.dbg()\r\n}\n");
        write("com/shop/notes.txt", "LOG.dbg(x)");
        write("com/shop/Old.java.bak", "LOG.dbg(x)");

        AssertionError error = assertThrows(AssertionError.class, () -> NanologChecks.assertNoDbgCalls(root.toString()));

        assertEquals("2 LOG.dbg calls left in the code; remove them:" +
                     NL + order + ":2: BigDecimal total = LOG.dbg(order.total());" +
                     NL + cart + ":3: fun add() = LOG.dbg()", error.getMessage());
    }

    @Test
    public void testFilesAreListedInPathOrder() throws IOException {
        Path b = write("b/B.java", "LOG.dbg(b);");
        Path a = write("a/A.java", "LOG.dbg(a);");

        AssertionError error = assertThrows(AssertionError.class, () -> NanologChecks.assertNoDbgCalls(root.toString()));

        assertEquals("2 LOG.dbg calls left in the code; remove them:" + NL + a + ":1: LOG.dbg(a);" + NL + b + ":1: LOG.dbg(b);",
                     error.getMessage());
    }

    @Test
    public void testOneCall() throws IOException {
        Path file = write("A.java", "LOG.dbg(a);");

        AssertionError error = assertThrows(AssertionError.class, () -> NanologChecks.assertNoDbgCalls(root.toString()));

        assertEquals("1 LOG.dbg call left in the code; remove it:" + NL + file + ":1: LOG.dbg(a);", error.getMessage());
    }

    @Test
    public void testADirectoryNamedLikeASourceIsNotRead() throws IOException {
        Files.createDirectories(root.resolve("odd.java"));
        write("odd.java/A.java", "class A {}");

        NanologChecks.assertNoDbgCalls(root.toString());
    }

    @Test
    public void testEveryDirectoryIsScanned() throws IOException {
        Path main = write("main/A.java", "LOG.dbg(a);");
        Path test = write("test/B.java", "LOG.dbg(b);");
        Path other = write("other/C.java", "LOG.dbg(c);");

        AssertionError error = assertThrows(AssertionError.class, () -> NanologChecks.assertNoDbgCalls(
                root.resolve("main").toString(), root.resolve("test").toString(), root.resolve("other").toString()));

        assertEquals("3 LOG.dbg calls left in the code; remove them:" +
                     NL + main + ":1: LOG.dbg(a);" + NL + test + ":1: LOG.dbg(b);" + NL + other + ":1: LOG.dbg(c);",
                     error.getMessage());
    }

    @Test
    public void testAMissingDirectoryIsAnError() {
        Path missing = root.resolve("src/main/java");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                      () -> NanologChecks.assertNoDbgCalls(root.toString(), missing.toString()));

        assertEquals("No source directory " + missing.toAbsolutePath(), error.getMessage());
    }

    @Test
    public void testAFileIsNotADirectory() throws IOException {
        Path file = write("A.java", "class A {}");

        assertThrows(IllegalArgumentException.class, () -> NanologChecks.assertNoDbgCalls(file.toString()));
    }
}
