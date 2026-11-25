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

        // The context adds requestId=r-17 to every entry in the block;
        // the unit writes one line when it ends: outcome, duration, fields, warnings
        try (LOG.Context context = LOG.context("requestId", "r-17");
             LOG.Unit checkout = LOG.unit("checkout")) {
            checkout.put("orderId", "A-17");
            LOG.info("Payment accepted", LOG.kv("method", "card")); // a named field
            LOG.warn("Disk space is low");
        }

        // A timer that returns the value of the lambda
        String report = LOG.timed("Build report", () -> buildReport());

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
