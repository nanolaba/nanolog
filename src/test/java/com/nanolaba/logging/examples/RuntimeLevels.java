package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;
import com.nanolaba.logging.LogEntry.LogEntryLevel;

/**
 * Changing levels while the application runs. The region is imported into the README.
 */
public class RuntimeLevels {

    public static void main(String[] args) {
        // nrg:begin:runtime
        LOG.setLevel(LogEntryLevel.INFO);                         // the global level
        LOG.setLevel("com.acme.payments", LogEntryLevel.DEBUG);   // one package or class, like nanolog.level.<name>
        LOG.clearLevel("com.acme.payments");                      // the global level applies to it again
        LOG.resetLevels();                                        // back to -D, the environment and nanolog.properties

        try (LOG.LevelOverride ignored = LOG.withLevel("com.acme", LogEntryLevel.TRACE)) {
            // TRACE for com.acme until the block ends
        }
        // nrg:end:runtime
    }
}
