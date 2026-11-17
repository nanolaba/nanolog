package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;

import java.util.concurrent.ExecutorService;

/**
 * The context of a request. The region is imported into the README.
 */
public class RequestContext {

    void charge(String requestId, int amount, ExecutorService executor) {
        // nrg:begin:context
        try (LOG.Context c = LOG.context("requestId", requestId)) {
            LOG.info("Charging {}", amount);
            // INFO 16.11.2026 10:15:02 [PaymentService] Charging 42 requestId=r-17

            executor.submit(LOG.wrap(() -> LOG.info("Sending the receipt")));
            // a thread of the pool logs it with requestId=r-17 as well
        }
        // nrg:end:context
    }
}
