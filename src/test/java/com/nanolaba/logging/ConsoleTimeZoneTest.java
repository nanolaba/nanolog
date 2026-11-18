package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code nanolog.console.timeZone}, {@code ConsoleLogger.setTimeZone} and the preset {@code nanolog.console.dateFormat=iso},
 * with a fixed clock.
 */
class ConsoleTimeZoneTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();
    private static final Date NOW = Date.from(Instant.parse("2026-11-18T09:30:00Z"));
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");
    private static final TimeZone TOKYO = TimeZone.getTimeZone("Asia/Tokyo");

    private final Map<String, String> environment = new HashMap<>();
    private final TimeZone originalDefault = TimeZone.getDefault();

    @BeforeEach
    public void fakeEnvironment() {
        DefaultLoggerCreator.environment = environment::get;
        // a zone of the JVM unlike every zone below, so the tests tell them apart on a machine in UTC as well
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
    }

    @AfterEach
    public void cleanUp() {
        TimeZone.setDefault(originalDefault);
        DefaultLoggerCreator.environment = System::getenv;
        System.clearProperty(ConsoleLogger.PROPERTY_TIME_ZONE);
        System.clearProperty(ConsoleLogger.PROPERTY_DATE_FORMAT);
    }

    /**
     * A console that prints only the date, of a fixed moment.
     */
    private static ConsoleLogger console() {
        return new ConsoleLogger() {
            @Override
            protected Date getCurrentDate() {
                return NOW;
            }
        }.setShowLevel(false).setShowSource(false);
    }

    private static String date(ConsoleLogger console) {
        String line = console.createLogString(LogEntry.create(LogEntry.LogEntryLevel.INFO, null, null, null, "m", new Object[0], 0));
        return line.substring(0, line.length() - " m".length());
    }

    private static String inDefaultZone(String pattern) {
        return new SimpleDateFormat(pattern).format(NOW);
    }

    private ConsoleLogger configured(String zone) {
        System.setProperty(ConsoleLogger.PROPERTY_TIME_ZONE, zone);
        return console();
    }

    @Test
    public void testTheSetting() {
        assertEquals("18.11.2026 09:30:00", date(configured("UTC")));
        assertEquals("18.11.2026 10:30:00", date(configured("Europe/Berlin")));
        assertEquals("18.11.2026 18:30:00", date(configured(" Asia/Tokyo ")));
        assertEquals("18.11.2026 12:30:00", date(configured("GMT+03:00")));
        assertEquals("18.11.2026 09:30:00", date(configured("GMT")));
        assertEquals("Europe/Berlin", configured("Europe/Berlin").getTimeZone().getID());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testWithoutTheSettingTheZoneOfTheJvm() {
        ConsoleLogger console = console();

        assertNull(console.getTimeZone());
        assertEquals("17.11.2026 23:30:00", date(console));
        assertEquals(inDefaultZone("dd.MM.yyyy HH:mm:ss"), date(console));
    }

    @Test
    public void testTheEnvironmentVariable() {
        environment.put("NANOLOG_CONSOLE_TIMEZONE", "UTC");

        assertEquals("18.11.2026 09:30:00", date(console()));
    }

    @Test
    public void testAnUnknownZoneIsReported() {
        ConsoleLogger console = configured("Europe/Berln");

        assertEquals("[NANOLOG] Invalid value \"Europe/Berln\" of \"nanolog.console.timeZone\": expected a time zone ID such as UTC, " +
                     "Europe/Berlin or GMT+03:00; set as a system property" + NL, getErrAndClear(),
                     "TimeZone.getTimeZone would take it for GMT without a word");
        assertNull(console.getTimeZone());
        assertEquals(inDefaultZone("dd.MM.yyyy HH:mm:ss"), date(console));

        configured("");
        assertEquals("[NANOLOG] Invalid value \"\" of \"nanolog.console.timeZone\": expected a time zone ID such as UTC, " +
                     "Europe/Berlin or GMT+03:00; set as a system property" + NL, getErrAndClear());
    }

    @Test
    public void testTheIsoPreset() {
        System.setProperty(ConsoleLogger.PROPERTY_DATE_FORMAT, "iso");
        ConsoleLogger console = configured("UTC");
        assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", console.getDateFormat().toPattern());
        assertEquals("2026-11-18T09:30:00.000Z", date(console));

        System.setProperty(ConsoleLogger.PROPERTY_DATE_FORMAT, " ISO ");
        assertEquals("2026-11-18T10:30:00.000+01:00", date(configured("Europe/Berlin")));
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testTheSetter() {
        ConsoleLogger console = console();

        assertSame(console, console.setTimeZone(UTC));
        assertSame(UTC, console.getTimeZone());
        assertEquals("18.11.2026 09:30:00", date(console));

        console.setDateFormat(new SimpleDateFormat("HH:mm"));
        assertEquals("09:30", date(console), "a format set later takes the zone of the logger");

        console.setTimeZone(BERLIN);
        assertEquals("10:30", date(console));

        console.setTimeZone(null);
        assertNull(console.getTimeZone());
        assertEquals(inDefaultZone("HH:mm"), date(console), "null is the zone of the JVM");

        SimpleDateFormat tokyo = new SimpleDateFormat("HH:mm");
        tokyo.setTimeZone(TOKYO);
        console.setDateFormat(tokyo);
        assertEquals("18:30", date(console), "without a zone of the logger a format keeps its own");
    }

    @Test
    public void testNoFormat() {
        ConsoleLogger console = console().setDateFormat(null).setTimeZone(UTC).setDateFormat(null);

        assertEquals("", console.createLogString(LogEntry.create(LogEntry.LogEntryLevel.INFO, null, null, null, "", new Object[0], 0)));
        assertSame(UTC, console.getTimeZone());
    }
}
