package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;

/**
 * Named arguments, the fields of an entry. The region is imported into the README.
 */
public class Fields {

    void load(int count, String source, String orderId, Exception e) {
        // nrg:begin:fields
        LOG.info("Loaded {} items from {}", LOG.kv("count", count), LOG.kv("source", source));
        // INFO 16.11.2026 10:15:02 [Importer] Loaded 3 items from db
        // JSON: "message":"Loaded 3 items from db",...,"fields":{"count":3,"source":"db"}

        LOG.info("Payment accepted", LOG.kv("orderId", orderId));
        // INFO 16.11.2026 10:15:02 [PaymentService] Payment accepted orderId=A-17
        // JSON: "message":"Payment accepted","fields":{"orderId":"A-17"}

        LOG.error("Payment failed", LOG.kv("orderId", orderId), e); // the exception goes last
        // nrg:end:fields
    }
}
