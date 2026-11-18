package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Several {@code nanolog.properties} on the classpath: reported when one comes from a jar, the case of a library that
 * ships its own file.
 */
class SeveralConfigFilesTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();
    private static final String FILE = DefaultLoggerCreator.DEFAULT_CONFIGURATION_FILE;
    private static final String KEY = ConsoleLogger.PROPERTY_SHOW_THREAD;

    private final Map<String, String> environment = new HashMap<>();
    private final ClassLoader original = Thread.currentThread().getContextClassLoader();
    private Path temp;

    @BeforeEach
    public void setUp() throws IOException {
        DefaultLoggerCreator.environment = environment::get;
        temp = Files.createTempDirectory("nanolog-several");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Thread.currentThread().setContextClassLoader(original);
        DefaultLoggerCreator.environment = System::getenv;
        System.clearProperty(KEY);
        LOG.init();
        getErrAndClear();
        try (java.util.stream.Stream<Path> files = Files.walk(temp)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    /**
     * A directory with a configuration file that sets the key to the value.
     */
    private URL directory(String name, String value) throws IOException {
        Path dir = Files.createDirectories(temp.resolve(name));
        Files.write(dir.resolve(FILE), (KEY + "=" + value).getBytes(StandardCharsets.ISO_8859_1));
        return dir.toUri().toURL();
    }

    /**
     * A jar with a configuration file that sets the key to the value.
     */
    private URL jar(String name, String value) throws IOException {
        Path jar = temp.resolve(name + ".jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream zip = new JarOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(FILE));
            zip.write((KEY + "=" + value).getBytes(StandardCharsets.ISO_8859_1));
            zip.closeEntry();
        }
        return jar.toUri().toURL();
    }

    /**
     * Initialises {@code LOG} with a class loader that sees only these locations, in this order.
     *
     * @return what {@code LOG.init()} printed to {@code System.err}
     */
    private String init(URLClassLoader loader) throws IOException {
        try {
            Thread.currentThread().setContextClassLoader(loader);
            LOG.init();
            return getErrAndClear();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
            loader.close();
        }
    }

    private static String resource(URL location) throws IOException {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{location}, null)) {
            return String.valueOf(loader.getResource(FILE));
        }
    }

    @Test
    public void testAJarAndADirectoryAreReported() throws IOException {
        URL application = directory("classes", "true");
        URL library = jar("library", "false");

        String err = init(new URLClassLoader(new URL[]{application, library}, null));

        assertEquals("[NANOLOG] Several files \"nanolog.properties\" are on the classpath, one in a jar at least; read: " +
                     resource(application) + "; ignored: " + resource(library) + ". The order of the classpath decides which one " +
                     "is read: a library should not ship this file, and nanolog.config names another one" + NL, err);
        assertEquals("true", System.getProperty(KEY), "the first file is read");
    }

    @Test
    public void testTwoJarsAreReported() throws IOException {
        URL first = jar("first", "false");
        URL second = jar("second", "true");
        URL third = directory("classes", "true");

        String err = init(new URLClassLoader(new URL[]{first, second, third}, null));

        assertTrue(err.contains("; read: " + resource(first) + "; ignored: " + resource(second) + ", " + resource(third) + ". "), err);
        assertEquals("false", System.getProperty(KEY));
    }

    @Test
    public void testTwoDirectoriesAreAnOverride() throws IOException {
        // the test resources override the main ones: a legitimate pair
        URL test = directory("test-classes", "true");
        URL main = directory("classes", "false");

        assertEquals("", init(new URLClassLoader(new URL[]{test, main}, null)));
        assertEquals("true", System.getProperty(KEY));
    }

    @Test
    public void testTheTestFileReplacesTheMainOneWhole() throws IOException {
        // src/test/resources comes first on the test classpath; its file is read alone, not merged with the main one
        Path test = Files.createDirectories(temp.resolve("test-classes"));
        Files.write(test.resolve(FILE), (KEY + "=true").getBytes(StandardCharsets.ISO_8859_1));
        Path main = Files.createDirectories(temp.resolve("classes"));
        Files.write(main.resolve(FILE), (KEY + "=false\n" + ConsoleLogger.PROPERTY_SHOW_LOCATION + "=true").getBytes(StandardCharsets.ISO_8859_1));

        try {
            assertEquals("", init(new URLClassLoader(new URL[]{test.toUri().toURL(), main.toUri().toURL()}, null)));
            assertEquals("true", System.getProperty(KEY));
            assertEquals(null, System.getProperty(ConsoleLogger.PROPERTY_SHOW_LOCATION), "a key of the main file only is not read");
        } finally {
            System.clearProperty(ConsoleLogger.PROPERTY_SHOW_LOCATION);
        }
    }

    @Test
    public void testOneFileFromAJarIsNotReported() throws IOException {
        assertEquals("", init(new URLClassLoader(new URL[]{jar("only", "true")}, null)));
        assertEquals("true", System.getProperty(KEY));
    }

    @Test
    public void testAClassLoaderThatCannotListTheFilesSkipsTheCheck() throws IOException {
        URL application = directory("classes", "true");
        URL library = jar("library", "false");

        String err = init(new URLClassLoader(new URL[]{application, library}, null) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                throw new IOException("denied");
            }
        });

        assertEquals("", err);
        assertEquals("true", System.getProperty(KEY));
    }
}
