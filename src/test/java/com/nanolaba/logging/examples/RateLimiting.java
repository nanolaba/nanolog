package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;

import java.util.concurrent.TimeUnit;

/**
 * Rate-limited statements. The region is imported into the README.
 */
public class RateLimiting {

    void handle(String key, Object item) {
        // nrg:begin:limits
        LOG.once().warn("Option {} is deprecated and ignored", key);
        LOG.atMostEvery(10, TimeUnit.SECONDS).warn("Queue is full, dropping {}", item);
        // the next line that gets through says how many were skipped:
        // WARN 16.11.2026 10:15:02 [Worker] Queue is full, dropping 17 [skipped 241]
        // nrg:end:limits
    }
}
