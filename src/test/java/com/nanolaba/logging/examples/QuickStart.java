package com.nanolaba.logging.examples;

import com.nanolaba.logging.ConsoleLogger;
import com.nanolaba.logging.LOG;
import com.nanolaba.logging.Slf4jLogger;

public class QuickStart {
    public static void main(String[] args) {
        try {
            // This is the logger used by default
            LOG.init(new ConsoleLogger());

            // If you want to use SLF4J
            LOG.init(new Slf4jLogger());

            // If you want to write your own logger, use a lambda function or implement the ILogger interface.
            LOG.init(entry -> System.err.println(entry.getLevel() + " - " + entry.getFormattedMessage()));

            LOG.debug("A static logger variable is not needed");
            LOG.info(String.class, "But you can explicitly specify which class the logging should belong to");
            LOG.warn("This is a parameterized message: {}, {}, {} ",
                    100, "foo", new Object[]{"foo", "bar"});

            if (LOG.isDebugEnabled()) {
                LOG.debug("You can check if a log level is enabled in the standard way: " +
                        hugeComputations());
            }
            LOG.debug(() -> "Alternatively, you can supply a lambda: " + hugeComputations());

        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private static String hugeComputations() {
        return "OK";
    }
}