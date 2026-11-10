package com.nanolaba.logging.examples;

import com.nanolaba.logging.ConsoleLogger;
import com.nanolaba.logging.LOG;
import com.nanolaba.logging.Slf4jLogger;

import java.text.SimpleDateFormat;

/**
 * The three ways to pick a logger programmatically. The regions are imported into the README.
 */
public class LoggerSetup {

    public static void main(String[] args) {
        // nrg:begin:console
        LOG.init(new ConsoleLogger()
                .setShowLevel(true)
                .setShowDate(true)
                .setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"))
                .setShowSource(true)
                .setShowSourceFullName(false)
                .setShowLocation(false)
                .setTraceEnabled(false)
                .setDebugEnabled(false));
        // nrg:end:console

        // nrg:begin:slf4j
        LOG.init(new Slf4jLogger());
        // nrg:end:slf4j

        // nrg:begin:lambda
        LOG.init(entry -> System.out.println(
                entry.getLevel() + " [" + entry.getSourceClass().getSimpleName() + "] " + entry.getFormattedMessage()));
        // nrg:end:lambda
    }
}
