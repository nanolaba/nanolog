package com.nanolaba.logging;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts the stack walks of caller detection by wrapping a locator. It implements {@link ILogger} only so that caller
 * detection skips its frames, as it skips the frames of every logger; otherwise the wrapper would be found as the
 * caller.
 */
class CountingLocator implements CallerDetection.Locator, ILogger {

    final AtomicInteger walks = new AtomicInteger();
    private final CallerDetection.Locator delegate;

    CountingLocator(CallerDetection.Locator delegate) {
        this.delegate = delegate;
    }

    @Override
    public CallerDetection.Caller find() {
        walks.incrementAndGet();
        return delegate.find();
    }

    @Override
    public void log(LogEntry entry) {
        throw new UnsupportedOperationException("not a logger");
    }
}
