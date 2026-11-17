package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Print debugging with {@code LOG.dbg}. The regions are imported into the README, which shows the output of
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

    // nrg:begin:dump
    // no toString() in any of them
    static class Customer {
        String name = "alice";
        String password = "s3cr3t";
    }

    static class Line {
        String sku;
        int qty;

        Line(String sku, int qty) {
            this.sku = sku;
            this.qty = qty;
        }
    }

    static class Invoice {
        long id = 17;
        List<Line> lines = Arrays.asList(new Line("A-1", 2), new Line("B-7", 1));
        Customer customer = new Customer();
    }

    void inspect(Invoice invoice) {
        LOG.dbg(invoice);
    }
    // nrg:end:dump

    public static void main(String[] args) {
        PrintDebugging example = new PrintDebugging();
        example.checkout(() -> new BigDecimal("129.90"), "alice", Arrays.asList("book", "pen", "lamp"));
        example.inspect(new Invoice());
    }
}
