package com.nanolaba.logging;

import java.lang.reflect.Method;

/**
 * What the loggers of Nanolog need to know about their own subclasses. Kept apart from {@link LOG}, so that creating a
 * logger does not initialise {@code LOG}.
 */
final class LoggerIntrospection {

    private LoggerIntrospection() {/**/}

    /**
     * Whether the class of a logger overrides {@code isEnabled} below {@code base}. {@link ConsoleLogger} and
     * {@link CompositeLogger} derive {@code mayBeEnabled} from their own {@code isEnabled}, so a subclass that changes
     * {@code isEnabled} without overriding {@code mayBeEnabled} must get the default {@code true}. Also {@code true}
     * when the method cannot be looked up, as in a native image without reflection metadata for the class.
     */
    static boolean overridesIsEnabled(ILogger logger, Class<?> base) {
        Method method = isEnabledMethod(logger.getClass());
        return method == null || method.getDeclaringClass() != base;
    }

    private static Method isEnabledMethod(Class<?> loggerClass) {
        try {
            return loggerClass.getMethod("isEnabled", LogEntry.LogEntryLevel.class, Class.class);
        } catch (Throwable t) {
            // NoSuchMethodException cannot happen on a JVM, but a native image may lack the metadata
            return null;
        }
    }
}
