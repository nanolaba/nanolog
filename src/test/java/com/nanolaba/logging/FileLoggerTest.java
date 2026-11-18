package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code FileLogger}: entries in a file, appended, rolled over by size, flushed per entry or on demand.
 */
class FileLoggerTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    @TempDir
    Path dir;

    private final Map<String, String> environment = new HashMap<>();
    private final Consumer<Thread> originalHooks = FileLogger.shutdownHooks;
    private final List<Thread> hooks = new ArrayList<>();
    private final List<FileLogger> opened = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        FileLogger.shutdownHooks = hooks::add;
    }

    @AfterEach
    public void tearDown() {
        for (FileLogger logger : opened) {
            logger.close();
        }
        FileLogger.shutdownHooks = originalHooks;
        DefaultLoggerCreator.environment = System::getenv;
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("nanolog.file")) {
                System.clearProperty(key);
            }
        }
        LOG.init();
    }

    /**
     * A logger whose lines are the message alone, installed in LOG.
     */
    private FileLogger plain(Path file) {
        FileLogger logger = new FileLogger(file);
        logger.setShowDate(false).setShowLevel(false).setShowSource(false);
        opened.add(logger);
        LOG.init(logger);
        return logger;
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test
    public void testEntriesReachTheFileAtOnce() throws IOException {
        Path file = dir.resolve("logs").resolve("app.log");
        FileLogger logger = new FileLogger(file);
        logger.setShowDate(false);
        opened.add(logger);
        LOG.init(logger);

        LOG.info("Started");
        LOG.warn("Disk almost full");

        assertEquals("INFO [FileLoggerTest] Started" + NL + "WARN [FileLoggerTest] Disk almost full" + NL, read(file),
                     "on disk without a flush, in a directory created for it");
        assertEquals("", getOutAndClear() + getErrAndClear(), "nothing on the console");
        assertEquals(file, logger.getFile());
    }

    @Test
    public void testAppendsByDefaultAndOverwritesWhenAsked() throws IOException {
        Path file = dir.resolve("app.log");
        Files.write(file, ("old" + NL).getBytes(StandardCharsets.UTF_8));

        FileLogger appending = plain(file);
        assertTrue(appending.isAppend());
        LOG.info("new");
        assertEquals("old" + NL + "new" + NL, read(file));
        appending.close();

        plain(file).setAppend(false);
        LOG.info("fresh");
        LOG.info("again");
        assertEquals("fresh" + NL + "again" + NL, read(file), "overwritten once, when the file opens");
    }

    @Test
    public void testRolloverBySize() throws IOException {
        Path file = dir.resolve("app.log");
        FileLogger logger = plain(file).setMaxSize(2 * ("entry 1" + NL).length()).setBackups(2);

        for (int i = 1; i <= 7; i++) {
            LOG.info("entry " + i);
        }

        assertEquals("entry 7" + NL, read(file));
        assertEquals("entry 5" + NL + "entry 6" + NL, read(dir.resolve("app.log.1")));
        assertEquals("entry 3" + NL + "entry 4" + NL, read(dir.resolve("app.log.2")));
        assertFalse(Files.exists(dir.resolve("app.log.3")), "the oldest backup is deleted");
        assertEquals(2, logger.getBackups());
    }

    @Test
    public void testRolloverWithoutBackupsStartsOver() throws IOException {
        Path file = dir.resolve("app.log");
        plain(file).setMaxSize(("entry 1" + NL).length()).setBackups(0);

        LOG.info("entry 1");
        LOG.info("entry 2");

        assertEquals("entry 2" + NL, read(file));
        assertFalse(Files.exists(dir.resolve("app.log.1")));
    }

    @Test
    public void testALineLongerThanTheLimitIsWrittenWhole() throws IOException {
        Path file = dir.resolve("app.log");
        plain(file).setMaxSize(4);

        LOG.info("a long line");
        LOG.info("next");

        assertEquals("next" + NL, read(file));
        assertEquals("a long line" + NL, read(dir.resolve("app.log.1")));
        assertFalse(Files.exists(dir.resolve("app.log.2")), "an empty file is never rolled over");
    }

    @Test
    public void testAFileAppendedToCountsWhatItHolds() throws IOException {
        Path file = dir.resolve("app.log");
        Files.write(file, ("entry 0" + NL).getBytes(StandardCharsets.UTF_8));
        plain(file).setMaxSize(("entry 1" + NL).length());

        LOG.info("entry 1");

        assertEquals("entry 0" + NL, read(dir.resolve("app.log.1")), "the file was full already");
        assertEquals("entry 1" + NL, read(file));
    }

    @Test
    public void testARolloverInTheBufferModeKeepsWhatWasBuffered() throws IOException {
        Path file = dir.resolve("app.log");
        plain(file).setFlush("buffer").setMaxSize(("entry 1" + NL).length());

        LOG.info("entry 1");
        LOG.info("entry 2");

        assertEquals("entry 1" + NL, read(dir.resolve("app.log.1")));
    }

    @Test
    public void testBufferedUntilFlushed() throws IOException {
        Path file = dir.resolve("app.log");
        FileLogger logger = plain(file).setFlush("buffer");

        LOG.info("held");
        assertEquals("", read(file), "the buffer holds it");
        assertEquals(1, hooks.size(), "a buffered file is flushed when the JVM exits");

        LOG.flush();
        assertEquals("held" + NL, read(file));

        LOG.info("at exit");
        hooks.get(0).run();
        assertEquals("held" + NL + "at exit" + NL, read(file));
        assertEquals("buffer", logger.getFlush());
    }

    @Test
    public void testJsonLines() throws IOException {
        Path file = dir.resolve("app.json");
        FileLogger logger = plain(file).setFormat("json");

        LOG.info("Paid {}", 5);

        String line = read(file);
        assertTrue(line.startsWith("{\"ts\":\"") && line.contains("\"message\":\"Paid 5\",\"template\":\"Paid {}\"") && line.endsWith("}" + NL), line);
        assertEquals("json", logger.getFormat());
    }

    @Test
    public void testCloseWritesTheBufferAndReleasesTheFile() throws IOException {
        Path file = dir.resolve("app.log");
        FileLogger logger = plain(file).setFlush("buffer");
        LOG.info("buffered");

        logger.close();
        assertEquals("buffered" + NL, read(file));

        Files.move(file, dir.resolve("moved.log"));
        LOG.info("after");
        LOG.flush();
        assertEquals("after" + NL, read(file), "the next entry opens the file anew");
        assertEquals("buffered" + NL, read(dir.resolve("moved.log")));
    }

    @Test
    public void testCloseAndReopen() throws IOException {
        Path file = dir.resolve("app.log");
        FileLogger logger = plain(file).setAppend(false);
        LOG.info("one");
        logger.close();
        logger.close();

        LOG.info("two");

        assertEquals("one" + NL + "two" + NL, read(file), "after close the file opens again and is appended to");
    }

    @Test
    public void testAFileThatCannotBeWrittenFallsBackToStandardError() {
        Path file = dir.resolve("a-file").resolve("app.log");
        try {
            Files.write(dir.resolve("a-file"), new byte[0]);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        plain(file);

        LOG.info("first");
        LOG.info("second");

        String err = getErrAndClear();
        assertTrue(err.startsWith("[NANOLOG] Cannot write the log file " + file.toAbsolutePath() + ": "), err);
        assertTrue(err.contains("; entries go to System.err instead" + NL + "first" + NL + "second" + NL), err);
        assertEquals(1, err.split("\\[NANOLOG]").length - 1, "one diagnostic");
    }

    @Test
    public void testARolloverThatFailsLetsTheFileGrow() throws IOException {
        Path file = dir.resolve("app.log");
        Files.createDirectories(dir.resolve("app.log.1").resolve("occupied"));
        // one backup: the rollover first deletes app.log.1, which a directory with a file in it occupies
        plain(file).setMaxSize(("entry 1" + NL).length()).setBackups(1);

        LOG.info("entry 1");
        LOG.info("entry 2");
        LOG.info("entry 3");

        assertEquals("entry 1" + NL + "entry 2" + NL + "entry 3" + NL, read(file));
        String err = getErrAndClear();
        assertTrue(err.startsWith("[NANOLOG] Cannot roll over the log file " + file.toAbsolutePath() + ": "), err);
        assertTrue(err.endsWith("; it keeps growing" + NL), err);
    }

    @Test
    public void testSettings() {
        Path file = dir.resolve("from-settings.log");
        System.setProperty(FileLogger.PROPERTY_FILE, file.toString());
        System.setProperty(FileLogger.PROPERTY_APPEND, "false");
        System.setProperty(FileLogger.PROPERTY_MAX_SIZE, " 10MB ");
        System.setProperty(FileLogger.PROPERTY_BACKUPS, "5");
        System.setProperty(FileLogger.PROPERTY_FLUSH, "BUFFER");
        System.setProperty(FileLogger.PROPERTY_FORMAT, "json");

        FileLogger logger = new FileLogger();
        opened.add(logger);

        assertEquals(file, logger.getFile());
        assertFalse(logger.isAppend());
        assertEquals(10L * 1024 * 1024, logger.getMaxSize());
        assertEquals(5, logger.getBackups());
        assertEquals("buffer", logger.getFlush());
        assertEquals("json", logger.getFormat());
        assertEquals("", getErrAndClear());

        System.setProperty(FileLogger.PROPERTY_MAX_SIZE, "0");
        System.setProperty(FileLogger.PROPERTY_BACKUPS, "0");
        FileLogger zero = new FileLogger();
        opened.add(zero);
        assertEquals(0, zero.getMaxSize());
        assertEquals(0, zero.getBackups());
        assertEquals("", getErrAndClear(), "0 is a valid size and a valid number of backups");
    }

    @Test
    public void testDefaults() {
        FileLogger logger = new FileLogger();
        opened.add(logger);

        assertEquals(Paths.get("nanolog.log"), logger.getFile());
        assertTrue(logger.isAppend());
        assertEquals(0, logger.getMaxSize(), "no rollover");
        assertEquals(3, logger.getBackups());
        assertEquals("entry", logger.getFlush());
        assertEquals("text", logger.getFormat());
    }

    @Test
    public void testSizes() {
        assertEquals(512, FileLogger.parseSize("512"));
        assertEquals(2048, FileLogger.parseSize("2kb"));
        assertEquals(3L * 1024 * 1024, FileLogger.parseSize("3 MB"));
        assertEquals(1024L * 1024 * 1024, FileLogger.parseSize("1GB"));
        assertEquals(0, FileLogger.parseSize("0"));
        assertEquals(-1, FileLogger.parseSize("big"));
        assertEquals(-1, FileLogger.parseSize("-5MB"));
        assertEquals(-1, FileLogger.parseSize("MB"));
    }

    @Test
    public void testInvalidSettings() {
        System.setProperty(FileLogger.PROPERTY_MAX_SIZE, "big");
        System.setProperty(FileLogger.PROPERTY_BACKUPS, "-1");
        System.setProperty(FileLogger.PROPERTY_FLUSH, "sometimes");
        System.setProperty(FileLogger.PROPERTY_FORMAT, "xml");

        FileLogger logger = new FileLogger();
        opened.add(logger);

        assertEquals(0, logger.getMaxSize());
        assertEquals(3, logger.getBackups());
        assertEquals("entry", logger.getFlush());
        assertEquals("text", logger.getFormat());
        String err = getErrAndClear();
        assertTrue(err.contains("Invalid value \"big\" of \"nanolog.file.maxSize\": expected a size such as 10MB, 0 for no rollover"), err);
        assertTrue(err.contains("Invalid value \"-1\" of \"nanolog.file.backups\": expected a number of backups, 0 for none"), err);
        assertTrue(err.contains("Invalid value \"sometimes\" of \"nanolog.file.flush\": expected entry or buffer"), err);
        assertTrue(err.contains("Invalid value \"xml\" of \"nanolog.file.format\": expected text or json"), err);
    }

    @Test
    public void testInvalidSetterValuesAreReported() {
        FileLogger logger = new FileLogger(dir.resolve("app.log"));
        opened.add(logger);

        logger.setFlush("never").setFormat("yaml").setMaxSize(-1).setBackups(-2);

        assertEquals("entry", logger.getFlush());
        assertEquals("text", logger.getFormat());
        assertEquals(0, logger.getMaxSize());
        assertEquals(0, logger.getBackups());
        String err = getErrAndClear();
        assertTrue(err.contains("Invalid flush mode \"never\" in setFlush: expected entry or buffer; nothing changed"), err);
        assertTrue(err.contains("Invalid format \"yaml\" in setFormat: expected text or json; nothing changed"), err);
    }

    @Test
    public void testFlushReachesEveryLoggerOfAComposite() throws IOException {
        Path first = dir.resolve("first.log");
        Path second = dir.resolve("second.log");
        FileLogger one = new FileLogger(first).setFlush("buffer");
        FileLogger two = new FileLogger(second).setFlush("buffer");
        opened.add(one);
        opened.add(two);
        LOG.init(new CompositeLogger(new ConsoleLogger(), one, two));

        LOG.warn("both");
        LOG.flush();

        assertTrue(read(first).contains("both") && read(second).contains("both"));
    }

    @Test
    public void testADelegateWhoseFlushThrowsDoesNotStopTheOthers() throws IOException {
        LOG.clearReportedLoggerFailures();
        Path file = dir.resolve("app.log");
        FileLogger buffered = new FileLogger(file).setFlush("buffer");
        opened.add(buffered);
        LOG.init(new CompositeLogger(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public void flush() {
                throw new IllegalStateException("broken");
            }
        }, buffered));

        LOG.warn("kept");
        LOG.flush();

        assertTrue(read(file).contains("kept"));
        assertTrue(getErrAndClear().contains("threw java.lang.IllegalStateException: broken; the other loggers of the CompositeLogger still receive its entries"));
        LOG.clearReportedLoggerFailures();
    }

    @Test
    public void testAFlushThatThrowsIsReported() {
        LOG.clearReportedLoggerFailures();
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public void flush() {
                throw new IllegalStateException("broken");
            }
        });

        LOG.flush();

        assertTrue(getErrAndClear().contains("threw java.lang.IllegalStateException: broken"));
        LOG.clearReportedLoggerFailures();
        LOG.init(entry -> {
        });
        LOG.flush();
        assertEquals("", getErrAndClear(), "a logger without a flush of its own does nothing");
    }

    @Test
    public void testAgentModeLeavesTheFileAlone() {
        FileLogger logger = new FileLogger(dir.resolve("app.log"));
        opened.add(logger);

        assertNull(Agent.shorten(logger), "agent mode shortens the console, not a file");
        assertTrue(logger.isEnabled(LogEntry.LogEntryLevel.DEBUG, FileLoggerTest.class));
        assertSame(logger, logger.setShowDate(true));
    }
}
