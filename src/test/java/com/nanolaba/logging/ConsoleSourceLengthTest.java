package com.nanolaba.logging;

import com.nanolaba.logging.util.AnonymousSource;
import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code nanolog.console.sourceLength}: a fully qualified source name abbreviated like {@code %logger{n}} of Logback.
 */
class ConsoleSourceLengthTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    public void fakeEnvironment() {
        DefaultLoggerCreator.environment = environment::get;
    }

    @AfterEach
    public void cleanUp() {
        DefaultLoggerCreator.environment = System::getenv;
        System.clearProperty(ConsoleLogger.PROPERTY_SOURCE_LENGTH);
    }

    private static ConsoleLogger fullNames(int length) {
        return new ConsoleLogger().setShowSourceFullName(true).setSourceLength(length);
    }

    private static final class Nested {
    }

    @Test
    public void testTheExamplesOfLogback() {
        // https://logback.qos.ch/manual/layouts.html, %logger{length}; its {0}, the class name alone, is not an option here
        assertEquals("m.s.s.Bar", ConsoleLogger.abbreviate("mainPackage.sub.sample", "Bar", 5));
        assertEquals("m.s.s.Bar", ConsoleLogger.abbreviate("mainPackage.sub.sample", "Bar", 10));
        assertEquals("m.s.sample.Bar", ConsoleLogger.abbreviate("mainPackage.sub.sample", "Bar", 15));
        assertEquals("m.sub.sample.Bar", ConsoleLogger.abbreviate("mainPackage.sub.sample", "Bar", 16));
        assertEquals("mainPackage.sub.sample.Bar", ConsoleLogger.abbreviate("mainPackage.sub.sample", "Bar", 26));
        assertEquals("m.s.s.Bar", ConsoleLogger.abbreviate("mainPackage.sub.sample", "Bar", 1));
        assertEquals("Bar", ConsoleLogger.abbreviate("", "Bar", 1), "a class without a package");
        // the example of the Javadoc and the README
        assertEquals("c.a.b.payments.PaymentService", ConsoleLogger.abbreviate("com.acme.billing.payments", "PaymentService", 30));
        assertEquals("c.a.b.p.PaymentService", ConsoleLogger.abbreviate("com.acme.billing.payments", "PaymentService", 20));
    }

    @Test
    public void testFullNamesAreAbbreviated() {
        assertEquals("c.n.l.u.InfoObject", fullNames(20).getSourceName(InfoObject.class));
        assertEquals("c.n.logging.util.InfoObject", fullNames(30).getSourceName(InfoObject.class));
        assertEquals("com.nanolaba.logging.util.InfoObject", fullNames(36).getSourceName(InfoObject.class));
        assertEquals("com.nanolaba.logging.util.InfoObject", fullNames(0).getSourceName(InfoObject.class), "0 is the full name");

        LogEntry entry = LogEntry.create(LogEntry.LogEntryLevel.INFO, InfoObject.class, null, null, "m", new Object[0], 0);
        assertEquals("[c.n.l.u.InfoObject] m", fullNames(1).setShowDate(false).setShowLevel(false).createLogString(entry));
    }

    @Test
    public void testOnlyAFullNameIsAbbreviated() {
        ConsoleLogger console = new ConsoleLogger().setSourceLength(1);

        assertEquals("InfoObject", console.getSourceName(InfoObject.class));
    }

    @Test
    public void testTheNameOfTheClassIsKept() {
        ConsoleLogger console = fullNames(1);

        assertEquals("c.n.l.ConsoleSourceLengthTest.Nested", console.getSourceName(Nested.class), "the enclosing class is part of the name");
        assertEquals("c.n.l.u.AnonymousSource$1", console.getSourceName(AnonymousSource.ANONYMOUS));
        assertEquals("int", console.getSourceName(int.class), "a name without a package");
    }

    @Test
    public void testTheSetter() {
        ConsoleLogger console = new ConsoleLogger().setShowSourceFullName(true);
        assertEquals(0, console.getSourceLength());

        assertSame(console, console.setSourceLength(20));
        assertEquals(20, console.getSourceLength());
        String name = console.getSourceName(InfoObject.class);
        assertEquals("c.n.l.u.InfoObject", name);
        assertSame(name, console.getSourceName(InfoObject.class), "found once per class");

        console.setSourceLength(30);
        assertEquals("c.n.logging.util.InfoObject", console.getSourceName(InfoObject.class), "another length, another name");

        console.setSourceLength(-5);
        assertEquals(0, console.getSourceLength());
        assertEquals("com.nanolaba.logging.util.InfoObject", console.getSourceName(InfoObject.class));
    }

    @Test
    public void testTheSetting() {
        System.setProperty(ConsoleLogger.PROPERTY_SOURCE_LENGTH, " 20 ");
        assertEquals(20, new ConsoleLogger().getSourceLength());
        System.setProperty(ConsoleLogger.PROPERTY_SOURCE_LENGTH, "0");
        assertEquals(0, new ConsoleLogger().getSourceLength());

        System.clearProperty(ConsoleLogger.PROPERTY_SOURCE_LENGTH);
        environment.put("NANOLOG_CONSOLE_SOURCELENGTH", "30");
        assertEquals(30, new ConsoleLogger().getSourceLength());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testInvalidValuesAreReported() {
        System.setProperty(ConsoleLogger.PROPERTY_SOURCE_LENGTH, "short");
        assertEquals(0, new ConsoleLogger().getSourceLength());
        System.setProperty(ConsoleLogger.PROPERTY_SOURCE_LENGTH, "-1");
        assertEquals(0, new ConsoleLogger().getSourceLength());

        assertEquals("[NANOLOG] Invalid value \"short\" of \"nanolog.console.sourceLength\": expected a number of characters, " +
                     "0 for the full name; set as a system property" + NL +
                     "[NANOLOG] Invalid value \"-1\" of \"nanolog.console.sourceLength\": expected a number of characters, " +
                     "0 for the full name; set as a system property" + NL, getErrAndClear());
    }
}
