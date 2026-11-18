<!--@nrg.languages=llms,skill-->
<!--@nrg.defaultLanguage=llms-->
<!--@nrg.fileNamePattern.llms=llms.txt-->
<!--@nrg.fileNamePattern.skill=skills/nanolog/SKILL.md-->
<!--@nrg.noHeader=true-->
<!--@stableVersion=${pom.properties.nanolog.stableVersion}-->
<!--@devVersion=${pom.version}-->
<!--@coords=${pom.groupId}:${pom.artifactId}-->
<!--@repo=${pom.scm.url}-->
<!--@javadoc=https://javadoc.io/doc/${pom.groupId}/${pom.artifactId}-->
<!--@examples=src/test/java/com/nanolaba/logging/examples-->
Maintainer note: this template generates llms.txt and skills/nanolog/SKILL.md during mvn compile. Edit it, never the outputs.<!--nrg.ignore-->
---<!--skill-->
name: nanolog<!--skill-->
description: Use when writing, reviewing or debugging Java code that logs through Nanolog (com.nanolaba:nanolog, the static com.nanolaba.logging.LOG class), when adding log calls to a project that depends on it, when reading its output as a coding agent, or when asserting log output in its tests. Covers the call shapes, the rules that differ from SLF4J, print debugging with LOG.dbg, agent mode, the run summary and the run log, settings without code changes and capturing entries in tests.<!--skill-->
---<!--skill-->
<!--skill-->
# Nanolog

> Nanolog (`${coords}`) is a zero-dependency logging library for Java ${pom.properties.maven.compiler.release}+. Every call goes through the static methods of `com.nanolaba.logging.LOG`, and the source class is detected from the stack, so classes have no logger fields. It prints to the console by default, writes JSON lines, captures entries in tests or forwards to SLF4J, and `LOG.dbg(expression)` prints an expression with its value and call site for print debugging. `LOG.unit(name)` writes one line per request or job, the wide events of canonical log lines. When a coding agent runs the program, the console keeps `WARN`, `ERROR` and `LOG.dbg`, and the full log goes to `target/nanolog/latest.log`. Not related to NanoLog, the C++ logger.<!--llms-->
Nanolog (`${coords}`) is a tiny logging facade for Java ${pom.properties.maven.compiler.release}+: every call goes through the static methods of `com.nanolaba.logging.LOG`, and the source class is detected from the stack, so classes have no logger fields. Follow this guide whenever you add or change log calls in a project that depends on it.<!--skill-->

The latest stable version is ${stableVersion}${widget:if(cond='endsWith(${devVersion}, -SNAPSHOT)', text="; this guide describes the development version ${devVersion}")}. Features marked *1.2+*, *1.3+* or *1.4+* need that version or newer.

## Rules

1. Do not declare logger fields. `import com.nanolaba.logging.LOG;` and call `LOG.info(...)` directly.
2. Placeholders are `{}`, as in SLF4J. A missing argument leaves `{}` in the text, an extra one is dropped. `\{}` is a literal `{}`, `\\{}` a literal backslash followed by a placeholder. Keep the template a constant and pass values through `{}`: `LOG.info("User {} logged in from {}", id, ip)`, not `LOG.info("User " + id + " logged in from {}", ip)`, so the `template` of `JsonLogger` and `assertTemplateLogged` group the lines.
3. Pass the exception first, `LOG.error(e, "Failed to load {}", id)`, or last, `LOG.error("Failed to load {}", id, e)` *1.2+*. Version 1.1 drops a trailing exception, so put it first there. Do not pass `e.getMessage()` as an argument next to `e`: the stack trace prints it already.
4. Build expensive messages lazily: `LOG.debug(() -> "Report: " + buildReport())` runs the lambda only when `DEBUG` is enabled. For one expensive argument keep the template: `LOG.debug("Report: {}", LOG.lazy(() -> buildReport()))` *1.4+*, not an `if (LOG.isDebugEnabled())` around the call.
5. In hot paths pass the class: `LOG.debug(MyClass.class, "...")`. Without it every call walks the stack, about 3 µs on JDK 17+ *1.3+* and 5 to 8 µs on Java 8 *1.4+* (20 to 60 µs or more before), `LOG.isDebugEnabled()` included; only a level that is off for every class, such as `DEBUG` under `nanolog.level=INFO`, skips the walk *1.3+*.
6. Call `LOG.init(...)` once at application startup and never from library code: it replaces the logger for the whole JVM.
7. Logging never throws: a broken `toString()` becomes `[FAILED toString()]`, a lazy message whose lambda throws becomes `[FAILED supplier: <exception>]` *1.3+*. Do not wrap log calls in `try`/`catch`.
8. A custom `ILogger` that itself logs through `LOG` must pass its source class explicitly, because caller detection skips every `ILogger` class. If it throws, the call that logged does not fail: Nanolog prints one `[NANOLOG]` line per logger class and writes the entry to `System.err` instead *1.3+*.
9. In a loop, a retry or a poll, log through `LOG.once()` or `LOG.atMostEvery(...)` *1.3+*: a line repeated thousands of times crowds the context window and pushes out the one that matters. The next line that gets through says how many were skipped, `[skipped 241]`.
10. For temporary debug output use `LOG.dbg(...)` *1.4+*, never `System.out.println`, and remove every `LOG.dbg` call before you finish; `NanologChecks.assertNoDbgCalls("src/main/java")` in a test lists the ones left.
11. Tag the lines of one request, job or test with `try (LOG.Context c = LOG.context("requestId", id)) { ... }` *1.4+* instead of repeating the id in every message: each line inside ends with `requestId=r-17`, so `grep requestId=r-17` finds the whole path, and `JsonLogger` writes `"context":{"requestId":"r-17"}`. A task for an executor keeps the context only through `executor.submit(LOG.wrap(task))`. Trace IDs of OpenTelemetry or another tracer go in once at startup with `LOG.addContextProvider(() -> ids)` *1.4+*, not by hand in every request.
12. Name the values a script or you will select on with `LOG.kv(key, value)` *1.4+*: `LOG.info("Payment accepted", LOG.kv("orderId", id))` prints `Payment accepted orderId=A-17`, `JsonLogger` writes `"fields":{"orderId":"A-17"}` for `jq 'select(.fields.orderId=="A-17")'`, and in a `{}` a field prints its value. Put an exception after the fields.
13. Wrap a request, a job or a step of an agent loop in `try (LOG.Unit u = LOG.unit("checkout")) { u.put("orderId", id); ... }` *1.4+*, or `LOG.unit("import", () -> ...)`: when it ends it writes one line, `checkout ok 182 ms orderId=A-17 warn=1`, with the counts of the warnings and errors logged inside it; a failed unit is an `ERROR` line with `error.type`, `error.message` and `error.key`. An exception that leaves the try block is not seen: call `u.fail(e)` or use the lambda form.
14. Wrap payloads, such as prompts, model responses, request bodies and documents, in `LOG.content(...)` *1.4+*: `LOG.debug("Prompt: {}", LOG.content(prompt))` writes `[content 1834 chars #3f2a9c1b]`, and equal payloads share the hash. `-Dnanolog.content=show` or `truncate:200` writes the text, escaped, while you debug. The logs you read are sent to your model provider, so never log a payload raw. For Spring AI and LangChain4j the README has an advisor and a listener that log one line per model call with `gen_ai.*` fields.
15. For best-effort work, such as closing a resource or deleting a temporary file, write `LOG.catching(() -> file.delete())` *1.4+* instead of `try`/`catch` with `LOG.error`: it logs at `ERROR` with the stack trace and carries on. Never use it where the caller must see the failure. In `main`, `LOG.installUncaughtExceptionHandler()` *1.4+* puts the exceptions that kill threads into the log, attributed to the code that threw.
16. Never log a secret: wrap it in `LOG.secret(token)` *1.4+*, which every logger writes as `***`. Fields and context values named `password`, `token`, `secret`, `apikey`, `credential` or `authorization`, or with such a word in the key (`db.password`, `userToken`), are masked anyway (`nanolog.redact`).
17. If the project logs through a helper of its own, such as `Audit.record(...)`, name it in `nanolog.caller.skip=com.acme.Audit` *1.4+*, or call `LOG.skipCaller(Audit.class)` once in a library; otherwise every line it logs names the helper as its source and call site.

## Call shapes

`info` stands for any of `trace`, `debug`, `info`, `warn`, `error`; every level has the same overloads.

```java
LOG.info("Application started");                    // any object as the message
LOG.info("Loaded {} items in {} ms", count, millis); // parameterized
LOG.info(() -> "Report: " + buildReport());          // lazy
LOG.info("Report: {}", LOG.lazy(this::buildReport)); // one lazy argument, the template stays (1.4+)
LOG.info(e);                                         // stack trace without a message
LOG.info(e, "Failed to load {}", id);                // exception first
LOG.info("Failed to load {}", id, e);                // exception last (1.2+)
LOG.info(MyClass.class, "...");                      // any form above with an explicit source class
LOG.isInfoEnabled();                                 // level check, with or without a class
LOG.once().warn("Option {} is deprecated", key);     // only the first time this line runs (1.3+)
LOG.tag("sql").debug("{} rows", rows);               // an entry of a concern, levels per tag in nanolog.tags (1.4+)
LOG.atMostEvery(10, TimeUnit.SECONDS).warn("...");   // at most once per period, then "[skipped N]" (1.3+)
LOG.catching(() -> connection.close());              // ERROR with the stack trace, then carry on (1.4+)
int n = LOG.catching(() -> Integer.parseInt(s), -1); // a default value when it fails (1.4+)
LOG.info("Payment accepted", LOG.kv("orderId", id)); // a field: "Payment accepted orderId=A-17", JSON "fields" (1.4+)
try (LOG.Timer t = LOG.time("Load users")) { ... }  // "Load users took 123 ms" when the block ends (1.4+)
try (LOG.Unit u = LOG.unit("checkout")) {          // one line when it ends: "checkout ok 182 ms warn=1" (1.4+)
    u.put("orderId", id);
}
try (LOG.Context c = LOG.context("requestId", id)) { // every line of the thread inside ends with requestId=... (1.4+)
    executor.submit(LOG.wrap(task));                 // the task logs with the same context in another thread (1.4+)
}
```

## Print debugging *1.4+*

`LOG.dbg(expression)` prints the expression as written in the source, its value and type, and the call site, and returns the value, so it wraps an expression in place:

```java
BigDecimal total = LOG.dbg(order.total());
// DBG 17.11.2026 10:15:02 [OrderService.checkout(OrderService.java:87)] order.total() = 129.90 (BigDecimal)
LOG.dbg(user, cart.size());   // user = User{id=17}, cart.size() = 3 (Integer)
LOG.dbg();                    // reached
```

The text is read from the source file, found from the class location (`target/classes` leads to `src/main/java`) or `-Dnanolog.sources=dir1,dir2`. Without it, or with two `dbg` calls on one line, only the values are printed. It is a `DEBUG` entry, so `NANOLOG_LEVEL=INFO` silences it, except in agent mode; `ConsoleLogger` marks it `DBG`, `JsonLogger` adds `"dbg":true`. When the JVM exits, one `[NANOLOG]` line on `System.err` lists the `dbg` call sites that printed.

An object without a `toString()` of its own prints field by field, `Invoice{id=17, lines=[Line{sku="A-1", qty=2}]}`, instead of `Invoice@2f92e0f4` *1.4+*. Classes of the JDK are walked through their public API, never by reflection, so no `--add-opens` is needed. Fields named like secrets (`password`, `token`, `apiKey`...) print `***`, proxies print only their class name, and cycles, depth, items and string length are cut with a note of what was left out. `-Dnanolog.dump=auto` applies the same to `{}` arguments of every log call; it is off by default.

Remove every `LOG.dbg` call before you finish. This test fails while one is left in the sources, with the file, line and code of each; it reads `.java` and `.kt` files as text, so comments and strings do not count *1.4+*:

${widget:import(path='${examples}/NoDbgCallsLeftTest.java', region='test', wrap='true')}

## Agent mode *1.4+*

When the environment names a coding agent (`AI_AGENT`, `AGENT`, `CLAUDECODE`, `GEMINI_CLI`, `CODEX_SANDBOX`, `CODEX_CI`, `CURSOR_AGENT`, `VSCODE_AGENT`, `CLINE_ACTIVE`), the console of the default logger shows only `WARN`, `ERROR` and `LOG.dbg`, one line per entry (line breaks written as `\n`, the stack trace included), without dates and with short stack traces, and the full log goes to the run log. One `[NANOLOG]` line on `System.err` says so:

```
[NANOLOG] Agent mode (AI_AGENT=claude-code): the console shows WARN, ERROR and every LOG.dbg; the full log is in /app/target/nanolog/latest.log, WARN and ERROR also in /app/target/nanolog/latest.errors.log; nanolog.agent=off turns it off
```

- Read `latest.log` (every level, thread, call site, whole stack traces) instead of running again with another level or filter.
- The run ends with `[NANOLOG] Run summary: ...`, printed last: the counts by level, each kind of `WARN` and `ERROR` with its call site and count, the trace of the first error, the `LOG.dbg` calls that ran and the path of `latest.log`. Read it first; it survives `| tail`. `NANOLOG_SUMMARY=on` prints it outside agent mode too, `errors` only after a `WARN` or `ERROR`, `off` never.
- `LOG.dbg` prints whatever the levels. `NANOLOG_LEVEL=DEBUG` brings more to the console.
- `NANOLOG_AGENT=off` turns agent mode off; `NANOLOG_AGENT=on` turns it on where the variables do not reach (`sudo`, `docker run`, `ssh`).
- A test that reads the console output of the default logger sees less under an agent: capture entries with `MemoryLogger`, or set `NANOLOG_AGENT=off` for the test JVM.

## Choosing the output

- Default: `ConsoleLogger`, one line per entry, `LEVEL dd.MM.yyyy HH:mm:ss [SourceClass] message`, `ERROR` to `System.err`, everything else to `System.out`. Every level is enabled.
- `-Dnanolog.logger=com.nanolaba.logging.JsonLogger` *1.2+*: one JSON object per line with `ts`, `level`, `logger`, `thread`, `message`, `template`, `args`, `fields` and `context` *1.4+*, `caller` and `exception`. Add `-Dnanolog.console.stream=out` *1.3+* before piping into `jq`: by default `ERROR` lines go to `System.err` and miss the pipe.
- `-Dnanolog.logger=com.nanolaba.logging.Slf4jLogger`: forwards to SLF4J; add `slf4j-api` and a backend to the project, since Nanolog declares it `provided`. Logback and Log4j 2 report the real call site in `%file:%line` / `%l` *1.2+*.
- The same keys work in a `nanolog.properties` file on the classpath and, *1.2+*, as environment variables: `nanolog.logger` is `NANOLOG_LOGGER`. Precedence: `LOG.init(logger)`, `-D`, environment, file.

## Settings without code changes *1.2+*

Prefer these over editing code for one run: a temporary edit tends to get committed.

- `NANOLOG_LEVEL=INFO` or `-Dnanolog.level=INFO`: the lowest level printed (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF`).
- `-Dnanolog.level.com.acme.payments=DEBUG` or `NANOLOG_LEVELS=com.acme.payments=DEBUG,com.acme.db=TRACE`: the level of one package or class. It beats the global level, even one set in code.
- `-Dnanolog.console.showLocation=true`: adds the call site, `[PaymentService.charge(PaymentService.java:87)]`, to every line.
- `-Dnanolog.console.showSourceLine=true` *1.4+*: under each entry the source line of the call, `    > LOG.warn("Charge of {} failed", amount);`, and under the first three frames of a stack trace outside the JDK, so you see the code without opening the file. It needs the sources, so it works in development and stays silent when they are missing or stale.
- `-Dnanolog.console.showThread=true` *1.3+*: adds the thread name, `[main]`, after the date, to tell the lines of a thread pool apart.
- `-Dnanolog.exception.exclude=org.junit,sun.reflect,jdk.internal.reflect,java.lang.reflect` *1.3+*: collapses runner and reflection frames of stack traces into `... N frames excluded`; `-Dnanolog.exception.maxFrames=40` caps the frames of each throwable. Both keep the trace of a failing test short.
- `-Dnanolog.dump=auto` *1.4+*: `{}` arguments whose class has no `toString()` of its own print field by field, as in `LOG.dbg`, instead of `Order@2f92e0f4`; values with a `toString()` print as before.
- `-Dnanolog.dbg=off` *1.4+* silences every `LOG.dbg` call, as in production; `-Dnanolog.dbg=fail` still prints them and reports each one, so `MemoryLogger.assertNoDiagnostics()` fails.
- `-Dnanolog.console.multiline=escape` *1.4+*: every entry on one physical line, line breaks written as `\n`, so `grep` and `tail -n` count entries; `indent` starts further lines with a tab instead. Both escape terminal control characters.
- `-Dnanolog.buffer=DEBUG` *1.4+*: `DEBUG` and `TRACE` entries are held back and written only before an `ERROR`, or after the line of a failed `LOG.unit`, under `--- N earlier DEBUG entries ---`, so a run that succeeds stays short and a failure comes with its context.
- `nanolog.console.repeats=collapse` *1.4+*, the default in agent mode: a line repeated by a loop or a retry is written once, then as `... (repeated 41 more times in 20.3 s)`; the run log keeps every one. `-Dnanolog.console.repeats=keep` writes each.
- `-Dnanolog.console.color=never` *1.4+*: no colour codes in the console. `auto`, the default, colours the level word only on a terminal and never in a pipe or a file, and agent mode turns colours off, so set it only when a terminal you read through shows escape codes.
- `-Dnanolog.run=on` *1.4+*, the default in agent mode: every entry also goes to `target/nanolog/latest.log` (Gradle: `build/nanolog`, otherwise `.nanolog`; the absolute path is printed at startup), and each `WARN` and `ERROR` as one line to `latest.errors.log`. Read that file instead of running again to filter the output, and `tail -f` it for a server in the background. `runs/` keeps the last 10 runs; `-Dnanolog.run.format=json` writes `JsonLogger` lines for `jq`.
- A mistyped key is reported when `LOG` initialises *1.4+*: `[NANOLOG] Unknown setting "nanolog.levle" (system property); did you mean "nanolog.level"?`. Read the `[NANOLOG]` lines on `System.err` before trusting the output. Per-package levels from the environment go into `NANOLOG_LEVELS`; `NANOLOG_LEVEL_COM_ACME` does nothing.
- `[NANOLOG]` lines are Nanolog's own: notices (agent mode, the run log, the run summary) and diagnostics, which name what is wrong and where it was set, such as `Invalid value "LOUD" of "nanolog.level": ...; set as a system property`. Each diagnostic is printed once per JVM; `-Dnanolog.diagnostics=all` *1.4+* prints every one.
- A `nanolog.properties` in `src/test/resources` replaces the main one in tests, as a whole file, not merged; to change one setting for the tests use `-D` (Surefire `systemPropertyVariables`) instead.
- `-Dnanolog.strict=true` in test runs: reports each call whose `{}` placeholders and arguments do not match, with its call site, on `System.err`; *1.4+* also an argument that repeats the message of the exception and a call site seen with two templates, a template built from data. *1.3+*: `MemoryLogger.assertNoDiagnostics()` fails the test on them, even on a finding an earlier test met first.

## Changing levels at run time *1.3+*

`LOG.setLevel(LogEntryLevel.INFO)`, `LOG.setLevel("com.acme.payments", LogEntryLevel.DEBUG)`, `LOG.clearLevel(name)` and `LOG.resetLevels()` change the levels of `ConsoleLogger` and `JsonLogger` while the program runs, in every thread. A level may also be a string as in the settings, `OFF` included: `LOG.setLevel("com.acme.noisy", "OFF")`. In a test prefer `try (LOG.LevelOverride ignored = LOG.withLevel("com.acme", LogEntryLevel.TRACE)) { ... }`, which puts the previous level back. Like `LOG.init`, a change is JVM-wide.

## Asserting log output in tests *1.2+*

`MemoryLogger` captures entries; `close()` puts the previous logger back, also when the test fails. `LOG.init` is JVM-wide, so such tests must not run in parallel.

${widget:import(path='${examples}/CapturingLogs.java', region='capture', wrap='true')}

*1.4+*: `MemoryLogger.capture()` captures the entries of the current thread only, every level, and keeps them from the installed logger; prefer it, it is safe when tests run in parallel. `captureForwarding()` also passes them on. Entries logged from other threads, a pool included, are not captured.

*1.4+*: `@ExtendWith(NanologExtension.class)` on a test class prints the log of a test only when it fails, between `--- log of Class.method ---` and `--- end of the log of Class.method ---`; passing tests stay quiet. When a test fails, read that block first. `-Dnanolog.junit.output=all` prints every log as it is written.

`MemoryLogger.installForwarding()` captures and still prints. `getEntries()`, `getEntries(level)`, `getMessages()`, `contains(level, text)` and `clear()` cover the usual assertions. *1.3+*: `assertNoneAtOrAbove(LogEntryLevel.WARN)`, `assertLogged(level, text)` and `assertTemplateLogged(level, "Retry {} of {}")` throw an `AssertionError` that lists every captured entry with its call site and stack trace, so a failing test shows the log without a rerun; `getEntries(SomeClass.class)` selects one source class.

## Links

- [README](${repo}#readme): the full documentation, with the changelog
- [Javadoc](${javadoc}): every class and method of the latest release
- [Source](${repo}): one package of small classes, `src/main/java/com/nanolaba/logging`
