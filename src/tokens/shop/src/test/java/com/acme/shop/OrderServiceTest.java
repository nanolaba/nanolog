package com.acme.shop;

import com.nanolaba.logging.LOG;
import org.junit.jupiter.api.Test;

/**
 * Scenario a: a test that logs 200 orders and fails on order 150.
 */
class OrderServiceTest {

    @Test
    void processesTheDailyBatch() {
        OrderService service = new OrderService(150);
        try {
            for (int order = 1; order <= 200; order++) {
                service.process(order);
            }
        } catch (RuntimeException e) {
            LOG.error("The batch failed", e);
            throw e;
        }
    }
}
