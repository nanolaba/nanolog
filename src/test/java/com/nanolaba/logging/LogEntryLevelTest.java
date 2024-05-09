package com.nanolaba.logging;

import com.nanolaba.logging.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogEntryLevelTest {

    @BeforeAll
    public static void init() {
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {

            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class clz) {

                return (level == TRACE && clz == TraceObject.class ||
                        level == DEBUG && clz == DebugObject.class ||
                        level == ERROR && clz == ErrorObject.class) ||
                       level == WARN && clz == WarnObject.class ||
                       level == INFO && clz == InfoObject.class;
            }
        });
    }

    @Test
    public void testTrace() {
        assertFalse(LOG.isTraceEnabled());
        assertTrue(LOG.isTraceEnabled(TraceObject.class));
        assertFalse(LOG.isTraceEnabled(DebugObject.class));
        assertFalse(LOG.isTraceEnabled(InfoObject.class));
        assertFalse(LOG.isTraceEnabled(WarnObject.class));
        assertFalse(LOG.isTraceEnabled(ErrorObject.class));
    }

    @Test
    public void testDebug() {
        assertFalse(LOG.isDebugEnabled());
        assertFalse(LOG.isDebugEnabled(TraceObject.class));
        assertTrue(LOG.isDebugEnabled(DebugObject.class));
        assertFalse(LOG.isDebugEnabled(InfoObject.class));
        assertFalse(LOG.isDebugEnabled(WarnObject.class));
        assertFalse(LOG.isDebugEnabled(ErrorObject.class));
    }

    @Test
    public void testInfo() {
        assertFalse(LOG.isInfoEnabled());
        assertFalse(LOG.isInfoEnabled(TraceObject.class));
        assertFalse(LOG.isInfoEnabled(DebugObject.class));
        assertTrue(LOG.isInfoEnabled(InfoObject.class));
        assertFalse(LOG.isInfoEnabled(WarnObject.class));
        assertFalse(LOG.isInfoEnabled(ErrorObject.class));
    }

    @Test
    public void testWarn() {
        assertFalse(LOG.isWarnEnabled());
        assertFalse(LOG.isWarnEnabled(TraceObject.class));
        assertFalse(LOG.isWarnEnabled(DebugObject.class));
        assertFalse(LOG.isWarnEnabled(InfoObject.class));
        assertTrue(LOG.isWarnEnabled(WarnObject.class));
        assertFalse(LOG.isWarnEnabled(ErrorObject.class));
    }

    @Test
    public void testError() {
        assertFalse(LOG.isErrorEnabled());
        assertFalse(LOG.isErrorEnabled(TraceObject.class));
        assertFalse(LOG.isErrorEnabled(DebugObject.class));
        assertFalse(LOG.isErrorEnabled(InfoObject.class));
        assertFalse(LOG.isErrorEnabled(WarnObject.class));
        assertTrue(LOG.isErrorEnabled(ErrorObject.class));
    }

}
