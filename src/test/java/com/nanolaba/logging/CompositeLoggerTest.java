package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeLoggerTest {

    @Test
    public void testEveryDelegateReceivesOnlyWhatItHasEnabled() {
        RecordingLogger all = new RecordingLogger(true);
        RecordingLogger infoOnly = new RecordingLogger(false);
        LOG.init(new CompositeLogger(all, infoOnly));

        LOG.info(InfoObject.class, "info");
        LOG.debug(InfoObject.class, "debug");

        assertEquals(Arrays.asList("info", "debug"), all.messages);
        assertEquals(Collections.singletonList("info"), infoOnly.messages);
    }

    @Test
    public void testEnabledIfAnyDelegateIsEnabled() {
        CompositeLogger composite = new CompositeLogger(new RecordingLogger(false), new RecordingLogger(false));
        assertTrue(composite.isEnabled(INFO, InfoObject.class));
        assertFalse(composite.isEnabled(DEBUG, InfoObject.class));

        assertTrue(new CompositeLogger(new RecordingLogger(false), new RecordingLogger(true)).isEnabled(DEBUG, InfoObject.class));
        assertFalse(new CompositeLogger().isEnabled(INFO, InfoObject.class), "no delegates, nothing to log");
    }

    @Test
    public void testDisabledEverywhereSkipsTheMessage() {
        RecordingLogger infoOnly = new RecordingLogger(false);
        LOG.init(new CompositeLogger(infoOnly));

        List<String> evaluated = new ArrayList<>();
        LOG.debug(() -> {
            evaluated.add("evaluated");
            return "debug";
        });

        assertEquals(Collections.emptyList(), evaluated, "a lazy message must not be built when no delegate wants it");
    }

    @Test
    public void testCallerDetectionLooksThroughTheComposite() {
        RecordingLogger delegate = new RecordingLogger(true);
        LOG.init(new CompositeLogger(delegate));

        LOG.info("who called");

        assertEquals(CompositeLoggerTest.class, delegate.entries.get(0).getSourceClass());
        assertEquals(CompositeLoggerTest.class.getName(), delegate.entries.get(0).getCaller().getClassName());
    }

    @Test
    public void testDelegatesAreCopiedAndMustNotBeNull() {
        RecordingLogger first = new RecordingLogger(true);
        RecordingLogger second = new RecordingLogger(true);
        ILogger[] delegates = {first};
        CompositeLogger composite = new CompositeLogger(delegates);
        delegates[0] = second;

        composite.log(new LogEntry(INFO, InfoObject.class, null, "copied"));
        assertEquals(Collections.singletonList("copied"), first.messages);
        assertEquals(Collections.emptyList(), second.messages);

        assertEquals(Collections.singletonList(first), composite.getLoggers());
        assertThrows(UnsupportedOperationException.class, () -> composite.getLoggers().add(second));

        assertThrows(NullPointerException.class, () -> new CompositeLogger(first, null));
        assertThrows(NullPointerException.class, () -> new CompositeLogger((ILogger[]) null));
    }

    /**
     * Records every entry; {@code debugEnabled == false} rejects TRACE and DEBUG.
     */
    private static class RecordingLogger implements ILogger {

        final boolean debugEnabled;
        final List<LogEntry> entries = new ArrayList<>();
        final List<String> messages = new ArrayList<>();

        RecordingLogger(boolean debugEnabled) {
            this.debugEnabled = debugEnabled;
        }

        @Override
        public void log(LogEntry entry) {
            entries.add(entry);
            messages.add(entry.getFormattedMessage());
        }

        @Override
        public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
            return debugEnabled || level.compareTo(INFO) >= 0;
        }
    }
}
