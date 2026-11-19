package org.example.vendor;

import com.nanolaba.logging.ILogger;
import com.nanolaba.logging.LOG;

/**
 * A container of another organization that installs the logger of the application through reflection, as Spring's
 * {@code MethodInvokingBean} does with {@code staticMethod="com.nanolaba.logging.LOG.init"}.
 */
public final class Container {

    private Container() {/**/}

    /**
     * @return the call site of the reflective call, as a frame prints it
     */
    public static String install(ILogger logger) throws ReflectiveOperationException {
        LOG.class.getMethod("init", ILogger.class).invoke(null, logger);
        StackTraceElement caller = new Throwable().getStackTrace()[0];
        return new StackTraceElement(caller.getClassName(), caller.getMethodName(), caller.getFileName(), caller.getLineNumber() - 1).toString();
    }
}
