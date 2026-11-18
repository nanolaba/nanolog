package com.acme.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Scenario d: a test that fails with an exception from deep inside a stream pipeline.
 */
class ReportServiceTest {

    @Test
    void buildsTheMarchReport() {
        assertEquals(4, new ReportService().monthly("2026-03").size());
    }
}
