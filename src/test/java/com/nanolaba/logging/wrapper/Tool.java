package com.nanolaba.logging.wrapper;

import com.nanolaba.logging.LOG;

/**
 * A class in a package whose name starts like {@code com.nanolaba.logging.wrap}, which must not cover it.
 */
public final class Tool {

    private Tool() {/**/}

    public static void record(String message) {
        LOG.info(message);
    }
}
