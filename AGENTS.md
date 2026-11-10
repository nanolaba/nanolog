# AGENTS.md

Guidance for coding agents (Claude Code, Codex, Copilot and others) working in this repository. `CLAUDE.md` only points here.

## Build & Test

Maven project targeting **Java 8** (`maven.compiler.release=8`). Do not introduce Java 9+ APIs in `src/main`. Tests may rely on a JDK (not a JRE): `LOGTest` compiles a class at runtime with `javax.tools.ToolProvider`.

- Full build: `mvn verify -Dgpg.skip=true` (the default `verify` tries to GPG-sign artifacts)
- Tests + mutation analysis: `mvn test`
- Single test class: `mvn test -Dtest=LOGTest`
- Single test method: `mvn test -Dtest=LOGTest#methodName`
- Compile only (also regenerates the README files): `mvn compile`
- Fast JUnit-only loop while developing: `mvn test-compile surefire:test -Dtest=LOGTest`

CI (`.github/workflows/ci.yml`) runs `mvn -Dgpg.skip=true -Dcheck=true verify` on JDK 8, 11, 17 and 21. Actions are pinned to commit SHAs and the token is read-only; keep it that way when editing the workflow.

### Pitest mutation coverage (important)

The `test` phase is wired to run **pitest-maven** with `mutationThreshold=100`. Any surviving mutant fails the build, not just failing unit tests. When changing production code:

- `mvn test` covers both JUnit and mutation analysis; run it before every commit.
- If a mutant survives, add a test that would catch the mutation rather than weakening the threshold. "Mutations with no coverage" means a line is never executed by tests: cover it.
- Pitest's default mutators do not remove `synchronized` blocks, so thread-safety fixes need a deterministic test of the contract instead (see `ConsoleLoggerTest.testDateFormatIsGuardedByItsMonitor`).

### README generation

`README.md` and `README.ru.md` are **generated** during the `compile` phase by `nrg-maven-plugin` 1.3 ([readme-generator](https://github.com/nanolaba/readme-generator)) from `README.src.md`. Never edit the generated files.

- Edit `README.src.md`, run `mvn compile`, review both outputs, commit all three files together.
- CI runs the plugin with `-Dcheck=true`: a committed README that differs from what the template generates fails the build. Local verification: `mvn -q compile -Dcheck=true`.
- Code samples are imported from `src/test/java/com/nanolaba/logging/examples` via `// nrg:begin:NAME` / `// nrg:end:NAME` regions and `${widget:import(... region='NAME', wrap='true')}`. Changing an example changes the README; keep the console output shown in the quick start in sync by hand. An import may sit on an indented line (inside a list item): every line of its output takes the indentation of the widget line.
- Versions come from `pom.xml` (`${pom.version}`, `${pom.properties.slf4j.version}`, ...). The only hand-maintained version is `<!--@stableVersion=...-->` at the top of the template: bump it after every release.
- Snapshot-only content (the "Using SNAPSHOT versions" section, the development entry of the changelog, the "version under development" clause) is guarded by `${widget:if(cond='endsWith(${devVersion}, -SNAPSHOT)')}` … `${widget:endIf}` or by the inline `${widget:if(cond='…', text='…')}`, so it disappears from the README of a release commit by itself.
- Collapsible content uses `${widget:details(summary='…')}` … `${widget:endDetails}`; the inner markdown and language markers keep working.
- The English and Russian quick-start examples are chosen with the per-language properties `quickStartFile.en` / `quickStartFile.ru`.

Template conventions that still apply with NRG 1.3:

- Keep code spans inside the language strings (`${en:'use `LOG`.', ru:'используйте `LOG`.'}`); placing a backticked term outside the construct produces a space before Russian punctuation.
- Every path the template names (`import`, `fileTree`, output files) must stay inside the repository: since 1.3 a template is confined to its project root, and an escaping path fails the build.
- Do not downgrade the plugin below 1.3: the template relies on fixes for readme-generator #55 (parentheses in `if` conditions), #56 (language markers on `import` lines broke the table of contents) and #57 (indented widgets). Plugins are resolved from Maven Central only; do not add a snapshot plugin repository.

After regenerating, grep the outputs for leftover `${` and compare the generated table of contents with `grep -n '^#' README.md`.

## Architecture

Nanolog is a tiny SLF4J-compatible logging facade: six source files, no runtime dependencies. The whole public API is static methods on one class; there are no per-class logger fields.

### The single entry point: `LOG` (`src/main/java/com/nanolaba/logging/LOG.java`)

All logging goes through static methods on `LOG` (`LOG.info(...)`, `LOG.debug(...)`, ...). Each level has the same set of overloads: explicit source `Class`, `Throwable`, `Supplier<Object>` for lazy messages, `String` + `Object...` for parameterized messages, and since 1.1 the combinations `(Throwable, String, Object...)` and `(Class, Throwable, String, Object...)`. All overloads funnel into the private `LOG.doLog(...)`: overloads with an explicit `Class` through the public `LOG.log(level, class, throwable, supplier, args...)`, the others through `logFromCaller(...)`, which passes the detected source class and caller frame. `doLog` checks `isEnabled` and hands a `LogEntry` to the configured `ILogger`. When adding an overload, add it to all five levels and extend the per-level tests in `LOGTest`, which assert `LogEntry.toString()`.

Overload resolution matters: the `Object` and `Supplier` forms win in the non-varargs phases, the varargs forms only apply when extra arguments are present. Do not add overloads that make existing calls ambiguous (`LOG.info(e, "text")` must keep resolving to `(Throwable, Object)`).

### Caller auto-detection: `LOG.findCaller()`

When the caller does not pass an explicit `Class`, Nanolog identifies the source class by throwing a `Throwable` and walking its stack trace, skipping any frame whose top-level enclosing class is `LOG`, `LogEntry`, or an `ILogger` implementation. The first remaining frame is kept as well and reaches loggers as `LogEntry.getCaller()` (the call site); the source class is that frame's top-level class. `getCaller()` is `null` for calls with an explicit class, which skip the walk. **This filter is load-bearing**: any new intermediary class between user code and the actual logger must also be skipped here, otherwise it is misreported as the source class. The same holds if you ever add another public façade alongside `LOG`.

Frame classes are resolved by `LOG.resolveClass(...)`: the thread's context class loader first, then the loader of `LOG`, without initialization. A frame neither loader can see is skipped instead of aborting the call (needed in application servers and plugin frameworks where Nanolog sits in a parent loader). `LOGTest` covers this by compiling a caller at runtime into an isolated `URLClassLoader`.

### Extension point: `ILogger`

Implementations: `ConsoleLogger` (default) and `Slf4jLogger` (bridge to SLF4J). Custom loggers can be a lambda (`LOG.init(entry -> ...)`) because `ILogger` is effectively single-abstract-method. `isEnabled(level, sourceClass)` defaults to `true`; override it for level filtering. `LOG.log(...)` short-circuits on `isEnabled` before constructing the `LogEntry`, and `Supplier<Object>` messages are only resolved past that check, so lazy-message semantics depend on this order (`NoUnnecessaryCallsTest`, `MemoizingMessageTest`).

- `ConsoleLogger` writes `ERROR` to `System.err`, everything else to `System.out`. Its single `SimpleDateFormat` is shared by all threads, so `writeDate` reads it once and formats inside `synchronized (format)`; keep that when touching the date code.
- `Slf4jLogger` caches one `org.slf4j.Logger` per source class in a `ConcurrentHashMap`. A `null` source class maps to the SLF4J `ROOT` logger (`ConcurrentHashMap` rejects `null` keys). An entry without a message is passed as an empty string, not `"null"`. The formatted message is passed as a plain string, so SLF4J never sees the `{}` arguments; markers are unsupported.

### Bootstrap: `DefaultLoggerCreator`

On `LOG` class init (and on any `LOG.init()` with no args), a default `ILogger` is resolved as follows:

1. Load `nanolog.properties` from the classpath (or the resource named by system property `nanolog.config`) through the thread's context class loader, falling back to the class's own loader when the context loader is `null`. Copy **only keys with the `nanolog.` prefix** (`DefaultLoggerCreator.PROPERTY_PREFIX`) into `System` properties, and only where not already set, so CLI `-D` overrides win over the file and the file cannot alter unrelated JVM settings.
2. If system property `nanolog.logger` is set, `Class.forName(...).getConstructor().newInstance()` that class.
3. Otherwise, return `new ConsoleLogger()`.

Failures in step 2, and a missing file named by `nanolog.config`, are printed to `System.err` with a `[NANOLOG]` prefix and fall through to `ConsoleLogger`. An `IOException` while reading the file is rethrown as `RuntimeException` and would break `LOG`'s static initializer.

Do not add a `nanolog.properties` to `src/test/resources`: it would be picked up by every test's `LOG` initialization. Tests use `nanolog.config` pointing at `test.properties` / `mixed.properties` instead.

### Message formatting: `LogEntry.formatMessage()`

Parameterized messages use `{}` placeholders (SLF4J-style). Escaping is non-trivial and has dedicated tests in `LogEntryTest`:

- `\{}` is a literal `{}`; the argument is **not consumed** (the consumer loop decrements `argIndex`).
- `\\{}` is a literal `\` followed by a substitution (both escapes consumed, the argument is substituted).
- Missing arguments leave `{}` in place; extra arguments are ignored.

Primitive arrays are rendered via `Arrays.toString`, object arrays via `Arrays.deepToString`; if `deepToString` throws because of an element's `toString()`, `arrayToString` falls back to rendering element by element so only the broken element becomes `"[FAILED toString()]"`. Every `toString()` call goes through `safeToString`, including those in `LogEntry.toString()`. Preserve this "logging never throws" guarantee when changing formatting code; the README promises it.

## Tests

- `AbstractLoggerTest` redirects `System.out` / `System.err` into buffers; `ConsoleLoggerTest` and `Slf4jLoggerTest` assert exact output lines (slf4j-simple is configured by `src/test/resources/simplelogger.properties`).
- `LOGTest` drives every overload and asserts the `LogEntry.toString()` representation; it also contains the class-loader scenarios.
- `DefaultLoggerCreatorTest` manipulates system properties and the context class loader and must clean up in `@AfterEach`.
- Classes under `src/test/java/com/nanolaba/logging/examples` are not tests: they are the README's code samples and only need to compile.

## Dependencies

- `slf4j-api` is **`provided`**: required only if the consumer uses `Slf4jLogger`. Do not promote it to `compile` scope; that would force SLF4J onto every downstream user and defeat the "zero dependencies" claim in the README.
- `junit-jupiter`, `slf4j-simple`, `commons-lang3`, `pitest-junit5-plugin` are all test-scope.
- The jar manifest declares `Automatic-Module-Name: com.nanolaba.nanolog` (configured on `maven-jar-plugin`); keep it stable, modular applications depend on it.

## Release build

The default build signs artifacts with GPG (`maven-gpg-plugin` 3.x, through gpg-agent) and publishes via `central-publishing-maven-plugin` with `autoPublish=true`. For local development use `mvn test` / `mvn compile` / `mvn verify -Dgpg.skip=true`; avoid `mvn deploy` unless you are actually cutting a release. Plugins are resolved from Maven Central only; do not reintroduce snapshot plugin repositories.

Release checklist:

1. Set the release version in `pom.xml`, update `<!--@stableVersion=...-->` and the changelog section in `README.src.md`, run `mvn compile`, commit the regenerated README files together with the sources.
2. `mvn clean deploy` (signs the artifacts and publishes them to Maven Central), then tag the release commit (`git tag -a vX.Y -m "Nanolog X.Y"`) and push the branch and the tag.
3. **Create a GitHub release for the tag.** Every published version must have one: `gh release create vX.Y --verify-tag --title "vX.Y" --notes-file notes.md`. The notes are the `### X.Y` changelog section from `README.src.md` (English), grouped into "New" / "Fixes" / "Documentation and build", followed by the Maven coordinates and a `compare/vPREV...vX.Y` link; the `v1.1` release is the reference layout. Do not publish it as a draft or pre-release.
4. Bump `pom.xml` to the next `-SNAPSHOT`, run `mvn compile`, commit the regenerated README files.

## Conventions

- Commit messages: English, imperative subject line, body explains the why.
- Keep the public API additive; this library is consumed from Maven Central.
- Every production change ships with tests that kill the corresponding pitest mutants and, where user-visible, with a README change in `README.src.md`.
