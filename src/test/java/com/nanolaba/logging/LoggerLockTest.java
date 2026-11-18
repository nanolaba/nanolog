package com.nanolaba.logging;

import org.example.vendor.Sdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A logger replaced by code of another organization is reported, and {@code LOG.lock()} / {@code nanolog.logger.lock}
 * keep the logger of the application from libraries.
 */
class LoggerLockTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();
    private static final String CONSOLE = ConsoleLogger.class.getName();
    private static final String JSON = JsonLogger.class.getName();

    private final Map<String, String> environment = new HashMap<>();
    private final Function<Throwable, StackTraceElement[]> originalStackTrace = CallerDetection.ThrowableWalk.stackTraceOf;

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        LOG.init();
        getErrAndClear();
    }

    @AfterEach
    public void tearDown() {
        LOG.unlock();
        CallerDetection.ThrowableWalk.stackTraceOf = originalStackTrace;
        System.clearProperty(DefaultLoggerCreator.PROPERTY_LOGGER_LOCK);
        DefaultLoggerCreator.environment = System::getenv;
        LOG.init();
    }

    /**
     * The call site of the line after the call of this method, as a frame prints it.
     */
    private static String nextLine() {
        StackTraceElement caller = new Throwable().getStackTrace()[1];
        return new StackTraceElement(caller.getClassName(), caller.getMethodName(), caller.getFileName(), caller.getLineNumber() + 1).toString();
    }

    @Test
    public void testALibraryThatReplacesTheLoggerIsReported() {
        String application = nextLine();
        LOG.init(new ConsoleLogger());
        assertEquals("", getErrAndClear(), "the logger of the configuration may be replaced");

        String library = Sdk.install(new JsonLogger());

        assertEquals("[NANOLOG] Logger replaced: " + CONSOLE + " (installed at " + application + ") -> " + JSON + " (installed at " + library + ")" +
                     NL, getErrAndClear());
        assertEquals(JsonLogger.class, LOG.getLogger().getClass(), "the replacement happens");

        String again = nextLine();
        LOG.init(new ConsoleLogger());
        assertEquals("[NANOLOG] Logger replaced: " + JSON + " (installed at " + library + ") -> " + CONSOLE + " (installed at " + again + ")" + NL,
                     getErrAndClear(), "the application taking it back is reported the same way");
    }

    @Test
    public void testTheCodeOfOneOrganizationAndTheSameLoggerAreQuiet() {
        LOG.init(new ConsoleLogger());
        LOG.init(new JsonLogger());
        assertEquals("", getErrAndClear(), "com.nanolaba, twice");

        ConsoleLogger console = new ConsoleLogger();
        Sdk.install(console);
        getErrAndClear();
        LOG.init(console);
        assertEquals("", getErrAndClear(), "the same logger again");
    }

    @Test
    public void testALibraryThatResetsTheLogger() {
        String application = nextLine();
        LOG.init(new JsonLogger());

        String library = Sdk.reset();

        assertEquals("[NANOLOG] Logger replaced: " + JSON + " (installed at " + application + ") -> " + CONSOLE + " (installed at " + library + ")" +
                     NL, getErrAndClear());
        LOG.init(new JsonLogger());
        assertEquals("", getErrAndClear(), "LOG.init() installs the logger of the configuration");
    }

    @Test
    public void testAnUnknownCallSite() {
        LOG.init(new ConsoleLogger());
        CallerDetection.ThrowableWalk.stackTraceOf = t -> new StackTraceElement[0];

        LOG.init(new JsonLogger());
        LOG.init(new ConsoleLogger());

        String err = getErrAndClear();
        assertEquals(1, err.split(NL).length, err);
        assertTrue(err.endsWith(" -> " + JSON + " (installed at an unknown place)" + NL), err);
    }

    @Test
    public void testTheOrganization() {
        assertEquals("com.acme", LOG.Origin.organizationOf("com.acme.billing.Main"));
        assertEquals("com.acme", LOG.Origin.organizationOf("com.acme.Main$1"));
        assertEquals("acme", LOG.Origin.organizationOf("acme.Main"));
        assertEquals("", LOG.Origin.organizationOf("Main"));
    }

    @Test
    public void testTheLockIgnoresLaterInits() {
        ConsoleLogger console = new ConsoleLogger();
        String application = nextLine();
        LOG.init(console);
        LOG.lock();

        String library = Sdk.install(new JsonLogger());
        String own = nextLine();
        LOG.init();

        assertSame(console, LOG.getLogger());
        assertEquals("[NANOLOG] Logger locked: " + CONSOLE + " (installed at " + application + ") stays; LOG.init(" + JSON + ") at " + library +
                     " is ignored" + NL +
                     "[NANOLOG] Logger locked: " + CONSOLE + " (installed at " + application + ") stays; LOG.init() at " + own + " is ignored" + NL,
                     getErrAndClear());
    }

    @Test
    public void testMemoryLoggerWorksUnderTheLock() {
        ConsoleLogger console = new ConsoleLogger();
        LOG.init(console);
        LOG.lock();

        try (MemoryLogger memory = MemoryLogger.install()) {
            assertSame(memory, LOG.getLogger());
            LOG.info("captured");
            assertEquals(1, memory.getEntries().size());
        }

        assertSame(console, LOG.getLogger());
        assertEquals("", getErrAndClear());
        Sdk.install(new JsonLogger());
        String err = getErrAndClear();
        assertTrue(err.startsWith("[NANOLOG] Logger locked: " + CONSOLE + " (installed at com.nanolaba."), "close() restores the origin as well: " + err);
    }

    @Test
    public void testTheSetting() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_LOGGER_LOCK, "true");
        LOG.init();
        ILogger configured = LOG.getLogger();

        String library = Sdk.install(new JsonLogger());

        assertSame(configured, LOG.getLogger());
        assertEquals("[NANOLOG] Logger locked: " + CONSOLE + " (from the configuration) stays; LOG.init(" + JSON + ") at " + library +
                     " is ignored" + NL, getErrAndClear());
    }

    @Test
    public void testTheEnvironmentVariableAndAnInvalidValue() {
        environment.put("NANOLOG_LOGGER_LOCK", "true");
        LOG.init();
        LOG.init(new JsonLogger());
        assertEquals(CONSOLE, LOG.getLogger().getClass().getName());
        getErrAndClear();

        LOG.unlock();
        environment.put("NANOLOG_LOGGER_LOCK", "yes");
        LOG.init();
        assertEquals("[NANOLOG] Invalid value \"yes\" of \"nanolog.logger.lock\": expected true or false; set by the environment variable " +
                     "NANOLOG_LOGGER_LOCK" + NL, getErrAndClear());
        LOG.init(new JsonLogger());
        assertEquals(JsonLogger.class, LOG.getLogger().getClass(), "an invalid value does not lock");
    }
}
