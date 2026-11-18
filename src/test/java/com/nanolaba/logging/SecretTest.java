package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.spi.LocationAwareLogger;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;

import static com.nanolaba.logging.LOG.content;
import static com.nanolaba.logging.LOG.kv;
import static com.nanolaba.logging.LOG.secret;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.secret(...)}, {@code nanolog.redact} and the secret patterns of shown payloads.
 */
class SecretTest extends AbstractLoggerTest {

    private static final Object NEVER_FORMATTED = new Object() {
        @Override
        public String toString() {
            throw new AssertionError("a secret must not be formatted");
        }
    };

    private LongSupplier originalClock;

    @BeforeEach
    public void setUp() {
        originalClock = LOG.nanoClock;
        LOG.nanoClock = () -> 0L;
        Summary.shutdownHooks = thread -> {
        };
        Redaction.configure();
        Contents.configure();
    }

    @AfterEach
    public void tearDown() {
        LOG.nanoClock = originalClock;
        Summary.shutdownHooks = Runtime.getRuntime()::addShutdownHook;
        System.clearProperty(DefaultLoggerCreator.PROPERTY_REDACT);
        System.clearProperty(DefaultLoggerCreator.PROPERTY_CONTENT);
        Redaction.configure();
        Contents.configure();
        Contexts.CURRENT.remove();
        LOG.init();
    }

    private static LogEntry entry(String message, Object... args) {
        return new LogEntry(INFO, InfoObject.class, null, message, args);
    }

    private static String console(LogEntry entry) {
        return new ConsoleLogger().setShowDate(false).setShowLevel(false).setShowSource(false).createLogString(entry);
    }

    // LOG.secret

    @Test
    public void testASecretIsNeverWritten() {
        LOG.Secret hidden = secret(NEVER_FORMATTED);
        assertEquals("***", hidden.toString());
        assertSame(hidden, secret("another"), "the value is not kept at all");

        LogEntry entry = entry("Login of {} with {}", "alice", secret(NEVER_FORMATTED));
        assertEquals("Login of alice with ***", entry.getFormattedMessage());
        assertTrue(new JsonLogger().createLogString(entry).contains("\"args\":[\"alice\",\"***\"]"));
        assertEquals("Login of alice with ***", entry.snapshot().getFormattedMessage());
        assertTrue(entry.toString().contains("args=[alice, ***]"), entry.toString());

        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.info("key {}", secret(NEVER_FORMATTED));
            LOG.dbg(secret(NEVER_FORMATTED));
            assertEquals("key ***", memory.getEntries().get(0).getFormattedMessage());
            assertTrue(memory.getEntries().get(1).getFormattedMessage().contains("***"));
        }
    }

    // nanolog.redact

    @Test
    public void testFieldsAndContextNamedLikeSecretsAreMasked() {
        LogEntry trailing = entry("Login", kv("user", "alice"), kv("password", NEVER_FORMATTED));
        assertEquals("Login user=alice password=***", console(trailing));
        assertTrue(new JsonLogger().createLogString(trailing).contains("\"fields\":{\"user\":\"alice\",\"password\":\"***\"}"));

        LogEntry placed = entry("Token {}", kv("token", NEVER_FORMATTED));
        assertEquals("Token ***", placed.getFormattedMessage(), "a placeholder does not show it either");

        try (LOG.Context a = LOG.context("authorization", NEVER_FORMATTED); LOG.Context b = LOG.context("requestId", "r-17")) {
            LogEntry inContext = entry("call");
            assertEquals("call authorization=*** requestId=r-17", console(inContext));
            assertTrue(Contexts.current().get("authorization") == NEVER_FORMATTED, "the context of the thread is left as it is");
        }

        Object[] args = {kv("password", "p")};
        entry("m", args);
        assertEquals("p", ((LOG.Field) args[0]).getValue(), "the array of the caller is not changed");
        assertEquals("m x", entry("m {}", kv(null, "x")).getFormattedMessage(), "a field without a key is a plain argument");
        Object[] plain = {kv("user", "u")};
        assertSame(plain, Redaction.apply(plain), "nothing to mask, nothing copied");
        assertSame(null, Redaction.apply((Object[]) null));
        assertSame(Contexts.current(), entry("m").getContext());
    }

    @Test
    public void testAKeyMatchesByItsWords() {
        for (String key : new String[]{"password", "PASSWORD", "db.password", "userPassword", "X-Auth-Token", "api_key", "apiKey",
                                       "client_secret", "Authorization", "credentials.credential"}) {
            assertTrue(Redaction.matches(key), key);
        }
        for (String key : new String[]{"passwords", "tokens", "gen_ai.usage.input_tokens", "user", "keyboard", "", "pass"}) {
            assertFalse(Redaction.matches(key), key);
        }
    }

    @Test
    public void testTheSetting() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_REDACT, " Session , pin-code ");
        Redaction.configure();
        assertTrue(Redaction.matches("sessionId"));
        assertTrue(Redaction.matches("pinCode"), "a word of the list is compared without its separators");
        assertTrue(Redaction.matches("PIN_CODE"));
        assertFalse(Redaction.matches("password"), "the list replaces the default one");

        System.setProperty(DefaultLoggerCreator.PROPERTY_REDACT, "");
        Redaction.configure();
        assertFalse(Redaction.matches("password"), "empty: off");
        assertEquals("m password=p", console(entry("m", kv("password", "p"))));

        System.setProperty(DefaultLoggerCreator.PROPERTY_REDACT, " , ,");
        Redaction.configure();
        assertFalse(Redaction.matches("password"));

        System.clearProperty(DefaultLoggerCreator.PROPERTY_REDACT);
        LOG.init();
        assertTrue(Redaction.matches("password"), "read by LOG.init; the default list without the setting");
    }

    @Test
    public void testSlf4jAndUnitsGetTheMaskToo() {
        BasicMDCAdapter mdc = new BasicMDCAdapter();
        List<String> seen = new ArrayList<>();
        Logger backend = (Logger) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LocationAwareLogger.class},
                                                         (proxy, method, a) -> {
                                                             if (method.getName().startsWith("is")) {
                                                                 return true;
                                                             }
                                                             seen.add(a[3] + " mdc=" + mdc.get("token"));
                                                             return null;
                                                         });
        try (LOG.Context ignored = LOG.context("token", "t-1")) {
            new Slf4jLogger(name -> backend, mdc).log(entry("call", kv("apiKey", "k-1")));
        }
        assertEquals(Arrays.asList("call apiKey=*** mdc=***"), seen);

        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Unit u = LOG.unit("login")) {
                u.put("password", NEVER_FORMATTED).put("user", "alice");
            }
            assertEquals("***", memory.getEntries().get(0).getFields().get("password").toString());
        }
    }

    // SECRET PATTERNS OF SHOWN PAYLOADS

    @Test
    public void testAShownPayloadHidesKnownSecretPatterns() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_CONTENT, "show");
        Contents.configure();
        String text = "key sk-proj-abcdefghijklmnopqrstuv aws AKIAABCDEFGHIJKLMNOP auth Bearer abc.def-ghi_jkl " +
                      "jwt eyJhbGciOiJI.eyJzdWIiOiIx.sflKxwRJSMeKKF2QT4 and bearer token";
        assertEquals("key sk-*** aws AKIA*** auth Bearer *** jwt eyJ*** and bearer token", content(text).toString());
        assertEquals("ask-me sk-short AKIAshort", content("ask-me sk-short AKIAshort").toString(), "too short to be a key");

        System.setProperty(DefaultLoggerCreator.PROPERTY_CONTENT, "truncate:10");
        Contents.configure();
        assertEquals("key sk-***... [+9 chars]", content("key sk-abcdefghijklmnopqrstuvwxyz and more").toString(), "masked before it is cut");

        System.clearProperty(DefaultLoggerCreator.PROPERTY_CONTENT);
        Contents.configure();
        String secret = "sk-abcdefghijklmnopqrstuvwxyz";
        assertEquals("[content " + secret.length() + " chars #" + Contents.hash(secret) + "]", content(secret).toString(),
                     "hidden: the length and hash of the text itself");
    }
}
