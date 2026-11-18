package com.nanolaba.logging;

/**
 * A thread whose whole stack belongs to Nanolog: it is a top-level {@code ILogger}, and its {@code run} is the bottom
 * frame. A locator asked from it finds no caller.
 */
final class LoggerThread extends Thread implements ILogger {

    private final CallerDetection.Locator locator;
    volatile CallerDetection.Caller found;

    LoggerThread(CallerDetection.Locator locator) {
        this.locator = locator;
    }

    @Override
    public void run() {
        found = locator.find();
    }

    @Override
    public void log(LogEntry entry) {
        // never called
    }
}
