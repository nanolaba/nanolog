package com.nanolaba.logging.util;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A context provider for {@code nanolog.context.provider}: a public class with a public no-argument constructor.
 */
public class TraceIdProvider implements Supplier<Map<String, ?>> {

    @Override
    public Map<String, ?> get() {
        return Collections.singletonMap("traceId", "configured");
    }
}
