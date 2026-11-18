package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.TRACE;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.tag(...)}: entries of a concern, such as audit or SQL, with levels per tag in {@code nanolog.tags}.
 */
class TagTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        LOG.clearLimits();
    }

    @AfterEach
    public void tearDown() {
        DefaultLoggerCreator.environment = System::getenv;
        System.clearProperty(DefaultLoggerCreator.PROPERTY_TAGS);
        System.clearProperty(DefaultLoggerCreator.PROPERTY_LEVEL);
        System.clearProperty("nanolog.level.com.nanolaba");
        LOG.clearLimits();
        LOG.init();
    }

    @Test
    public void testEveryShapeAtEveryLevelCarriesTheTag() {
        IllegalStateException e = new IllegalStateException("boom");
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.Tagged audit = LOG.tag("audit");
            audit.trace("t {}", 1);
            audit.trace(() -> "t");
            audit.trace(e, "t {}", 1);
            audit.trace(e, () -> "t");
            audit.debug("d {}", 1);
            audit.debug(() -> "d");
            audit.debug(e, "d {}", 1);
            audit.debug(e, () -> "d");
            audit.info("i {}", 1);
            audit.info(() -> "i");
            audit.info(e, "i {}", 1);
            audit.info(e, () -> "i");
            audit.warn("w {}", 1);
            audit.warn(() -> "w");
            audit.warn(e, "w {}", 1);
            audit.warn(e, () -> "w");
            audit.error("e {}", 1);
            audit.error(() -> "e");
            audit.error(e, "e {}", 1);
            audit.error(e, () -> "e");

            List<LogEntry> entries = memory.getEntries();
            assertEquals(20, entries.size());
            LogEntry.LogEntryLevel[] levels = {TRACE, DEBUG, INFO, WARN, ERROR};
            for (int i = 0; i < entries.size(); i++) {
                LogEntry entry = entries.get(i);
                String name = levels[i / 4].name().substring(0, 1).toLowerCase();
                assertEquals(levels[i / 4], entry.getLevel());
                assertEquals(i % 2 == 0 ? name + " 1" : name, entry.getFormattedMessage());
                assertSame(i % 4 < 2 ? null : e, entry.getThrowable());
                assertEquals(Collections.singletonList("audit"), entry.getTags());
                assertEquals(TagTest.class, entry.getSourceClass());
                assertEquals("testEveryShapeAtEveryLevelCarriesTheTag", entry.getCaller().getMethodName());
                assertEquals(LOG.Tagged.class.getName(), entry.getCallerBoundary());
            }
            assertTrue(entries.get(0).toString().contains(", tags=[audit]"), entries.get(0).toString());

            LOG.info("untagged");
            LOG.tag(null).info("no tag");
            assertEquals(Collections.emptyList(), memory.getEntries().get(20).getTags());
            assertEquals(Collections.emptyList(), memory.getEntries().get(21).getTags());
            assertFalse(memory.getEntries().get(20).toString().contains("tags"));
        }
    }

    @Test
    public void testATagWithALimit() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            for (int i = 0; i < 3; i++) {
                LOG.tag("audit").once().warn("once");
                LOG.tag("sql").atMostEvery(1, TimeUnit.HOURS).info("hourly");
            }

            assertEquals(Arrays.asList("once", "hourly"), memory.getMessages());
            assertEquals(Collections.singletonList("audit"), memory.getEntries().get(0).getTags());
            assertEquals(Collections.singletonList("sql"), memory.getEntries().get(1).getTags());
            assertEquals(LOG.Limited.class.getName(), memory.getEntries().get(0).getCallerBoundary());

            LOG.once().info("a limit without a tag");
            assertEquals(Collections.emptyList(), memory.getEntries().get(2).getTags());
        }
    }

    private ConsoleLogger console(String tags) {
        System.setProperty(DefaultLoggerCreator.PROPERTY_TAGS, tags);
        System.setProperty(DefaultLoggerCreator.PROPERTY_LEVEL, "INFO");
        return new ConsoleLogger().setShowDate(false);
    }

    @Test
    public void testATagRuleDecidesForItsEntries() {
        System.setProperty("nanolog.level.com.nanolaba", "TRACE");
        ConsoleLogger console = console("audit=WARN, sql = DEBUG,,");

        assertFalse(console.isEnabled(INFO, InfoObject.class, "audit"), "the tag wins over the package rule");
        assertTrue(console.isEnabled(WARN, InfoObject.class, "audit"));
        assertTrue(console.isEnabled(DEBUG, String.class, "sql"), "the tag wins over the global level");
        assertFalse(console.isEnabled(TRACE, String.class, "sql"));
        assertFalse(console.isEnabled(DEBUG, String.class, "other"), "a tag without a rule follows the class");
        assertFalse(console.isEnabled(DEBUG, String.class, null));
        assertTrue(console.isEnabled(DEBUG, InfoObject.class, null));
        assertTrue(console.mayBeEnabled(DEBUG));
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testATagRuleMayEnableALevelThatIsOffEverywhereElse() {
        ConsoleLogger console = console("sql=DEBUG");
        assertTrue(console.mayBeEnabled(DEBUG));
        assertFalse(console.mayBeEnabled(TRACE));
        assertFalse(console("audit=WARN").mayBeEnabled(DEBUG));
    }

    @Test
    public void testTheConsoleThroughLog() {
        LOG.init(console("sql=DEBUG,audit=WARN"));

        LOG.tag("sql").debug("select 1");
        LOG.debug("plain debug");
        LOG.tag("audit").info("audit info");
        LOG.tag("audit").warn("role changed");

        assertEquals("DEBUG [TagTest] [sql] select 1" + NL + "WARN [TagTest] [audit] role changed" + NL, getOutAndClear());
    }

    @Test
    public void testADisabledTagBuildsNothing() {
        LOG.init(console("audit=WARN"));
        AtomicInteger calls = new AtomicInteger();

        LOG.tag("audit").info(() -> "built " + calls.incrementAndGet());

        assertEquals(0, calls.get());
        assertEquals("", getOutAndClear());
    }

    @Test
    public void testInvalidRules() {
        ConsoleLogger console = console("audit, =WARN, sql=LOUD, cache=ERROR");

        assertEquals("[NANOLOG] Invalid item \"audit\" of \"nanolog.tags\": expected <tag>=<level>; set as a system property" + NL +
                     "[NANOLOG] Invalid item \"=WARN\" of \"nanolog.tags\": expected <tag>=<level>; set as a system property" + NL +
                     "[NANOLOG] Invalid value \"LOUD\" of \"nanolog.tags\": expected TRACE, DEBUG, INFO, WARN, ERROR or OFF; " +
                     "set as a system property" + NL, getErrAndClear());
        assertFalse(console.isEnabled(WARN, String.class, "cache"), "the valid items apply");
    }

    @Test
    public void testResetLevelsReadsTheRulesAgain() {
        ConsoleLogger console = console("sql=DEBUG");
        System.setProperty(DefaultLoggerCreator.PROPERTY_TAGS, "sql=WARN");

        console.resetLevels();

        assertFalse(console.isEnabled(INFO, String.class, "sql"));
    }

    @Test
    public void testACompositeAsksEachDelegateWithTheTag() {
        MemoryLogger memory = new MemoryLogger();
        CompositeLogger composite = new CompositeLogger(console("sql=DEBUG,audit=OFF"), memory);
        LOG.init(composite);

        LOG.tag("audit").error("only in memory");
        LOG.tag("sql").debug("in both");

        assertEquals("DEBUG [TagTest] [sql] in both" + NL, getOutAndClear(), "the console takes only the levels of its tag rules");
        assertEquals(Arrays.asList("only in memory", "in both"), memory.getMessages());
        assertTrue(composite.isEnabled(ERROR, String.class, "audit"), "one delegate takes it");
        assertFalse(new CompositeLogger(console("audit=OFF")).isEnabled(ERROR, String.class, "audit"));
    }

    @Test
    public void testJson() {
        JsonLogger json = new JsonLogger();
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.tag("audit").info("tagged");
            LOG.info("plain");

            String tagged = json.createLogString(memory.getEntries().get(0));
            assertTrue(tagged.contains("\"thread\":\"" + Thread.currentThread().getName() + "\",\"tags\":[\"audit\"],\"message\""), tagged);
            assertFalse(json.createLogString(memory.getEntries().get(1)).contains("\"tags\""));
        }
    }

    @Test
    public void testUntaggedCallsAskTheLoggerAsBefore() {
        List<String> asked = new ArrayList<>();
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                asked.add(level + " " + sourceClass.getSimpleName());
                return level == WARN;
            }
        });

        LOG.tag("audit").warn("through the default method");
        LOG.info("plain");

        assertEquals(Arrays.asList("WARN TagTest", "INFO TagTest"), asked);
    }
}
