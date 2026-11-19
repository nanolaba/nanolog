package com.nanolaba.logging;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Prints the end of the run when the launcher session of the JUnit Platform closes: once per test JVM, after its last
 * test class. Maven Surefire opens one session per forked JVM and, with {@code forkCount} above 1, runs each test class
 * of the fork as a plan of its own, so the end of a plan ({@link SummaryListener}) is not yet the end of the run.
 * <p>
 * The JUnit Platform 1.8 and later finds it through {@code ServiceLoader}, with nothing to configure; an older platform
 * never loads it, and {@link SummaryListener} prints at the end of the plan as before. A class of its own, so that
 * loading {@code SummaryListener} needs no interface that an older platform lacks.
 */
public final class SummarySessionListener implements LauncherSessionListener {

    /**
     * Creates the listener; JUnit does, through {@code ServiceLoader}.
     */
    public SummarySessionListener() {
        // no state: the sessions are counted by Nanolog for the whole JVM
    }

    /**
     * Notes that a session opened, so that the end of a plan inside it waits for the end of the session.
     *
     * @param session the session; not used
     */
    @Override
    public void launcherSessionOpened(LauncherSession session) {
        Summary.sessionOpened();
    }

    /**
     * Prints the end of the run, unless it has been printed already or no test plan ran in this JVM.
     *
     * @param session the session; not used
     */
    @Override
    public void launcherSessionClosed(LauncherSession session) {
        Summary.sessionClosed();
    }
}
