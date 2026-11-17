package com.nanolaba.logging.examples;

import com.nanolaba.logging.NanologChecks;
import org.junit.jupiter.api.Test;

/**
 * Failing the build on {@code LOG.dbg} calls left in the sources. The region is imported into the README. Unlike the
 * other examples this one is a real test: it keeps Nanolog's own sources free of {@code LOG.dbg}.
 */
class NoDbgCallsLeftTest {

    // nrg:begin:test
    @Test
    void noDebugPrintsLeft() {
        NanologChecks.assertNoDbgCalls("src/main/java"); // lists the file, line and code of every call
    }
    // nrg:end:test
}
