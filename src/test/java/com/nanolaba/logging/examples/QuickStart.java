package com.nanolaba.logging.examples;

// nrg:begin:quickstart
import com.nanolaba.logging.LOG;

public class QuickStart {

    public static void main(String[] args) {
        // No logger field: the source class is detected automatically
        LOG.info("Application started");

        // SLF4J-style {} placeholders
        LOG.debug("Loaded {} items in {} ms", 42, 17);

        // Lazy message: the lambda runs only if DEBUG is enabled
        LOG.debug(() -> "Expensive report: " + buildReport());

        // The source class can also be passed explicitly
        LOG.warn(QuickStart.class, "Disk space is low");

        String input = "not a number";
        try {
            Integer.parseInt(input);
        } catch (NumberFormatException e) {
            // Exception plus a parameterized message; the stack trace follows the message
            LOG.error(e, "Failed to parse \"{}\"", input);
        }
    }

    private static String buildReport() {
        return "...";
    }
}
// nrg:end:quickstart
