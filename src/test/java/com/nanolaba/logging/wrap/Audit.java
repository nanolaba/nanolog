package com.nanolaba.logging.wrap;

import com.nanolaba.logging.LOG;

/**
 * A logging helper of an application, the kind {@code nanolog.caller.skip} names.
 */
public final class Audit {

    private Audit() {/**/}

    public static void record(String message, Object... args) {
        LOG.info(message, args);
    }

    public static void recordOnce(String message) {
        LOG.once().info(message);
    }

    public static void recordInner(String message) {
        Inner.record(message);
    }

    public static <T> T dbg(T value) {
        return LOG.dbg(value);
    }

    public static void dbgOnce(Object value) {
        LOG.once().dbg(value);
    }

    /**
     * A task that logs, for the caller to run through {@code LOG.catching}.
     */
    public static LOG.ThrowingRunnable task(String message) {
        return () -> LOG.info(message);
    }

    public static final class Inner {

        private Inner() {/**/}

        public static void record(String message) {
            LOG.info(message);
        }
    }
}
