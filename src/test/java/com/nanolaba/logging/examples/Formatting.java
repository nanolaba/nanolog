package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;

import java.io.IOException;

/**
 * Message formatting rules. Each line is followed by the message that reaches the logger.
 */
public class Formatting {

    public static void main(String[] args) {
        // nrg:begin:formatting
        LOG.info("User {} logged in from {}", "alice", "10.0.0.1");  // User alice logged in from 10.0.0.1
        LOG.info("Missing arguments stay as is: {} {}", 1);          // Missing arguments stay as is: 1 {}
        LOG.info("Extra arguments are ignored: {}", 1, 2);           // Extra arguments are ignored: 1
        LOG.info("Escaped placeholder: \\{} then {}", 1);           // Escaped placeholder: {} then 1
        LOG.info("Escaped backslash: \\\\{}", 1);                  // Escaped backslash: \1
        LOG.info("Arrays: {} and {}", new int[]{1, 2}, new String[][]{{"a"}, {"b"}}); // Arrays: [1, 2] and [[a], [b]]
        LOG.info("Null is fine: {}", (Object) null);                 // Null is fine: null
        LOG.info("Broken toString(): {}", new BrokenToString());     // Broken toString(): [FAILED toString()]
        LOG.error("Failed to load {}", "order", new IOException("timeout")); // Failed to load order, then the stack trace
        LOG.error("Cause: {}", new IOException("timeout"));          // Cause: java.io.IOException: timeout
        // nrg:end:formatting
    }

    private static class BrokenToString {
        @Override
        public String toString() {
            throw new IllegalStateException("boom");
        }
    }
}
