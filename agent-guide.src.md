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
description: Use when writing, reviewing or debugging Java code that logs through Nanolog (com.nanolaba:nanolog, the static com.nanolaba.logging.LOG class), when adding log calls to a project that depends on it, or when asserting log output in its tests. Covers the call shapes, the rules that differ from SLF4J, settings without code changes and capturing entries in tests.<!--skill-->
---<!--skill-->
<!--skill-->
# Nanolog

> Nanolog (`${coords}`) is a tiny logging facade for Java ${pom.properties.maven.compiler.release}+. Every call goes through the static methods of `com.nanolaba.logging.LOG`, and the source class is detected from the stack, so classes have no logger fields. It prints to the console by default, writes JSON lines or forwards to SLF4J, and has no runtime dependencies.<!--llms-->
Nanolog (`${coords}`) is a tiny logging facade for Java ${pom.properties.maven.compiler.release}+: every call goes through the static methods of `com.nanolaba.logging.LOG`, and the source class is detected from the stack, so classes have no logger fields. Follow this guide whenever you add or change log calls in a project that depends on it.<!--skill-->

The latest stable version is ${stableVersion}${widget:if(cond='endsWith(${devVersion}, -SNAPSHOT)', text="; this guide describes the development version ${devVersion}")}. Features marked *1.2+* or *1.3+* need that version or newer.

## Rules

1. Do not declare logger fields. `import com.nanolaba.logging.LOG;` and call `LOG.info(...)` directly.
2. Placeholders are `{}`, as in SLF4J. A missing argument leaves `{}` in the text, an extra one is dropped. `\{}` is a literal `{}`, `\\{}` a literal backslash followed by a placeholder.
3. Pass the exception first, `LOG.error(e, "Failed to load {}", id)`, or last, `LOG.error("Failed to load {}", id, e)` *1.2+*. Version 1.1 drops a trailing exception, so put it first there.
4. Build expensive messages lazily: `LOG.debug(() -> "Report: " + buildReport())` runs the lambda only when `DEBUG` is enabled.
5. In hot paths pass the class: `LOG.debug(MyClass.class, "...")`. Without it every call walks the stack, about 3 µs on JDK 17+ *1.3+* and 20 to 60 µs or more on Java 8, `LOG.isDebugEnabled()` included; only a level that is off for every class, such as `DEBUG` under `nanolog.level=INFO`, skips the walk *1.3+*.
6. Call `LOG.init(...)` once at application startup and never from library code: it replaces the logger for the whole JVM.
7. Logging never throws: a broken `toString()` becomes `[FAILED toString()]`, a lazy message whose lambda throws becomes `[FAILED supplier: <exception>]` *1.3+*. Do not wrap log calls in `try`/`catch`.
8. A custom `ILogger` that itself logs through `LOG` must pass its source class explicitly, because caller detection skips every `ILogger` class. If it throws, the call that logged does not fail: Nanolog prints one `[NANOLOG]` line per logger class and writes the entry to `System.err` instead *1.3+*.

## Call shapes

`info` stands for any of `trace`, `debug`, `info`, `warn`, `error`; every level has the same overloads.

```java
LOG.info("Application started");                    // any object as the message
LOG.info("Loaded {} items in {} ms", count, millis); // parameterized
LOG.info(() -> "Report: " + buildReport());          // lazy
LOG.info(e);                                         // stack trace without a message
LOG.info(e, "Failed to load {}", id);                // exception first
LOG.info("Failed to load {}", id, e);                // exception last (1.2+)
LOG.info(MyClass.class, "...");                      // any form above with an explicit source class
LOG.isInfoEnabled();                                 // level check, with or without a class
```

## Choosing the output

- Default: `ConsoleLogger`, one line per entry, `LEVEL dd.MM.yyyy HH:mm:ss [SourceClass] message`, `ERROR` to `System.err`, everything else to `System.out`. Every level is enabled.
- `-Dnanolog.logger=com.nanolaba.logging.JsonLogger` *1.2+*: one JSON object per line with `ts`, `level`, `logger`, `thread`, `message`, `template`, `args`, `caller` and `exception`. Add `-Dnanolog.console.stream=out` *1.3+* before piping into `jq`: by default `ERROR` lines go to `System.err` and miss the pipe.
- `-Dnanolog.logger=com.nanolaba.logging.Slf4jLogger`: forwards to SLF4J; add `slf4j-api` and a backend to the project, since Nanolog declares it `provided`. Logback and Log4j 2 report the real call site in `%file:%line` / `%l` *1.2+*.
- The same keys work in a `nanolog.properties` file on the classpath and, *1.2+*, as environment variables: `nanolog.logger` is `NANOLOG_LOGGER`. Precedence: `LOG.init(logger)`, `-D`, environment, file.

## Settings without code changes *1.2+*

Prefer these over editing code for one run: a temporary edit tends to get committed.

- `NANOLOG_LEVEL=INFO` or `-Dnanolog.level=INFO`: the lowest level printed (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF`).
- `-Dnanolog.level.com.acme.payments=DEBUG` or `NANOLOG_LEVELS=com.acme.payments=DEBUG,com.acme.db=TRACE`: the level of one package or class. It beats the global level, even one set in code.
- `-Dnanolog.console.showLocation=true`: adds the call site, `[PaymentService.charge(PaymentService.java:87)]`, to every line.
- `-Dnanolog.console.showThread=true` *1.3+*: adds the thread name, `[main]`, after the date, to tell the lines of a thread pool apart.
- `-Dnanolog.strict=true` in test runs: reports each call whose `{}` placeholders and arguments do not match, with its call site, on `System.err`.

## Changing levels at run time *1.3+*

`LOG.setLevel(LogEntryLevel.INFO)`, `LOG.setLevel("com.acme.payments", LogEntryLevel.DEBUG)`, `LOG.clearLevel(name)` and `LOG.resetLevels()` change the levels of `ConsoleLogger` and `JsonLogger` while the program runs, in every thread. A level may also be a string as in the settings, `OFF` included: `LOG.setLevel("com.acme.noisy", "OFF")`. In a test prefer `try (LOG.LevelOverride ignored = LOG.withLevel("com.acme", LogEntryLevel.TRACE)) { ... }`, which puts the previous level back. Like `LOG.init`, a change is JVM-wide.

## Asserting log output in tests *1.2+*

`MemoryLogger` captures entries; `close()` puts the previous logger back, also when the test fails. `LOG.init` is JVM-wide, so such tests must not run in parallel.

${widget:import(path='${examples}/CapturingLogs.java', region='capture', wrap='true')}

`MemoryLogger.installForwarding()` captures and still prints. `getEntries()`, `getEntries(level)`, `getMessages()`, `contains(level, text)` and `clear()` cover the usual assertions.

## Links

- [README](${repo}#readme): the full documentation, with the changelog
- [Javadoc](${javadoc}): every class and method of the latest release
- [Source](${repo}): one package of small classes, `src/main/java/com/nanolaba/logging`
