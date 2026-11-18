package com.nanolaba.logging;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * The capture of {@code System.out} and {@code System.err} by {@link LOG#captureSystemStreams()}: each line printed to
 * them becomes an entry, {@code INFO} for {@code System.out} and {@code ERROR} for {@code System.err}, whose source is
 * the code that printed it.
 * <p>
 * No loop: what Nanolog prints itself goes to the streams kept when the capture started ({@link #out()},
 * {@link #err()}), and anything a logger prints to the captured streams while an entry is delivered passes straight
 * through to them, in the thread that logs.
 */
final class SystemStreams {

    /**
     * The streams kept when the capture started; {@code null} while nothing is captured.
     */
    private static volatile PrintStream originalOut;
    private static volatile PrintStream originalErr;

    /**
     * The streams installed by the capture, to restore only what is still ours.
     */
    private static PrintStream capturingOut;
    private static PrintStream capturingErr;

    /**
     * Set while the thread delivers an entry or turns a line into one: what it prints passes through.
     */
    private static final ThreadLocal<Boolean> LOGGING = new ThreadLocal<>();

    /**
     * Frames of the printing machinery, which are not the code that printed.
     */
    private static final String[] MACHINERY = {"java.io.", "sun.nio.cs.", "jdk.internal.", "java.util.Formatter", "java.lang.Throwable",
                                                SystemStreams.class.getName() + "$"};

    private SystemStreams() {/**/}

    /**
     * Where Nanolog prints its own standard output: the stream kept by the capture, or {@code System.out}, read on
     * every call.
     */
    static PrintStream out() {
        PrintStream original = originalOut;
        return original != null ? original : System.out;
    }

    /**
     * Where Nanolog prints its own errors and diagnostics: the stream kept by the capture, or {@code System.err}.
     */
    static PrintStream err() {
        PrintStream original = originalErr;
        return original != null ? original : System.err;
    }

    static synchronized void capture() {
        if (originalOut == null) {
            PrintStream out = System.out;
            PrintStream err = System.err;
            capturingOut = capturing(new Lines(LogEntry.LogEntryLevel.INFO, SystemOut.class, out));
            capturingErr = capturing(new Lines(LogEntry.LogEntryLevel.ERROR, SystemErr.class, err));
            originalOut = out;
            originalErr = err;
            System.setOut(capturingOut);
            System.setErr(capturingErr);
        }
    }

    /**
     * A print stream over the lines of a captured stream: UTF-8, so a printed text reaches the entry whole whatever
     * the charset of the platform, and no autoFlush, which would flush after every write and turn a partial line into
     * an entry.
     */
    private static PrintStream capturing(Lines lines) {
        try {
            return new PrintStream(lines, false, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Puts the kept streams back, each only if the capturing one is still installed. Restoring again changes nothing.
     */
    static synchronized void restore() {
        if (originalOut != null) {
            if (System.out == capturingOut) {
                System.setOut(originalOut);
            }
            if (System.err == capturingErr) {
                System.setErr(originalErr);
            }
            originalOut = null;
            originalErr = null;
        }
    }

    /**
     * Marks the thread as delivering an entry, while something is captured, so what a logger prints passes through.
     *
     * @return {@code true} if this call marked it, and {@link #leaveLogging()} must follow
     */
    static boolean enterLogging() {
        if (originalOut == null || LOGGING.get() != null) {
            return false;
        }
        LOGGING.set(Boolean.TRUE);
        return true;
    }

    static void leaveLogging() {
        LOGGING.remove();
    }

    /**
     * One captured stream: bytes are collected per thread until a line ends, at {@code \n}, with a {@code \r} before
     * it dropped, or until {@code flush()}; an empty line is no entry.
     */
    private static final class Lines extends OutputStream {

        private final LogEntry.LogEntryLevel level;
        private final Class stream;
        private final PrintStream original;
        private final ThreadLocal<ByteArrayOutputStream> pending = ThreadLocal.withInitial(ByteArrayOutputStream::new);

        Lines(LogEntry.LogEntryLevel level, Class stream, PrintStream original) {
            this.level = level;
            this.stream = stream;
            this.original = original;
        }

        @Override
        public void write(int b) {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            if (LOGGING.get() != null) {
                original.write(bytes, offset, length);
                return;
            }
            ByteArrayOutputStream line = pending.get();
            for (int i = offset; i < offset + length; i++) {
                if (bytes[i] == '\n') {
                    emit(line);
                } else {
                    line.write(bytes[i]);
                }
            }
        }

        @Override
        public void flush() {
            if (LOGGING.get() == null) {
                emit(pending.get());
            }
            original.flush();
        }

        private void emit(ByteArrayOutputStream line) {
            byte[] bytes = line.toByteArray();
            line.reset();
            int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
            if (length > 0) {
                LOGGING.set(Boolean.TRUE);
                try {
                    CallerDetection.Caller printer = printer();
                    LOG.writeLine(level, printer.sourceClass, printer.frame, new String(bytes, 0, length, StandardCharsets.UTF_8));
                } catch (Throwable t) {
                    // logging does not throw; should it, the line is lost, not the stream
                }
                LOGGING.remove();
            }
        }

        /**
         * The code that printed: the first frame outside the printing machinery whose class can be loaded; without
         * one, the stream itself, {@link SystemOut} or {@link SystemErr}, and no call site.
         */
        private CallerDetection.Caller printer() {
            for (StackTraceElement frame : CallerDetection.ThrowableWalk.stackTraceOf.apply(new Throwable())) {
                if (!isMachinery(frame.getClassName())) {
                    Class frameClass = CallerDetection.ThrowableWalk.resolveClass(frame.getClassName());
                    if (frameClass != null) {
                        return new CallerDetection.Caller(CallerDetection.topLevel(frameClass), frame);
                    }
                }
            }
            return new CallerDetection.Caller(stream, null);
        }

        private static boolean isMachinery(String className) {
            for (String prefix : MACHINERY) {
                if (className.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }
}
