package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;

public class QuickStart {

    // nrg:begin:quickstart
    public static void main(String[] args) {
        // No logger field: the source class is detected automatically
        LOG.info("Application started");

        // SLF4J-style {} placeholders
        LOG.debug("Loaded {} items in {} ms", 42, 17);

        // Lazy message: the lambda runs only if DEBUG is enabled
        LOG.debug(() -> "Expensive report: " + buildReport());

        // The source class can also be passed explicitly
        LOG.warn(QuickStart.class, "Disk space is low");

        try {
            Integer.parseInt("not a number");
        } catch (NumberFormatException e) {
            // The stack trace is printed after the message
            LOG.error(e, "Failed to parse the input");
        }
    }
    // nrg:end:quickstart

    private static String buildReport() {
        return "...";
    }
}
