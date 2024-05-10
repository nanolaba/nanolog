package com.nanolaba.logging;

public class DefaultLoggerCreator {

    public static final String LOGGER_CLASS_PROPERTY = "nanolog.logger";

    public static ILogger createDefaultLogger() {

        String property = System.getProperty(LOGGER_CLASS_PROPERTY);
        if (property != null && !property.isEmpty()) {
            try {
                Class<?> loggerClass = Class.forName(property);
                return (ILogger) loggerClass.getConstructor().newInstance();
            } catch (Throwable e) {
                System.err.printf("[NANOLOG] Property \"%s\" has been defined, but an object of class \"%s\" cannot be created %n", LOGGER_CLASS_PROPERTY, property);
                e.printStackTrace(System.err);
            }
        }

        return new ConsoleLogger();
    }
}
