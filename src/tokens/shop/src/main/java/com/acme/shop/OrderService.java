package com.acme.shop;

import com.nanolaba.logging.LOG;

import java.sql.SQLException;

/**
 * Scenario a: processes orders and logs three lines for each; the database deadlocks on one of them.
 */
public class OrderService {

    private final int deadlockOn;

    public OrderService(int deadlockOn) {
        this.deadlockOn = deadlockOn;
    }

    public void process(int order) {
        LOG.info("Order {} received: {} items", order, 1 + order % 5);
        reserve(order);
        LOG.debug("Order {} reserved", order);
    }

    private void reserve(int order) {
        LOG.debug("Reserving stock for order {}", order);
        if (order == deadlockOn) {
            LOG.warn("Order {}: stock of SKU A-7 below the threshold", order);
        }
        try {
            update(order);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot reserve stock for order " + order, e);
        }
    }

    private void update(int order) throws SQLException {
        if (order == deadlockOn) {
            throw new SQLException("Deadlock found when trying to get lock; order " + order);
        }
    }
}
