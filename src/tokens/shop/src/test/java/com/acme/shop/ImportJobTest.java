package com.acme.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Scenario b: a green test of a job that logs at INFO and DEBUG for every record.
 */
class ImportJobTest {

    @Test
    void importsEveryCustomer() {
        assertEquals(300, new ImportJob().run(300));
    }
}
