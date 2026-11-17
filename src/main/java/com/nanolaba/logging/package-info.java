/**
 * Nanolog: a tiny logging facade with static methods and automatic caller detection, so no class needs a logger
 * field. It runs on its own or on top of SLF4J, and has no runtime dependencies. When an AI coding agent runs the
 * program, agent mode keeps the console short, writes the full log to a file ({@code nanolog.agent}) and ends the run
 * with a summary that survives a truncated output ({@code nanolog.summary}).
 * <pre>{@code
 * LOG.info("Application started");                    // the source class is detected from the stack
 * LOG.debug("Loaded {} items in {} ms", count, millis);
 * LOG.error("Failed to load {}", id, e);              // the trailing exception keeps its stack trace
 * BigDecimal total = LOG.dbg(order.total());          // prints "order.total() = 129.90 (BigDecimal)" and the call site
 * }</pre>
 * <ul>
 *     <li>{@link com.nanolaba.logging.LOG} &mdash; the static entry point.</li>
 *     <li>{@link com.nanolaba.logging.ILogger} &mdash; the extension point; a lambda is enough.</li>
 *     <li>{@link com.nanolaba.logging.ILevelControl} &mdash; levels that change while the application runs, behind
 *     {@code LOG.setLevel} and {@code LOG.withLevel}.</li>
 *     <li>{@link com.nanolaba.logging.LogEntry} &mdash; one record and the message formatter.</li>
 *     <li>{@link com.nanolaba.logging.ConsoleLogger} (the default), {@link com.nanolaba.logging.JsonLogger},
 *     {@link com.nanolaba.logging.Slf4jLogger} &mdash; text, JSON lines and the bridge to SLF4J.</li>
 *     <li>{@link com.nanolaba.logging.MemoryLogger}, {@link com.nanolaba.logging.CompositeLogger} &mdash;
 *     capturing entries in tests and sending them to several loggers.</li>
 *     <li>{@link com.nanolaba.logging.DefaultLoggerCreator} &mdash; the startup choice of the logger and the
 *     settings: {@code nanolog.logger}, {@code nanolog.level}, {@code nanolog.strict} and others, from system
 *     properties, environment variables or {@code nanolog.properties}.</li>
 *     <li>{@link com.nanolaba.logging.SummaryListener} &mdash; prints the run summary when JUnit has run the tests;
 *     the JUnit Platform loads it by itself.</li>
 *     <li>{@link com.nanolaba.logging.NanologExtension} &mdash; a JUnit 5 extension that prints the log of a test only
 *     when the test fails.</li>
 * </ul>
 */
package com.nanolaba.logging;
