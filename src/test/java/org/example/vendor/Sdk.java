package org.example.vendor;

import com.nanolaba.logging.ILogger;
import com.nanolaba.logging.LOG;

/**
 * A library of another organization that calls {@code LOG.init}, which it should not.
 */
public final class Sdk {

    private Sdk() {/**/}

    /**
     * @return the call site of the {@code LOG.init} call, as a frame prints it
     */
    public static String install(ILogger logger) {
        LOG.init(logger);
        return previousLine();
    }

    /**
     * @return the call site of the {@code LOG.init} call, as a frame prints it
     */
    public static String reset() {
        LOG.init();
        return previousLine();
    }

    private static String previousLine() {
        StackTraceElement caller = new Throwable().getStackTrace()[1];
        return new StackTraceElement(caller.getClassName(), caller.getMethodName(), caller.getFileName(), caller.getLineNumber() - 1).toString();
    }
}
