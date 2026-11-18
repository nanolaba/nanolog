package com.nanolaba.logging;

/**
 * The handler of {@link LOG#installUncaughtExceptionHandler()}: logs the exception that kills a thread at
 * {@code ERROR}, in the format of the installed logger, then passes it to the handler installed before.
 */
final class Uncaught implements Thread.UncaughtExceptionHandler {

    /**
     * The prefixes of the classes of the JDK, whose frames are not where an exception comes from.
     */
    private static final String[] JDK = {"java.", "javax.", "jdk.", "sun.", "com.sun."};

    private final Thread.UncaughtExceptionHandler previous;

    /**
     * @param previous the default handler installed before; {@code null} if there was none
     */
    Uncaught(Thread.UncaughtExceptionHandler previous) {
        this.previous = previous;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        try {
            LOG.writeUncaught(thread, error);
        } catch (Throwable t) {
            // the handler must not fail: the previous one still gets the exception
        }
        if (previous != null) {
            previous.uncaughtException(thread, error);
        }
    }

    /**
     * Where an exception comes from: the first frame of its stack trace outside the JDK, or its top frame if every
     * frame is of the JDK, with the top-level class of that frame. A class that cannot be loaded gives no class; no
     * frame gives neither.
     */
    static CallerDetection.Caller origin(Throwable error) {
        StackTraceElement[] trace;
        try {
            trace = error.getStackTrace();
        } catch (Throwable t) {
            return CallerDetection.Caller.NONE;
        }
        if (trace == null || trace.length == 0) {
            return CallerDetection.Caller.NONE;
        }
        StackTraceElement frame = trace[0];
        for (StackTraceElement element : trace) {
            if (!isJdk(element.getClassName())) {
                frame = element;
                break;
            }
        }
        Class frameClass = CallerDetection.ThrowableWalk.resolveClass(frame.getClassName());
        return new CallerDetection.Caller(frameClass == null ? null : CallerDetection.topLevel(frameClass), frame);
    }

    private static boolean isJdk(String className) {
        for (String prefix : JDK) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
