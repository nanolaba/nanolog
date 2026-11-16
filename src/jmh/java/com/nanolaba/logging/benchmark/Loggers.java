package com.nanolaba.logging.benchmark;

import com.nanolaba.logging.ConsoleLogger;
import com.nanolaba.logging.ILogger;
import com.nanolaba.logging.LogEntry;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * The loggers the benchmarks install, built with the 1.2 API only.
 */
final class Loggers {

    private Loggers() {
    }

    /**
     * A {@code ConsoleLogger} at {@code INFO} with a {@code DEBUG} rule for one package, as
     * {@code -Dnanolog.level.<name>=DEBUG} sets it.
     */
    static ConsoleLogger debugOnlyFor(String packageName) {
        String property = "nanolog.level." + packageName;
        System.setProperty(property, "DEBUG");
        try {
            return new ConsoleLogger().setTraceEnabled(false).setDebugEnabled(false);
        } finally {
            System.clearProperty(property);
        }
    }

    /**
     * Accepts every entry and keeps the last one, so the JIT cannot drop the entry as unused.
     */
    static final class Keeping implements ILogger {

        volatile LogEntry last;

        @Override
        public void log(LogEntry entry) {
            last = entry;
        }
    }

    /**
     * A {@code ConsoleLogger} that formats every entry as usual and writes it to a stream that discards it.
     */
    static ConsoleLogger consoleToNowhere() {
        PrintStream nowhere = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public void write(byte[] b, int off, int len) {
            }
        });
        return new ConsoleLogger() {
            @Override
            protected PrintStream getOutputStream(LogEntry entry) {
                return nowhere;
            }
        };
    }
}
