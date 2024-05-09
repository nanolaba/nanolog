package com.nanolaba.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SimpleConsoleLoggerTest {

    @BeforeEach
    public void init() {
        LOG.init(new SimpleConsoleLogger());
    }

    @Test
    public void testLog() {
        SimpleConsoleLogger logger = new SimpleConsoleLogger();

        for (LogEntry.LogEntryLevel level : LogEntry.LogEntryLevel.values()) {
            logger.log(new LogEntry(level, this::getClass, new Throwable("test throwable"), () -> "some message"));
        }
    }

    @Test
    public void testArgsLog() {
        SimpleConsoleLogger logger = new SimpleConsoleLogger();

        for (LogEntry.LogEntryLevel level : LogEntry.LogEntryLevel.values()) {
            logger.log(new LogEntry(level, this::getClass, new Throwable("test throwable"), () -> "some message {} next {}", "abc", "123"));
        }
    }

}