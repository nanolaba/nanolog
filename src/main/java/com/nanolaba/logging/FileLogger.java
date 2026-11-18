package com.nanolaba.logging;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.function.Consumer;

import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_PREFIX;
import static com.nanolaba.logging.DefaultLoggerCreator.getProperty;
import static com.nanolaba.logging.DefaultLoggerCreator.printDiagnostic;
import static com.nanolaba.logging.DefaultLoggerCreator.printInvalid;
import static com.nanolaba.logging.DefaultLoggerCreator.quietly;
import static com.nanolaba.logging.DefaultLoggerCreator.readBoolean;
import static com.nanolaba.logging.DefaultLoggerCreator.readChoice;

/**
 * Writes entries to a file, in the layout of {@link ConsoleLogger} or as the JSON lines of {@link JsonLogger}:
 * {@code nanolog.logger=com.nanolaba.logging.FileLogger} with {@code nanolog.file=logs/app.log}. With the console as
 * well: {@code LOG.init(new CompositeLogger(new ConsoleLogger(), new FileLogger(path)))}.
 * <p>
 * The file is appended to, and its directory created, when the first entry comes. By default each entry is written
 * through at once, so what was logged is in the file even if the JVM dies; {@code buffer} keeps entries in memory
 * until {@link LOG#flush()}, a full buffer or the exit of the JVM. With {@code maxSize} the file rolls over:
 * {@code app.log} becomes {@code app.log.1}, the older backups move one up, and the oldest beyond {@code backups} is
 * deleted. There are no date-based policies, no compression and no asynchronous writing.
 * <p>
 * The levels and the layout follow the settings of {@link ConsoleLogger} ({@code nanolog.level},
 * {@code nanolog.console.*}), except that repeats are never collapsed and there are no colours. A file that cannot be
 * written is reported once, and its entries go to {@code System.err} instead; nothing is thrown.
 */
public class FileLogger extends ConsoleLogger {

    /**
     * The file, relative to the working directory: {@code nanolog.log} by default.
     */
    public static final String PROPERTY_FILE = PROPERTY_PREFIX + "file";
    /**
     * {@code true}, the default, appends to the file; {@code false} empties it when the logger opens it.
     */
    public static final String PROPERTY_APPEND = PROPERTY_FILE + ".append";
    /**
     * The size at which the file rolls over, such as {@code 10MB}, {@code 512KB} or a number of bytes; {@code 0}, the
     * default, never rolls it over.
     */
    public static final String PROPERTY_MAX_SIZE = PROPERTY_FILE + ".maxSize";
    /**
     * How many rolled-over files are kept, {@code app.log.1} the newest: {@code 3} by default, {@code 0} for none.
     */
    public static final String PROPERTY_BACKUPS = PROPERTY_FILE + ".backups";
    /**
     * {@code entry}, the default, writes each entry through at once; {@code buffer} keeps entries in memory until a
     * flush.
     */
    public static final String PROPERTY_FLUSH = PROPERTY_FILE + ".flush";
    /**
     * {@code text}, the default, in the layout of {@link ConsoleLogger}; {@code json} for the lines of
     * {@link JsonLogger}.
     */
    public static final String PROPERTY_FORMAT = PROPERTY_FILE + ".format";

    private static final String ENTRY = "entry";
    private static final String BUFFER = "buffer";
    private static final String TEXT = "text";
    private static final String JSON = "json";
    private static final int DEFAULT_BACKUPS = 3;

    /**
     * Registers the flush of a buffered file at the exit of the JVM; tests replace it and must restore it.
     */
    static Consumer<Thread> shutdownHooks = Runtime.getRuntime()::addShutdownHook;

    private final Path file;
    private boolean append;
    private long maxSize;
    private int backups;
    private String flush;
    private JsonLogger json;

    // guarded by this
    private OutputStream out;
    private long written;
    private boolean opened;
    private boolean failed;
    private boolean hooked;

    /**
     * A logger for the file of {@code nanolog.file}, with the other settings of {@code nanolog.file.*}; the
     * constructor that {@code nanolog.logger} calls.
     */
    public FileLogger() {
        this(fileOf(getProperty(PROPERTY_FILE)));
    }

    /**
     * A logger for a file, with the settings of {@code nanolog.file.*} other than {@code nanolog.file}; the setters
     * change them.
     *
     * @param file the file, relative to the working directory or absolute
     */
    public FileLogger(Path file) {
        this.file = file;
        this.append = readBoolean(PROPERTY_APPEND, true);
        this.maxSize = readMaxSize();
        this.backups = readBackups();
        this.flush = readChoice(PROPERTY_FLUSH, ENTRY, BUFFER);
        applyFormat(readChoice(PROPERTY_FORMAT, TEXT, JSON));
    }

    private static Path fileOf(String name) {
        return Paths.get(name == null || name.trim().isEmpty() ? "nanolog.log" : name.trim());
    }

    private static long readMaxSize() {
        String value = getProperty(PROPERTY_MAX_SIZE);
        if (value == null) {
            return 0;
        }
        long size = parseSize(value);
        if (size < 0) {
            printInvalid(PROPERTY_MAX_SIZE, value, "expected a size such as 10MB, 0 for no rollover");
            return 0;
        }
        return size;
    }

    private static int readBackups() {
        String value = getProperty(PROPERTY_BACKUPS);
        if (value != null) {
            try {
                int number = Integer.parseInt(value.trim());
                if (number >= 0) {
                    return number;
                }
            } catch (NumberFormatException e) {
                // reported below
            }
            printInvalid(PROPERTY_BACKUPS, value, "expected a number of backups, 0 for none");
        }
        return DEFAULT_BACKUPS;
    }

    /**
     * Reads a size: a number of bytes, or a number followed by {@code KB}, {@code MB} or {@code GB}, ignoring case and
     * spaces.
     *
     * @return the size in bytes, or {@code -1} if the value is no size
     */
    static long parseSize(String value) {
        String text = value.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        long unit = 1;
        String[] units = {"KB", "MB", "GB"};
        for (int i = 0; i < units.length; i++) {
            if (text.endsWith(units[i])) {
                unit = 1L << (10 * (i + 1));
                text = text.substring(0, text.length() - 2);
            }
        }
        try {
            long number = Long.parseLong(text);
            return number < 0 ? -1 : number * unit;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void applyFormat(String format) {
        // quietly: the settings of the JSON logger are those of this logger, which reported what is wrong with them
        json = JSON.equals(format) ? quietly(JsonLogger::new) : null;
    }

    // SETTINGS

    /**
     * Returns the file.
     *
     * @return the file, as given
     */
    public Path getFile() {
        return file;
    }

    /**
     * Returns {@code true} if the file is appended to when the logger opens it.
     *
     * @return {@code true} if the file is appended to
     */
    public boolean isAppend() {
        return append;
    }

    /**
     * Whether the file is appended to or emptied when the logger opens it first; also set by
     * {@code nanolog.file.append}. A file opened again after {@link #close()} is always appended to.
     *
     * @param append {@code true} to append, the default
     * @return this logger, for chaining
     */
    public synchronized FileLogger setAppend(boolean append) {
        this.append = append;
        return this;
    }

    /**
     * Returns the size at which the file rolls over.
     *
     * @return the size in bytes; {@code 0} for none
     */
    public long getMaxSize() {
        return maxSize;
    }

    /**
     * Sets the size at which the file rolls over; also set by {@code nanolog.file.maxSize}. An entry that would make
     * the file larger goes to a new file; one larger by itself is written whole.
     *
     * @param maxSize the size in bytes; zero or less for no rollover, the default
     * @return this logger, for chaining
     */
    public synchronized FileLogger setMaxSize(long maxSize) {
        this.maxSize = Math.max(0, maxSize);
        return this;
    }

    /**
     * Returns how many rolled-over files are kept.
     *
     * @return the number of backups
     */
    public int getBackups() {
        return backups;
    }

    /**
     * Sets how many rolled-over files are kept, {@code app.log.1} the newest; also set by {@code nanolog.file.backups}.
     *
     * @param backups the number of backups, {@code 3} by default; zero or less keeps none, and a full file starts over
     * @return this logger, for chaining
     */
    public synchronized FileLogger setBackups(int backups) {
        this.backups = Math.max(0, backups);
        return this;
    }

    /**
     * Returns when entries reach the file.
     *
     * @return {@code entry} or {@code buffer}
     */
    public String getFlush() {
        return flush;
    }

    /**
     * Sets when entries reach the file; also set by {@code nanolog.file.flush}. A file opened already keeps its mode
     * until it opens again.
     *
     * @param flush {@code entry}, the default, writes each entry through; {@code buffer} keeps entries in memory until
     *              {@link LOG#flush()}, a full buffer or the exit of the JVM
     * @return this logger, for chaining
     */
    public synchronized FileLogger setFlush(String flush) {
        String mode = choice(flush, ENTRY, BUFFER);
        if (mode == null) {
            printDiagnostic("Invalid flush mode \"" + flush + "\" in setFlush: expected entry or buffer; nothing changed");
        } else {
            this.flush = mode;
        }
        return this;
    }

    /**
     * Returns the format of the lines.
     *
     * @return {@code text} or {@code json}
     */
    public String getFormat() {
        return json == null ? TEXT : JSON;
    }

    /**
     * Sets the format of the lines; also set by {@code nanolog.file.format}.
     *
     * @param format {@code text}, the default, in the layout of {@link ConsoleLogger}; {@code json} for the lines of
     *               {@link JsonLogger}
     * @return this logger, for chaining
     */
    public synchronized FileLogger setFormat(String format) {
        String chosen = choice(format, TEXT, JSON);
        if (chosen == null) {
            printDiagnostic("Invalid format \"" + format + "\" in setFormat: expected text or json; nothing changed");
        } else {
            applyFormat(chosen);
        }
        return this;
    }

    private static String choice(String value, String... choices) {
        for (String choice : choices) {
            if (choice.equalsIgnoreCase(String.valueOf(value).trim())) {
                return choice;
            }
        }
        return null;
    }

    // WRITING

    /**
     * Writes an entry to the file, or to {@code System.err} once the file has failed.
     *
     * @param entry the entry to write
     */
    @Override
    public synchronized void log(LogEntry entry) {
        String line = (json == null ? createLogString(entry) : json.createLogString(entry)) + System.lineSeparator();
        if (failed) {
            SystemStreams.err().print(line);
            return;
        }
        try {
            if (out == null) {
                // the setting applies when the file opens first; opened again after close(), it is appended to
                open(opened || append);
            }
            write(line.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            failed = true;
            printDiagnostic("Cannot write the log file " + file.toAbsolutePath() + ": " + e + "; entries go to System.err instead");
            SystemStreams.err().print(line);
        }
    }

    private void open(boolean appending) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        OutputStream stream = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                                                    appending ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
        written = appending ? Files.size(file) : 0;
        opened = true;
        if (BUFFER.equals(flush)) {
            out = new BufferedOutputStream(stream);
            if (!hooked) {
                hooked = true;
                shutdownHooks.accept(new Thread(() -> LOG.flush(this), "nanolog-file"));
            }
        } else {
            out = stream;
        }
    }

    private void write(byte[] bytes) throws IOException {
        if (maxSize > 0 && written > 0 && written + bytes.length > maxSize) {
            rollOver();
        }
        // the stream of the file has no buffer of its own: in the entry mode the bytes reach the file here
        out.write(bytes);
        written += bytes.length;
    }

    /**
     * Moves the file to {@code .1} and each backup one up, deleting the oldest, and opens a new file. A move that fails
     * is reported once; the file is then appended to and never rolled over again.
     */
    private void rollOver() throws IOException {
        out.close();
        out = null;
        try {
            if (backups > 0) {
                Files.deleteIfExists(backup(backups));
                for (int i = backups; i >= 2; i--) {
                    if (Files.exists(backup(i - 1))) {
                        Files.move(backup(i - 1), backup(i), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Files.move(file, backup(1), StandardCopyOption.REPLACE_EXISTING);
            }
            open(false);
        } catch (IOException e) {
            maxSize = 0;
            printDiagnostic("Cannot roll over the log file " + file.toAbsolutePath() + ": " + e + "; it keeps growing");
            open(true);
        }
    }

    private Path backup(int number) {
        return file.resolveSibling(file.getFileName() + "." + number);
    }

    /**
     * Writes what the buffer holds to the file; {@link LOG#flush()} calls it, and so does the exit of the JVM in the
     * {@code buffer} mode. A failure is thrown as an {@code UncheckedIOException}, which {@link LOG#flush()} reports.
     */
    @Override
    public synchronized void flush() {
        if (out != null) {
            try {
                out.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Writes the buffer and closes the file; a later entry opens it again and appends to it.
     */
    public synchronized void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            // nothing more to write
        }
        out = null;
    }
}
