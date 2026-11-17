package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;

import java.util.List;

/**
 * Units of work: one line per request or job. The region is imported into the README.
 */
public class WideEvents {

    void checkout(String orderId, List<String> items, String file) {
        // nrg:begin:unit
        try (LOG.Unit u = LOG.unit("checkout")) {
            u.put("orderId", orderId).put("items", items.size());
            charge(orderId); // logs as usual; its warnings and errors are counted
        }
        // INFO 16.11.2026 10:15:02 [CheckoutService] checkout ok 182 ms orderId=A-17 items=3 warn=1

        LOG.unit("import", () -> importFile(file)); // an exception fails the unit and is thrown on
        // ERROR 16.11.2026 10:15:03 [CheckoutService] import error 41 ms error.type=java.io.UncheckedIOException ...
        // nrg:end:unit
    }

    private void charge(String orderId) {
        LOG.warn("Retrying the charge of {}", orderId);
    }

    private void importFile(String file) {
        LOG.info("Importing {}", file);
    }
}
