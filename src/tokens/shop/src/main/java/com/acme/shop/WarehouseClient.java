package com.acme.shop;

import com.nanolaba.logging.LOG;

/**
 * Scenario c: polls a warehouse that answers 503 for a while and logs the same warning on every retry.
 */
public class WarehouseClient {

    private final int unavailableFor;

    public WarehouseClient(int unavailableFor) {
        this.unavailableFor = unavailableFor;
    }

    public String stockOf(String sku) {
        for (int attempt = 1; ; attempt++) {
            if (attempt > unavailableFor) {
                LOG.info("Warehouse answered after {} attempts", attempt);
                return sku + ": 12";
            }
            LOG.warn("Warehouse returned 503 Service Unavailable, retrying in 100 ms");
        }
    }
}
