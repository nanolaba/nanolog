package com.acme.shop;

import com.nanolaba.logging.LOG;

/**
 * Scenario b: imports customers and logs every one of them; nothing goes wrong.
 */
public class ImportJob {

    public int run(int customers) {
        LOG.info("Import of {} customers started", customers);
        int imported = 0;
        for (int id = 1; id <= customers; id++) {
            LOG.debug("Normalized the address of customer {}", id);
            imported++;
            LOG.info("Imported customer {} ({} of {})", id, imported, customers);
        }
        LOG.info("Import finished: {} customers", imported);
        return imported;
    }
}
