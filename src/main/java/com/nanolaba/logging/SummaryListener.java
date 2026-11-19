package com.nanolaba.logging;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * Prints the end of the run when JUnit has run the tests: the run summary ({@code nanolog.summary}), or else the
 * {@code LOG.dbg} calls that ran. JUnit finds it through {@code ServiceLoader} in every test run on the JUnit Platform,
 * with nothing to configure, and calls it while the output of the tests is still read: Maven Surefire drops what a
 * shutdown hook of its forked JVM prints. Inside a launcher session of the JUnit Platform 1.8+ the end of the plan
 * waits for the end of the session ({@link SummarySessionListener}), since Surefire runs each test class of a fork as a
 * plan of its own when {@code forkCount} is above 1. Without the JUnit Platform the shutdown hook prints the same lines;
 * only the first of them prints.
 * <p>
 * Compiled against the JUnit Platform Launcher API, which the test run provides; Nanolog itself still has no
 * dependencies, and outside a JUnit run the class is never loaded.
 */
public final class SummaryListener implements TestExecutionListener {

    /**
     * Creates the listener; JUnit does, through {@code ServiceLoader}.
     */
    public SummaryListener() {
        // no state: the summary is kept by Nanolog for the whole JVM
    }

    /**
     * Prints the end of the run, unless it has been printed already or a launcher session is open: then the end of
     * the session is the end of the run ({@link SummarySessionListener}).
     *
     * @param testPlan the plan that finished; not used
     */
    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        Summary.planFinished();
    }
}
