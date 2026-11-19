package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public abstract class AbstractLoggerTest {

    protected final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    protected final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    protected final PrintStream originalOut = System.out;
    protected final PrintStream originalErr = System.err;
    private final String command = System.getProperty(COMMAND);

    /**
     * The main class of the JVM and its arguments. The tests run in a fork of Surefire, whose run log names its own file
     * (#96), so each test runs as a plain JVM unless it sets the command of a fork itself.
     */
    protected static final String COMMAND = "sun.java.command";

    @BeforeEach
    public void setUpStreams() {
        // nanolog.diagnostics=once remembers what earlier tests printed
        DefaultLoggerCreator.forgetDiagnostics();
        System.setProperty(COMMAND, "com.acme.shop.Main");
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void restoreStreams() {
        if (command == null) {
            System.clearProperty(COMMAND);
        } else {
            System.setProperty(COMMAND, command);
        }
        outContent.reset();
        errContent.reset();
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    protected String getOutAndClear() {
        String res = outContent.toString();
        outContent.reset();
        return res;
    }

    protected String getErrAndClear() {
        String res = errContent.toString();
        errContent.reset();
        return res;
    }
}
