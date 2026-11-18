package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;

import java.io.Closeable;

/**
 * Logging an exception and carrying on, and the exceptions that kill threads. The region is imported into the README.
 */
public class Catching {

    void work(Closeable connection, String text) {
        // nrg:begin:catching
        LOG.installUncaughtExceptionHandler(); // once, at startup: exceptions that kill threads go into the log

        LOG.catching(() -> connection.close()); // best-effort cleanup: ERROR with the stack trace, then carry on
        // ERROR 16.11.2026 10:15:02 [Catching] Caught and ignored
        // java.io.IOException: Connection reset ...

        int retries = LOG.catching(() -> Integer.parseInt(text), 3); // a default value when it fails
        // ERROR 16.11.2026 10:15:02 [Catching] Caught, returning 3
        // java.lang.NumberFormatException: For input string: "many" ...
        // nrg:end:catching
        LOG.debug("{} retries", retries);
    }
}
