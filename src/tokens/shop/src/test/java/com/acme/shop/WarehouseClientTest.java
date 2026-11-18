package com.acme.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Scenario c: a green test whose retry loop logs the same warning 300 times.
 */
class WarehouseClientTest {

    @Test
    void waitsForTheWarehouse() {
        assertEquals("A-7: 12", new WarehouseClient(300).stockOf("A-7"));
    }
}
