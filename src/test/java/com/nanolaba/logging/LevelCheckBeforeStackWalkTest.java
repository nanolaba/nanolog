package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A call without an explicit class asks {@link ILogger#mayBeEnabled} before caller detection walks the stack, so a
 * level that is off for every class costs no stack walk.
 */
class LevelCheckBeforeStackWalkTest extends AbstractLoggerTest {

    private final CallerDetection.Locator original = LOG.callerLocator;
    private final CountingLocator counting = new CountingLocator(original);
    private final AtomicInteger walks = counting.walks;

    @BeforeEach
    public void countStackWalks() {
        LOG.callerLocator = counting;
    }

    @AfterEach
    public void restore() {
        LOG.callerLocator = original;
        System.clearProperty("nanolog.level");
        System.clearProperty("nanolog.level.com.nanolaba.logging");
        LOG.init();
    }

    @Test
    public void testLevelOffForEveryClassDoesNotWalkTheStack() {
        LOG.init(new ConsoleLogger().setTraceEnabled(false).setDebugEnabled(false));
        List<String> evaluated = new ArrayList<>();

        LOG.trace("trace {}", 1);
        LOG.debug(() -> {
            evaluated.add("debug");
            return "debug";
        });
        assertFalse(LOG.isTraceEnabled());
        assertFalse(LOG.isDebugEnabled());

        assertEquals(0, walks.get(), "no stack walk for a level that is off everywhere");
        assertEquals(Collections.emptyList(), evaluated);
        assertEquals("", getOutAndClear());

        LOG.info("info");
        assertTrue(LOG.isInfoEnabled());

        assertEquals(2, walks.get());
        assertTrue(getOutAndClear().contains("[LevelCheckBeforeStackWalkTest] info"));
    }

    @Test
    public void testPackageRuleBelowTheGlobalLevelStillApplies() {
        System.setProperty("nanolog.level", "INFO");
        System.setProperty("nanolog.level.com.nanolaba.logging", "DEBUG");
        LOG.init(new ConsoleLogger());

        LOG.trace("trace");
        assertEquals(0, walks.get(), "no rule enables TRACE");

        LOG.debug("debug from a package with its own rule");
        assertTrue(LOG.isDebugEnabled());

        assertEquals(2, walks.get());
        assertTrue(getOutAndClear().contains("[LevelCheckBeforeStackWalkTest] debug from a package with its own rule"));
    }

    @Test
    public void testLoggerWithoutMayBeEnabledIsAskedWithTheDetectedClass() {
        List<Class> asked = new ArrayList<>();
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                asked.add(sourceClass);
                return false;
            }
        });

        LOG.debug("disabled");
        assertFalse(LOG.isDebugEnabled());

        assertEquals(Arrays.<Class>asList(LevelCheckBeforeStackWalkTest.class, LevelCheckBeforeStackWalkTest.class), asked);
        assertEquals(2, walks.get());
    }

    @Test
    public void testLevelThatMayNotBeEnabledIsNotAskedForAClass() {
        List<LogEntry.LogEntryLevel> asked = new ArrayList<>();
        List<String> logged = new ArrayList<>();
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
                logged.add(entry.getFormattedMessage());
            }

            @Override
            public boolean mayBeEnabled(LogEntry.LogEntryLevel level) {
                return level != DEBUG;
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                asked.add(level);
                return true;
            }
        });

        LOG.debug("skipped");
        assertFalse(LOG.isDebugEnabled());
        LOG.info("logged");

        assertEquals(Collections.singletonList(INFO), asked);
        assertEquals(Collections.singletonList("logged"), logged);
        assertEquals(1, walks.get());
    }
}
