package com.nanolaba.logging.examples;

// nrg:begin:tryit
import com.nanolaba.logging.LOG;

import java.math.BigDecimal;

public class TryIt {

    public static void main(String[] args) {
        BigDecimal price = new BigDecimal("43.30");
        int quantity = 3;
        LOG.info("Checkout of {} items", quantity);
        LOG.dbg(price.multiply(BigDecimal.valueOf(quantity)));
    }
}
// nrg:end:tryit
