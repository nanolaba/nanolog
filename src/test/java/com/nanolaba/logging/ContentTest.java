package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import com.nanolaba.logging.util.StrictJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.nanolaba.logging.LOG.content;
import static com.nanolaba.logging.LOG.kv;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.content(...)}: payloads logged as their length and hash unless shown on purpose.
 */
class ContentTest extends AbstractLoggerTest {

    private static final String PROMPT = "You are a helpful assistant.\nAnswer briefly.";
    private static final String HIDDEN = "[content " + PROMPT.length() + " chars #" + Contents.hash(PROMPT) + "]";

    @BeforeEach
    public void setUp() {
        Contents.configure();
    }

    @AfterEach
    public void tearDown() {
        for (String key : new ArrayList<>(System.getProperties().stringPropertyNames())) {
            if (key.startsWith(DefaultLoggerCreator.PROPERTY_CONTENT)) {
                System.clearProperty(key);
            }
        }
        Contents.configure();
        Breadcrumbs.threshold = -1;
        Breadcrumbs.drainThread();
        LOG.init();
    }

    private static String message(Class sourceClass, String template, Object... args) {
        return new LogEntry(INFO, sourceClass, null, template, args).getFormattedMessage();
    }

    private static void set(String key, String value) {
        System.setProperty(key, value);
        Contents.configure();
    }

    // THE MODES

    @Test
    public void testHiddenByDefault() {
        assertEquals("prompt to gpt-x: " + HIDDEN, message(InfoObject.class, "prompt to {}: {}", "gpt-x", content(PROMPT)));
        assertEquals("[content 1 char #" + Contents.hash("x") + "]", content("x").toString());
        assertEquals("[content 0 chars #" + Contents.hash("") + "]", content("").toString());
        assertEquals("[content 4 chars #" + Contents.hash("null") + "]", content(null).toString(), "null is the text null");
    }

    @Test
    public void testShowWritesTheTextEscaped() {
        set(DefaultLoggerCreator.PROPERTY_CONTENT, "show");
        assertEquals("You are a helpful assistant.\\nAnswer briefly.", content(PROMPT).toString());
        assertEquals("a\\rb\\u001bc\td\\u2028e", content("a\rb\u001bc\td e").toString(),
                     "line breaks and control characters cannot forge a line; a tab stays");
        assertEquals("[1, 2]", content(new int[]{1, 2}).toString(), "rendered like a {} argument");
    }

    @Test
    public void testShowEscapesBidiControlsAndTags() {
        // a model reply can hide text in tags, which a terminal does not show and an agent reads (#113)
        set(DefaultLoggerCreator.PROPERTY_CONTENT, "show");
        String joined = MultilineTest.cp(0x1F468, 0x200D, 0x1F469);

        assertEquals("Done.\\udb40\\udc73 admin\\u202etxt.exe " + joined,
                     content("Done." + MultilineTest.cp(0xE0073) + " admin" + MultilineTest.cp(0x202E) + "txt.exe " + joined).toString());
    }

    @Test
    public void testTruncateShowsTheStart() {
        set(DefaultLoggerCreator.PROPERTY_CONTENT, " Truncate:10 ");
        assertEquals("You are a ... [+" + (PROMPT.length() - 10) + " chars]", content(PROMPT).toString());
        assertEquals("0123456789", content("0123456789").toString(), "exactly the limit is not cut");
        assertEquals("01234\\n6789... [+1 char]", content("01234\n67890").toString());
    }

    @Test
    public void testInvalidValuesAreReported() {
        for (String value : new String[]{"truncate:0", "truncate:-5", "truncate:x", "truncate", "loud"}) {
            set(DefaultLoggerCreator.PROPERTY_CONTENT, value);
            assertEquals(HIDDEN, content(PROMPT).toString(), value);
            assertTrue(getErrAndClear().contains("Invalid value \"" + value + "\" of \"nanolog.content\": expected hide, show or truncate:<characters>"),
                       value);
        }
        set(DefaultLoggerCreator.PROPERTY_CONTENT, "HIDE");
        assertEquals(HIDDEN, content(PROMPT).toString());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testTheHashIsKeyedPerJvm() throws Exception {
        String hash = Contents.hash(PROMPT);
        assertTrue(hash.matches("[0-9a-f]{8}"), hash);
        assertEquals(hash, Contents.hash(PROMPT), "equal payloads, equal hashes");
        assertNotEquals(hash, Contents.hash(PROMPT + " "));
        assertNotEquals(Units.key(PROMPT), hash, "not the plain hashCode, which a short payload can be found from");
        byte[] plain = java.security.MessageDigest.getInstance("SHA-256").digest(PROMPT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertNotEquals(String.format("%02x%02x%02x%02x", plain[0], plain[1], plain[2], plain[3]), hash, "nor an unkeyed digest");
        assertEquals(Contents.hash(""), Contents.hash(""));
        assertEquals("0000000a", String.format("%02x%02x%02x%02x", (byte) 0, (byte) 0, (byte) 0, (byte) 10));
    }

    // RULES FOR PACKAGES AND CLASSES

    @Test
    public void testTheMostSpecificRuleWins() {
        set(DefaultLoggerCreator.PROPERTY_CONTENT, "show");
        set(DefaultLoggerCreator.PROPERTY_CONTENT + ".com.nanolaba", "hide");
        set(DefaultLoggerCreator.PROPERTY_CONTENT + ".com.nanolaba.logging.util", "truncate:3");
        set(DefaultLoggerCreator.PROPERTY_CONTENT + "." + Outer.Inner.class.getName(), "show");
        set(DefaultLoggerCreator.PROPERTY_CONTENT + ".com.nanolaba.logging.ContentTest$Outer", "truncate:2");

        assertEquals("abc... [+3 chars]", message(InfoObject.class, "{}", content("abcdef")));
        assertEquals("[content 6 chars #" + Contents.hash("abcdef") + "]", message(ContentTest.class, "{}", content("abcdef")));
        assertEquals("abcdef", message(Outer.Inner.class, "{}", content("abcdef")));
        assertEquals("ab... [+4 chars]", message(Outer.class, "{}", content("abcdef")));
        assertEquals("abcdef", message(String.class, "{}", content("abcdef")), "no rule: the global setting");
        assertEquals("abcdef", message(null, "{}", content("abcdef")));
        assertEquals("abcdef", content("abcdef").toString(), "outside a logging call: the global setting");

        set(DefaultLoggerCreator.PROPERTY_CONTENT + ".com.acme", "nonsense");
        assertTrue(getErrAndClear().contains("Invalid value \"nonsense\" of \"nanolog.content.com.acme\""));
    }

    @Test
    public void testARuleForAPackageHidesAPayloadOfTheContext() {
        set(DefaultLoggerCreator.PROPERTY_CONTENT, "show");
        set(DefaultLoggerCreator.PROPERTY_CONTENT + ".com.nanolaba.logging.util", "hide");
        try (LOG.Context ignored = LOG.context("prompt", content("secret"))) {
            assertEquals("[content 6 chars #" + Contents.hash("secret") + "]",
                         new LogEntry(INFO, InfoObject.class, null, "m").getContext().get("prompt").toString());
            assertEquals("secret", new LogEntry(INFO, ContentTest.class, null, "m").getContext().get("prompt").toString());
        }
    }

    private static final class Outer {
        private static final class Inner {
        }
    }

    // WHERE CONTENT GOES

    @Test
    public void testContentAsAFieldAsTheMessageAndInTheContext() {
        LogEntry field = new LogEntry(INFO, InfoObject.class, null, "Prompt sent", kv("prompt", content(PROMPT)), kv("tokens", 12));
        assertEquals("Prompt sent prompt=\"" + HIDDEN + "\" tokens=12", new ConsoleLogger().setShowDate(false).setShowLevel(false)
                                                                                           .setShowSource(false).createLogString(field));
        String json = new JsonLogger().createLogString(field);
        assertTrue(json.contains("\"fields\":{\"prompt\":{\"content_len\":" + PROMPT.length() + ",\"content_hash\":\"" +
                                 Contents.hash(PROMPT) + "\"},\"tokens\":12}"), json);
        Map<String, Object> parsed = (Map<String, Object>) ((Map<String, Object>) StrictJson.parseObject(json).get("fields")).get("prompt");
        assertEquals(new BigDecimal(PROMPT.length()), parsed.get("content_len"));

        assertEquals(HIDDEN, new LogEntry(INFO, InfoObject.class, null, content(PROMPT)).getFormattedMessage());

        try (LOG.Context ignored = LOG.context("prompt", content(PROMPT))) {
            assertTrue(new JsonLogger().createLogString(new LogEntry(INFO, InfoObject.class, null, "m"))
                                       .contains("\"context\":{\"prompt\":{\"content_len\":"));
        }

        set(DefaultLoggerCreator.PROPERTY_CONTENT, "show");
        String shown = new JsonLogger().createLogString(new LogEntry(INFO, InfoObject.class, null, "m", kv("prompt", content("a\nb"))));
        assertTrue(shown.contains("\"fields\":{\"prompt\":\"a\\\\nb\"}"), shown);
    }

    @Test
    public void testTheCallerArrayIsNotChanged() {
        LOG.Content secret = content("secret");
        Object[] args = {secret, "plain"};
        LogEntry entry = new LogEntry(INFO, InfoObject.class, null, "{} {}", args);
        assertSame(secret, args[0], "the entry binds a copy");
        assertEquals("[content 6 chars #" + Contents.hash("secret") + "] plain", entry.getFormattedMessage());
        Object[] plain = {"a", "b"};
        assertSame(plain, new LogEntry(INFO, InfoObject.class, null, "{} {}", plain).getArgs(), "without content nothing is copied");
        assertArrayEquals(new Object[]{"a"}, new LogEntry(INFO, InfoObject.class, null, "{}", (Object) "a").getArgs());
    }

    @Test
    public void testTheTextIsTakenWhenTheEntryIsCreated() {
        List<String> items = new ArrayList<>(Arrays.asList("a"));
        set(DefaultLoggerCreator.PROPERTY_CONTENT, "show");
        LogEntry entry = new LogEntry(INFO, InfoObject.class, null, "{}", kv("items", content(items)));
        items.add("b");
        assertEquals("[a]", entry.getFields().get("items").toString());
        assertSame(entry.getFields().get("items"), entry.snapshot().getFields().get("items"), "a bound content cannot change");

        try (LOG.Context ignored = LOG.context("items", content(items)); LOG.Context plain = LOG.context("plain", "text")) {
            LogEntry inContext = new LogEntry(INFO, InfoObject.class, null, "m");
            items.add("c");
            assertEquals("[a, b]", inContext.getContext().get("items").toString(), "a payload of the context is bound too");
            assertSame(inContext.getContext().get("items"), inContext.snapshot().getContext().get("items"));
            assertEquals(Arrays.asList("items", "plain"), new ArrayList<>(inContext.getContext().keySet()));
            assertThrows(UnsupportedOperationException.class, () -> inContext.getContext().put("x", 1));
            assertTrue(Contexts.current().get("items") instanceof LOG.Content, "the context of the thread is left as it is");
        }
        try (LOG.Context ignored = LOG.context("plain", "text")) {
            assertSame(Contexts.current(), new LogEntry(INFO, InfoObject.class, null, "m").getContext(), "nothing to bind, nothing copied");
        }
    }

    @Test
    public void testABrokenToStringIsGuarded() {
        Object broken = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException();
            }
        };
        assertEquals("[content 19 chars #" + Contents.hash("[FAILED toString()]") + "]", content(broken).toString());
        assertEquals("[content 19 chars #" + Contents.hash("[FAILED toString()]") + "]", message(InfoObject.class, "{}", content(broken)));
    }

    @Test
    public void testThroughLogAndTheBreadcrumbs() {
        set(DefaultLoggerCreator.PROPERTY_CONTENT + ".com.nanolaba.logging.ContentTest", "show");
        Breadcrumbs.threshold = DEBUG.ordinal();
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.debug("prompt {}", content("hi"));
            LOG.error("failed");
            assertEquals("prompt hi", memory.getEntries().get(1).getFormattedMessage());
            LOG.info(InfoObject.class, "prompt {}", content("hi"));
            assertEquals("prompt [content 2 chars #" + Contents.hash("hi") + "]", memory.getEntries().get(3).getFormattedMessage());
        }
    }
}
