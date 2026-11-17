package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Print debugging with {@code LOG.dbg}. The region is imported into the README, which shows the output of
 * {@link #main}.
 */
public class PrintDebugging {

    interface Order {
        BigDecimal total();
    }

    BigDecimal checkout(Order order, String user, List<String> cart) {
        // nrg:begin:dbg
        BigDecimal total = LOG.dbg(order.total());   // prints, then returns the value
        LOG.dbg(user, cart.size());
        LOG.dbg();                                   // prints "reached"
        // nrg:end:dbg
        return total;
    }

    public static void main(String[] args) {
        new PrintDebugging().checkout(() -> new BigDecimal("129.90"), "alice", Arrays.asList("book", "pen", "lamp"));
    }
}
