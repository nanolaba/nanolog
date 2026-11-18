package com.acme.shop;

import com.nanolaba.logging.LOG;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scenario d: builds a report through streams; one price has an unknown currency, so the exception comes up through
 * the frames of the stream pipeline, then the test runner's.
 */
public class ReportService {

    public List<BigDecimal> monthly(String month) {
        LOG.info("Building the report for {}", month);
        try {
            return Arrays.asList("12.50 EUR", "7.00 EUR", "3.10 XTS", "9.99 EUR").stream()
                    .map(String::trim)
                    .map(this::toEuro)
                    .filter(amount -> amount.signum() > 0)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            LOG.error("The report for {} failed", month, e);
            throw new IllegalStateException("Report for " + month + " failed", e);
        }
    }

    private BigDecimal toEuro(String price) {
        String[] parts = price.split(" ");
        if (!"EUR".equals(parts[1])) {
            throw new IllegalArgumentException("No rate for currency " + parts[1] + " in price \"" + price + "\"");
        }
        return new BigDecimal(parts[0]);
    }
}
