<!--@nrg.languages=en,ru-->
<!--@nrg.defaultLanguage=en-->

<!--@name=**Nanolog**-->
<!--@stableVersion=${pom.properties.nanolog.stableVersion}-->
<!--@devVersion=${pom.version}-->
<!--@logbackVersion=1.5.38-->
<!--@coords=${pom.groupId}:${pom.artifactId}-->
<!--@repo=${pom.scm.url}-->
<!--@javadoc=https://javadoc.io/doc/${pom.groupId}/${pom.artifactId}-->
<!--@examples=src/test/java/com/nanolaba/logging/examples-->
<!--@quickStartFile.en=QuickStart.java-->
<!--@quickStartFile.ru=QuickStartRu.java-->
Maintainer note: the stable version is the nanolog.stableVersion property in pom.xml, bump it after every release. The development version is the project version.<!--nrg.ignore-->

${widget:languages}

${widget:badge(type='github-workflow', repo='nanolaba/nanolog', workflow='ci.yml', name='CI', alt='CI build status')}
${widget:badge(type='maven-central', coordinates='${coords}')}
${widget:badge(type='custom', label='javadoc', message='${stableVersion}', color='blue', url='${javadoc}/${stableVersion}', alt='API documentation on javadoc.io')}
${widget:badge(type='custom', label='java', message='${pom.properties.maven.compiler.release}+', color='blue', url='https://adoptium.net/', alt='Java 8 or newer')}
${widget:badge(type='license', value='Apache-2.0', url='https://www.apache.org/licenses/LICENSE-2.0')}
${widget:badge(type='github-stars', repo='nanolaba/nanolog')}

# Nanolaba logger (Nanolog)

${name} — крошечный логгер для Java 8+: все вызовы идут через статические методы<!--ru-->
класса `LOG`, класс-источник определяется автоматически, поэтому поле логгера<!--ru-->
в каждом классе больше не нужно. Работает сам по себе через `ConsoleLogger`<!--ru-->
или поверх **SLF4J**, а значит и поверх Logback, Log4j 2, JBoss Logging и других.<!--ru-->
${name} is a tiny logging facade for Java 8+: every call goes through the static<!--en-->
methods of `LOG`, the source class is detected automatically, so no class needs<!--en-->
a logger field. It works on its own through `ConsoleLogger` or on top of<!--en-->
**SLF4J**, and therefore on top of Logback, Log4j 2, JBoss Logging and others.<!--en-->

## ${en:'Why Nanolog', ru:'Зачем нужен Nanolog'}

- **${en:'No logger fields.', ru:'Нет полей логгера.'}** `LOG.info("...")` ${en:'works from any class; the caller is found by walking the stack', ru:'работает из любого класса, вызывающий определяется по стеку'}.
- **${en:'Lazy messages.', ru:'Ленивые сообщения.'}** `LOG.debug(() -> ...)` ${en:'is evaluated only when the level is enabled', ru:'вычисляется только при включённом уровне'}.
- **${en:'SLF4J-style placeholders.', ru:'Подстановка в стиле SLF4J.'}** `{}` ${en:'with escaping, array rendering and sane handling of missing or extra arguments', ru:'с экранированием, выводом массивов и разумной обработкой лишних и недостающих аргументов'}.
- **${en:'Logging never throws.', ru:'Логирование никогда не падает.'}** ${en:'A broken', ru:'Сломанный'} `toString()` ${en:'becomes', ru:'превращается в'} `[FAILED toString()]` ${en:'instead of an exception in your code', ru:'вместо исключения в вашем коде'}.
- **${en:'Zero dependencies.', ru:'Ноль зависимостей.'}** ${en:'Nine classes, no transitive libraries;', ru:'Девять классов, никаких транзитивных библиотек;'} `slf4j-api` ${en:'is needed only if you use', ru:'нужен, только если вы используете'} `Slf4jLogger`.
- **${en:'Pluggable.', ru:'Расширяемый.'}** ${en:'A custom backend is one lambda or one class implementing', ru:'Свой бэкенд — это одна лямбда или один класс, реализующий'} `ILogger`.
- **${en:'Fits the agent loop.', ru:'Удобен агентам.'}** ${en:'One line per log call, output from the first run, an API that fits in one context window; see', ru:'Одна строка на вызов, вывод с первого запуска, API целиком помещается в контекстное окно; см.'} [${en:'Agent-driven development', ru:'Агентная разработка'}](#${en:'agent-driven-development', ru:'агентная-разработка'}).

${en:'The whole library:', ru:'Вся библиотека целиком:'}

${widget:fileTree(path='src/main/java/com/nanolaba/logging', depth='1')}

- `LOG` — ${en:'the static entry point with all the', ru:'статическая точка входа со всеми'} `trace` / `debug` / `info` / `warn` / `error` ${en:'overloads', ru:'перегрузками'}.
- `ILogger` — ${en:'the extension point: one abstract method plus an optional level filter', ru:'точка расширения: один абстрактный метод и необязательный фильтр уровней'}.
- `LogEntry` — ${en:'one log record and the message formatter', ru:'одна запись лога и форматирование сообщения'}.
- `ConsoleLogger`, `JsonLogger`, `Slf4jLogger` — ${en:'the bundled implementations: text, JSON lines and the bridge to SLF4J', ru:'встроенные реализации: текст, JSON-строки и мост к SLF4J'}.
- `MemoryLogger`, `CompositeLogger` — ${en:'capturing entries in tests and sending them to several loggers', ru:'перехват записей в тестах и отправка записей в несколько логгеров'}.
- `DefaultLoggerCreator` — ${en:'picks the logger at startup from a system property or a properties file', ru:'выбирает логгер при старте по системному свойству или файлу настроек'}.

${en:'The latest stable version is', ru:'Последняя стабильная версия —'} **${stableVersion}**${widget:if(cond='endsWith(${devVersion}, -SNAPSHOT)', text="; ${en:'the version under development is', ru:'версия в разработке —'} **${pom.version}** (${en:'see', ru:'см.'} [Changelog](#changelog))")}.

${widget:tableOfContents(title = "${en:'Table of contents', ru:'Содержание'}", ordered = "true", numbering-style = "dotted", max-depth = "3")}

## ${en:'Installation', ru:'Установка'}

**Maven (pom.xml)**

```xml
<dependency>
    <groupId>${pom.groupId}</groupId>
    <artifactId>${pom.artifactId}</artifactId>
    <version>${stableVersion}</version>
</dependency>
```

**Gradle (build.gradle)**

```groovy
dependencies {
    implementation '${coords}:${stableVersion}'
}
```

${widget:details(summary="${en:'Manual download', ru:'Скачивание вручную'}")}
Get the JAR from [Maven Central](https://repo1.maven.org/maven2/com/nanolaba/nanolog/${stableVersion}) and add it to the classpath.<!--en-->
Скачайте JAR из [Maven Central](https://repo1.maven.org/maven2/com/nanolaba/nanolog/${stableVersion}) и добавьте его в classpath.<!--ru-->
${widget:endDetails}

${en:'Requirements: Java', ru:'Требования: Java'} ${pom.properties.maven.compiler.release}+. ${en:'The jar declares', ru:'В манифесте jar объявлено'} `Automatic-Module-Name: com.nanolaba.nanolog` ${en:'for modular applications', ru:'для модульных приложений'}.

## ${en:'Quick start', ru:'Быстрый старт'}

${en:'No setup is needed:', ru:'Настройка не нужна:'} `ConsoleLogger` ${en:'is active by default and prints every level, including', ru:'включён по умолчанию и печатает все уровни, включая'} `TRACE`.

${widget:import(path='${examples}/${quickStartFile}', region='quickstart', wrap='true')}

${en:'Console output (', ru:'Вывод в консоль ('}`ERROR` ${en:'goes to', ru:'уходит в'} `System.err`, ${en:'everything else to', ru:'всё остальное — в'} `System.out`):

```
INFO 10.11.2026 21:30:00 [QuickStart] Application started<!--en-->
DEBUG 10.11.2026 21:30:00 [QuickStart] Loaded 42 items in 17 ms<!--en-->
DEBUG 10.11.2026 21:30:00 [QuickStart] Expensive report: ...<!--en-->
WARN 10.11.2026 21:30:00 [QuickStart] Disk space is low<!--en-->
ERROR 10.11.2026 21:30:00 [QuickStart] Failed to parse "not a number"<!--en-->
INFO 10.11.2026 21:30:00 [QuickStartRu] Приложение запущено<!--ru-->
DEBUG 10.11.2026 21:30:00 [QuickStartRu] Загружено 42 элементов за 17 мс<!--ru-->
DEBUG 10.11.2026 21:30:00 [QuickStartRu] Дорогой отчёт: ...<!--ru-->
WARN 10.11.2026 21:30:00 [QuickStartRu] Мало места на диске<!--ru-->
ERROR 10.11.2026 21:30:00 [QuickStartRu] Не удалось разобрать "not a number"<!--ru-->
java.lang.NumberFormatException: For input string: "not a number"
	at java.lang.NumberFormatException.forInputString(NumberFormatException.java:65)
	...
```

### ${en:'API at a glance', ru:'API одним взглядом'}

${en:'Every level has the same set of overloads;', ru:'У каждого уровня одинаковый набор перегрузок;'} `info` ${en:'below stands for', ru:'ниже означает любой из'} `trace`, `debug`, `info`, `warn`, `error`.

| ${en:'Call', ru:'Вызов'} | ${en:'Meaning', ru:'Что делает'} |
|---|---|
| `LOG.info(message)` | ${en:'any object; its', ru:'любой объект; его'} `toString()` ${en:'is called safely', ru:'вызывается безопасно'} |
| `LOG.info("... {} ...", args)` | ${en:'parameterized message, see', ru:'параметризованное сообщение, см.'} [${en:'Message formatting', ru:'Форматирование сообщений'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) |
| `LOG.info(() -> message)` | ${en:'lazy message, evaluated only if the level is enabled', ru:'ленивое сообщение, вычисляется только при включённом уровне'} |
| `LOG.info(throwable)` | ${en:'stack trace without a message', ru:'трассировка стека без сообщения'} |
| `LOG.info(throwable, message)` | ${en:'message plus stack trace; the message may also be a `Supplier`', ru:'сообщение и трассировка; сообщение может быть и `Supplier`'} |
| `LOG.info(throwable, "... {} ...", args)` | ${en:'exception plus a parameterized message', ru:'исключение и параметризованное сообщение'} |
| `LOG.info("... {} ...", args, throwable)` | ${en:'the SLF4J order: a trailing exception that no `{}` consumes becomes the exception of the entry', ru:'порядок SLF4J: исключение в конце, которое не занял ни один `{}`, становится исключением записи'} |
| `LOG.info(SomeClass.class, ...)` | ${en:'any form above with an explicit source class', ru:'любая форма выше с явным классом-источником'} |
| `LOG.isInfoEnabled()` | ${en:'level check, with or without an explicit class', ru:'проверка уровня, с явным классом или без'} |
| `LOG.log(level, sourceClass, throwable, supplier, args)` | ${en:'the universal entry point every overload delegates to', ru:'универсальная точка входа, к которой сводятся все перегрузки'} |

${en:'Every class and method is documented in the', ru:'Каждый класс и метод описан в'} [Javadoc](${javadoc}/${stableVersion}), ${en:'which IDEs also show from the sources jar.', ru:'который IDE показывают и из jar с исходниками.'}

## ${en:'Agent-driven development', ru:'Агентная разработка'}

When a coding agent (Claude Code, Codex, Copilot and others) writes and debugs the code,<!--en-->
one short loop repeats many times: edit, run, read the output, edit again.<!--en-->
${name} keeps every step of that loop cheap.<!--en-->
Когда код пишет и отлаживает кодовый агент (Claude Code, Codex, Copilot и другие),<!--ru-->
много раз повторяется один короткий цикл: правка, запуск, чтение вывода, снова правка.<!--ru-->
${name} делает каждый шаг этого цикла дешёвым.<!--ru-->

- **One line to add logging.** No logger field and no `getLogger(Foo.class)` that has to match the class name: one import and `LOG.info(...)`. The diff is a single line, and nothing goes stale when the code is moved or the class is renamed.<!--en-->
- **Логирование добавляется одной строкой.** Нет ни поля логгера, ни `getLogger(Foo.class)`, который обязан совпадать с именем класса: один импорт и `LOG.info(...)`. Диф состоит из одной строки, и ничего не устаревает при переносе кода и переименовании класса.<!--ru-->
- **Output from the first run.** `ConsoleLogger` works without configuration and prints every level, `TRACE` included. A `LOG.debug(...)` added while investigating a problem is visible in the very next run, with no backend to set up first.<!--en-->
- **Вывод с первого запуска.** `ConsoleLogger` работает без настройки и печатает все уровни, включая `TRACE`. `LOG.debug(...)`, добавленный при разборе проблемы, виден уже в следующем запуске, и настраивать бэкенд для этого не нужно.<!--ru-->
- **Levels per run, without code.** `NANOLOG_LEVEL=INFO` cuts the noise once the first run is read, and `-Dnanolog.level.com.acme.payments=DEBUG` turns detail up for one package, even if the startup code switched `DEBUG` off. Agents in sandboxes and CI can always set an environment variable; a temporary code edit, by contrast, tends to get committed. See [Levels and options without code](#levels-and-options-without-code).<!--en-->
- **Уровни на один запуск, без правки кода.** `NANOLOG_LEVEL=INFO` убирает лишнее, когда первый запуск уже прочитан, а `-Dnanolog.level.com.acme.payments=DEBUG` добавляет подробностей для одного пакета, даже если код при старте выключил `DEBUG`. Переменную окружения агент может задать в любой песочнице и в CI, а временная правка кода обычно попадает в коммит. См. [Уровни и настройки без кода](#уровни-и-настройки-без-кода).<!--ru-->
- **Lines that are easy to read back.** One entry is one line of the form `LEVEL date [SourceClass] message`, followed by the stack trace if there is one. The output is simple to `grep` and to match against the sources. With `setShowLocation(true)` every line also names the method and the line of the call, `[PaymentService.charge(PaymentService.java:87)]`, so the agent jumps from the output straight to the statement. Through SLF4J, the backend's own `%file:%line` reports the same call site.<!--en-->
- **Строки, которые легко прочитать обратно.** Одна запись занимает одну строку вида `LEVEL дата [КлассИсточник] сообщение`, за ней идёт трассировка стека, если она есть. Такой вывод легко искать через `grep` и сопоставлять с исходниками. С `setShowLocation(true)` каждая строка ещё и называет метод и строку вызова, `[PaymentService.charge(PaymentService.java:87)]`, и агент переходит от вывода прямо к нужному оператору. Через SLF4J то же место вызова показывает `%file:%line` самого бэкенда.<!--ru-->
- **Output a script can parse.** `-Dnanolog.logger=com.nanolaba.logging.JsonLogger` switches to one JSON object per line, with the timestamp, thread, call site and stack trace as fields, ready for `jq` or a script the agent writes on the fly. See [JsonLogger](#jsonlogger).<!--en-->
- **Вывод, который разбирается скриптом.** `-Dnanolog.logger=com.nanolaba.logging.JsonLogger` переключает вывод на один JSON-объект в строке, где время, поток, место вызова и трассировка стека — отдельные поля. Такой вывод готов для `jq` или скрипта, который агент напишет на ходу. См. [JsonLogger](#jsonlogger).<!--ru-->
- **SLF4J habits carry over.** The same `{}` placeholders, and the exception may go first or last: `LOG.error("Failed to load {}", id, e)` logs the stack trace exactly as SLF4J does.<!--en-->
- **Привычки из SLF4J работают.** Те же плейсхолдеры `{}`, а исключение можно передать первым или последним: `LOG.error("Failed to load {}", id, e)` печатает трассировку стека так же, как SLF4J.<!--ru-->
- **A log call cannot fail the run.** Logging never throws: a broken `toString()`, a `null` or a wrong number of `{}` arguments ends up as a readable message, not as an exception. A log statement is never the reason a test turns red. In test runs, `-Dnanolog.strict=true` still reports such mismatches with the call site, so they get fixed instead of staying silent.<!--en-->
- **Вызов лога не может уронить запуск.** Логирование никогда не бросает исключений: сломанный `toString()`, `null` или неверное число аргументов для `{}` превращаются в читаемое сообщение, а не в исключение. Строка лога никогда не становится причиной красного теста. При этом в тестовых запусках `-Dnanolog.strict=true` сообщает о таких несовпадениях вместе с местом вызова, чтобы их исправили, а не оставили незамеченными.<!--ru-->
- **The whole library fits in a context window.** Nine small classes, one entry point, this README and the [Javadoc](${javadoc}/${stableVersion}). Every public method has Javadoc, so the IDE and the sources jar give the same answers as this file. An agent reads everything it needs in one pass and does not have to map a framework first.<!--en-->
- **Вся библиотека помещается в контекстное окно.** Девять небольших классов, одна точка входа, этот README и [Javadoc](${javadoc}/${stableVersion}). У каждого публичного метода есть Javadoc, поэтому IDE и jar с исходниками отвечают так же, как этот файл. Агент читает всё нужное за один проход, и ему не приходится сначала строить карту фреймворка.<!--ru-->
- **Capturing the log in a test takes one line.** `try (MemoryLogger log = MemoryLogger.install())` keeps the entries in memory and puts the previous logger back afterwards, so what was logged is asserted without a mocking framework; see [MemoryLogger and CompositeLogger](#memorylogger-and-compositelogger).<!--en-->
- **Для перехвата лога в тесте хватает одной строки.** `try (MemoryLogger log = MemoryLogger.install())` хранит записи в памяти и потом возвращает прежний логгер, поэтому записанное в лог проверяется без mock-фреймворка; см. [MemoryLogger и CompositeLogger](#memorylogger-и-compositelogger).<!--ru-->
- **The repository itself is ready for agents.** [AGENTS.md](AGENTS.md) describes the build, the invariants and the test requirements (JUnit plus 100% mutation coverage), so an agent can fix and extend ${name}, not only use it.<!--en-->
- **Сам репозиторий готов к работе агентов.** [AGENTS.md](AGENTS.md) описывает сборку, инварианты и требования к тестам (JUnit и 100% мутационное покрытие), поэтому агент может не только использовать ${name}, но и чинить и расширять его.<!--ru-->

### ${en:'Using Nanolog from a coding agent', ru:'Nanolog в работе кодового агента'}

Два файла дают агенту правила работы с библиотекой в нескольких сотнях строк, без пояснений этого README:<!--ru-->
Two files give an agent the rules of the library in a few hundred lines, without the explanations of this README:<!--en-->

- [`llms.txt`](llms.txt) — ${en:'a cheat sheet in the [llms.txt](https://llmstxt.org/) format: coordinates, call shapes, the rules that differ from SLF4J, settings without code changes and the test recipe.', ru:'шпаргалка в формате [llms.txt](https://llmstxt.org/): координаты, формы вызова, правила, отличающиеся от SLF4J, настройки без правки кода и рецепт для тестов.'}
- [`skills/nanolog/SKILL.md`](skills/nanolog/SKILL.md) — ${en:'the same guide as an agent skill, which the agent loads by itself when it works on code that logs through Nanolog. For Claude Code, put it into the project:', ru:'то же руководство в виде навыка агента, который агент сам подгружает, когда работает с кодом, пишущим в лог через Nanolog. Для Claude Code положите его в проект:'}

  ```
  mkdir -p .claude/skills/nanolog
  curl -fsSL https://raw.githubusercontent.com/nanolaba/nanolog/main/skills/nanolog/SKILL.md -o .claude/skills/nanolog/SKILL.md
  ```

  ${en:'Other agents that support the Agent Skills format read the same file from their own skills directory.', ru:'Другие агенты, поддерживающие формат Agent Skills, читают тот же файл из своего каталога навыков.'}

Можно ничего не устанавливать: достаточно одного абзаца в собственном `AGENTS.md` (или `CLAUDE.md`) проекта:<!--ru-->
Without installing anything, one paragraph in `AGENTS.md` (or `CLAUDE.md`) of the project is enough:<!--en-->

```markdown
## Logging

This project logs with Nanolog (com.nanolaba:nanolog). Before adding or changing log calls,
read https://raw.githubusercontent.com/nanolaba/nanolog/main/llms.txt
```

Оба файла генерируются из `agent-guide.src.md` при каждой сборке: версии берутся из `pom.xml`, примеры кода —<!--ru-->
из компилируемых примеров, а CI падает, если файлы устарели. Идеи о том, что ещё облегчило бы работу агентов,<!--ru-->
собраны под [меткой `agents`](${repo}/issues?q=is%3Aissue+label%3Aagents).<!--ru-->
Both files are generated from `agent-guide.src.md` on every build: the versions come from `pom.xml`, the code<!--en-->
from the compiled examples, and CI fails when they are stale. Ideas for making the library easier for agents<!--en-->
are collected under the [`agents` label](${repo}/issues?q=is%3Aissue+label%3Aagents).<!--en-->

## ${en:'Choosing a logger', ru:'Выбор логгера'}

${en:'The default `ConsoleLogger` can be replaced in three ways:', ru:'Логгер по умолчанию, `ConsoleLogger`, можно заменить тремя способами:'}

1. **${en:'Programmatically', ru:'Программно'}** — ${en:'pass any', ru:'передайте любую реализацию'} `ILogger` ${en:'implementation to', ru:'в'} `LOG.init(...)` ${en:'at application startup:', ru:'при старте приложения:'}

   ${widget:import(path='${examples}/LoggerSetup.java', region='slf4j', wrap='true')}

2. **${en:'Via a system property', ru:'Через системное свойство'}** — ${en:'the fully qualified name of a class with a public no-arg constructor:', ru:'полное имя класса с публичным конструктором без параметров:'}

   ```
   -Dnanolog.logger=com.nanolaba.logging.Slf4jLogger
   ```

3. **${en:'Via a configuration file', ru:'Через файл конфигурации'}** — ${en:'put', ru:'положите'} `nanolog.properties` ${en:'on the classpath:', ru:'в classpath:'}

   ```properties
   nanolog.logger=com.nanolaba.logging.Slf4jLogger
   ```

   При первой инициализации `LOG` ключи файла с префиксом `nanolog.` копируются<!--ru-->
   в системные свойства, не перезаписывая те, что уже заданы через `-D` или переменную окружения.<!--ru-->
   Остальные ключи игнорируются. Путь к файлу переопределяется свойством `nanolog.config`.<!--ru-->
   When `LOG` initialises, the keys of the file with the `nanolog.` prefix are copied<!--en-->
   into system properties without overwriting the ones already set via `-D` or an environment variable.<!--en-->
   Other keys are ignored. The file path can be overridden with the `nanolog.config` property.<!--en-->

${en:'Every property can also be set as an environment variable: upper case, dots replaced by underscores, so', ru:'Любое свойство можно задать и переменной окружения: верхний регистр, точки заменены подчёркиваниями, то есть'} `nanolog.logger` ${en:'becomes', ru:'превращается в'} `NANOLOG_LOGGER`.
${en:'Precedence, highest first:', ru:'Приоритет, от высшего:'} `LOG.init(logger)` → `-Dnanolog.logger` → `NANOLOG_LOGGER` → `nanolog.properties` → `ConsoleLogger`.
${en:'A no-argument', ru:'Вызов'} `LOG.init()` ${en:'re-reads the property and the file.', ru:'без аргументов перечитывает свойство и файл.'}

| ${en:'Property', ru:'Свойство'} | ${en:'Meaning', ru:'Назначение'} | ${en:'Default', ru:'По умолчанию'} |
|---|---|---|
| `nanolog.logger` | ${en:'class name of the `ILogger` implementation', ru:'имя класса реализации `ILogger`'} | `com.nanolaba.logging.ConsoleLogger` |
| `nanolog.config` | ${en:'classpath resource with the configuration', ru:'ресурс в classpath с конфигурацией'} | `nanolog.properties` |
| `nanolog.strict` | ${en:'report calls whose `{}` placeholders and arguments do not match, see', ru:'сообщать о вызовах, у которых плейсхолдеры `{}` не совпадают с аргументами, см.'} [${en:'Message formatting', ru:'Форматирование сообщений'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) | `false` |

Если класс из `nanolog.logger` не удалось создать, ${name} печатает в `System.err`<!--ru-->
диагностику с префиксом `[NANOLOG]` и продолжает работу с `ConsoleLogger`.<!--ru-->
If the class named in `nanolog.logger` cannot be instantiated, ${name} prints a<!--en-->
`[NANOLOG]`-prefixed diagnostic to `System.err` and carries on with `ConsoleLogger`.<!--en-->

${widget:alert(type='important', text="${en:'`LOG.init` replaces the logger for the whole JVM. Call it once at application startup, before other threads start logging. Any library on the classpath can call it as well, so do not call it from library code.', ru:'`LOG.init` заменяет логгер для всей JVM. Вызывайте его один раз при старте приложения, до того как другие потоки начнут писать в лог. Его может вызвать и любая библиотека в classpath, поэтому не вызывайте его из библиотечного кода.'}")}

### ConsoleLogger

${en:'The default implementation. Writes', ru:'Реализация по умолчанию. Пишет'} `ERROR` ${en:'to', ru:'в'} `System.err` ${en:'and everything else to', ru:'и всё остальное в'} `System.out`, ${en:'one line per entry plus the stack trace:', ru:'по строке на запись плюс трассировка стека:'}

```
LEVEL dd.MM.yyyy HH:mm:ss [SourceClass] Message
```

${en:'Every component is controlled by a fluent setter:', ru:'Каждый компонент управляется цепочкой сеттеров:'}

${widget:import(path='${examples}/LoggerSetup.java', region='console', wrap='true')}

- `showLevel` — ${en:'the level name', ru:'имя уровня'}.
- `showDate` + `dateFormat` — ${en:'the timestamp and its', ru:'временная метка и её'} `SimpleDateFormat`; ${en:'formatting is synchronized, so one shared format is safe', ru:'форматирование синхронизировано, общий экземпляр безопасен'}.
- `showSource` / `showSourceFullName` — ${en:'the simple or the fully qualified source class name', ru:'короткое или полное имя класса-источника'}.
- `showLocation` — ${en:'the call site after the class name, in the form stack traces use, so IDEs and terminals turn it into a link: `[PaymentService.charge(PaymentService.java:87)]`. Off by default. A call with an explicit source class has no call site and keeps the plain `[PaymentService]`.', ru:'место вызова после имени класса в том же виде, что и в трассировках стека, поэтому IDE и терминалы превращают его в ссылку: `[PaymentService.charge(PaymentService.java:87)]`. По умолчанию выключено. У вызова с явным классом-источником места вызова нет, и остаётся обычный `[PaymentService]`.'}
- `traceEnabled` … `errorEnabled` — ${en:'per-level switches; everything is enabled by default, so switch `TRACE` and `DEBUG` off in production, in code or with `nanolog.level=INFO`. Disabled levels are rejected before the `LogEntry` is built, so lazy messages are never evaluated.', ru:'переключатели по уровням; по умолчанию включено всё, поэтому в продакшене отключите `TRACE` и `DEBUG` в коде или через `nanolog.level=INFO`. Отключённые уровни отсекаются до создания `LogEntry`, поэтому ленивые сообщения не вычисляются.'}

Для своего формата (цвет, JSON, другая раскладка) унаследуйтесь от `ConsoleLogger`<!--ru-->
и переопределите `getOutputStream`, `writeLevel`, `writeDate`, `writeSource`,<!--ru-->
`writeMessage`, `writeThrowable` или `addDelimiter`.<!--ru-->
For a custom layout (colours, JSON, a different order) extend `ConsoleLogger` and<!--en-->
override `getOutputStream`, `writeLevel`, `writeDate`, `writeSource`, `writeMessage`,<!--en-->
`writeThrowable` or `addDelimiter`.<!--en-->

### ${en:'Levels and options without code', ru:'Уровни и настройки без кода'}

${en:'The constructor of `ConsoleLogger` reads these settings from system properties, environment variables and `nanolog.properties`, in this order of precedence:', ru:'Конструктор `ConsoleLogger` читает эти настройки из системных свойств, переменных окружения и `nanolog.properties`, именно в таком порядке приоритета:'}

| ${en:'Property', ru:'Свойство'} | ${en:'Environment variable', ru:'Переменная окружения'} | ${en:'Meaning', ru:'Назначение'} |
|---|---|---|
| `nanolog.level` | `NANOLOG_LEVEL` | ${en:'the lowest enabled level:', ru:'самый низкий включённый уровень:'} `TRACE` (${en:'default', ru:'по умолчанию'}), `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF` |
| `nanolog.level.<${en:'package or class', ru:'пакет или класс'}>` | — | ${en:'the level for one package or class', ru:'уровень для одного пакета или класса'} |
| `nanolog.levels` | `NANOLOG_LEVELS` | ${en:'several such rules in one value:', ru:'несколько таких правил в одном значении:'} `com.acme.payments=DEBUG,com.acme.db=TRACE` |
| `nanolog.console.showLevel` | `NANOLOG_CONSOLE_SHOWLEVEL` | `true` ${en:'or', ru:'или'} `false`, ${en:'like the setter', ru:'как одноимённый сеттер'} |
| `nanolog.console.showDate` | `NANOLOG_CONSOLE_SHOWDATE` | ${en:'the same', ru:'то же'} |
| `nanolog.console.showSource` | `NANOLOG_CONSOLE_SHOWSOURCE` | ${en:'the same', ru:'то же'} |
| `nanolog.console.showSourceFullName` | `NANOLOG_CONSOLE_SHOWSOURCEFULLNAME` | ${en:'the same', ru:'то же'} |
| `nanolog.console.showLocation` | `NANOLOG_CONSOLE_SHOWLOCATION` | ${en:'the same', ru:'то же'} |
| `nanolog.console.dateFormat` | `NANOLOG_CONSOLE_DATEFORMAT` | ${en:'a `SimpleDateFormat` pattern', ru:'шаблон `SimpleDateFormat`'} |

```
java -Dnanolog.level=WARN -Dnanolog.level.com.acme.payments=DEBUG -jar app.jar
NANOLOG_LEVEL=WARN NANOLOG_LEVELS=com.acme.payments=DEBUG java -jar app.jar
```

- ${en:'A package or class rule beats the global level, however that level was set, setters included. Turning `DEBUG` on for one package therefore needs no code change.', ru:'Правило для пакета или класса главнее глобального уровня, как бы тот ни был задан, в том числе сеттерами. Поэтому включение `DEBUG` для одного пакета не требует правки кода.'}
- ${en:'The most specific rule wins: the class itself, then its enclosing classes, then its packages from the innermost outwards. A rule matches whole name segments only, so `com.acme.pay` does not cover `com.acme.payments`.', ru:'Действует самое точное правило: сам класс, затем внешние классы, затем пакеты от ближайшего к дальнему. Правило совпадает только с целыми частями имени, поэтому `com.acme.pay` не распространяется на `com.acme.payments`.'}
- ${en:'`nanolog.level.<name>` beats an item for the same name in `nanolog.levels`.', ru:'`nanolog.level.<имя>` главнее элемента с тем же именем в `nanolog.levels`.'}
- ${en:'Setters called after the constructor override the values it read, so `new ConsoleLogger().setDebugEnabled(false)` in code wins over `nanolog.level`; package and class rules still apply on top.', ru:'Сеттеры, вызванные после конструктора, главнее прочитанных им значений, поэтому `new ConsoleLogger().setDebugEnabled(false)` в коде главнее `nanolog.level`; правила для пакетов и классов при этом продолжают действовать.'}
- ${en:'An invalid value prints a `[NANOLOG]` diagnostic to `System.err` and is ignored; logging goes on.', ru:'Неверное значение печатает в `System.err` диагностику с префиксом `[NANOLOG]` и игнорируется; логирование продолжается.'}
- ${en:'`nanolog.properties` is read when `LOG` initialises, so a `ConsoleLogger` created before that sees only `-D` and the environment.', ru:'`nanolog.properties` читается при инициализации `LOG`, поэтому `ConsoleLogger`, созданный раньше, видит только `-D` и переменные окружения.'}

### JsonLogger

${en:'One JSON object per line, for `jq`, log collectors and scripts. It needs no JSON library and is selected without code:', ru:'Один JSON-объект в строке для `jq`, сборщиков логов и скриптов. Библиотека JSON не нужна, а включается логгер без правки кода:'}

```
-Dnanolog.logger=com.nanolaba.logging.JsonLogger
```

```json
{"ts":"2026-11-11T02:30:31.412Z","level":"WARN","logger":"com.acme.PaymentService","thread":"main","message":"Charge of 42 failed, retrying","template":"Charge of {} failed, retrying","args":["42"],"caller":{"class":"com.acme.PaymentService","method":"charge","file":"PaymentService.java","line":87}}
```

- `ts` — ${en:'ISO-8601 in UTC with milliseconds, sortable as text;', ru:'ISO-8601 в UTC с миллисекундами, сортируется как текст;'} `level`, `logger` (${en:'the source class', ru:'класс-источник'}), `thread`, `message` (${en:'the formatted message', ru:'отформатированное сообщение'}).
- `template` ${en:'and', ru:'и'} `args` — ${en:'the raw message and the arguments as strings, for parameterized messages only; equal events can be grouped whatever their arguments.', ru:'исходное сообщение и аргументы строками, только для параметризованных сообщений; одинаковые события можно группировать независимо от аргументов.'}
- `caller` — ${en:'the call site, when the source class was detected;', ru:'место вызова, если класс-источник определялся автоматически;'} `exception` — `class`, `message` ${en:'and', ru:'и'} `stackTrace`, ${en:'when there is a throwable. Fields without a value are left out.', ru:'если есть исключение. Поля без значения не выводятся.'}
- ${en:'Line breaks and control characters are escaped, so one entry is always exactly one line, stack trace included.', ru:'Переводы строк и управляющие символы экранируются, поэтому одна запись — всегда ровно одна строка, вместе с трассировкой стека.'}
- `JsonLogger` ${en:'extends `ConsoleLogger`: the same levels, the same level properties and the same streams (`ERROR` to `System.err`). The display options do not apply.', ru:'наследует `ConsoleLogger`: те же уровни, те же свойства уровней и те же потоки (`ERROR` в `System.err`). Настройки отображения на него не действуют.'}

### Slf4jLogger

${en:'The bridge to', ru:'Мост к'} **SLF4J**. ${en:'Each entry is forwarded to the', ru:'Каждая запись передаётся в'} `org.slf4j.Logger` ${en:'named after the source class, so your SLF4J backend sees the real caller, not', ru:'с именем класса-источника, поэтому SLF4J-бэкенд видит реального вызывающего, а не'} `LOG`. ${en:'Levels, patterns, appenders and files are configured in that backend as usual.', ru:'Уровни, шаблоны, аппендеры и файлы настраиваются в этом бэкенде как обычно.'}

${widget:alert(type='note', text="${en:'`slf4j-api` is a `provided` dependency of Nanolog. Add it and a backend to your project yourself.', ru:'`slf4j-api` объявлен у Nanolog со scope `provided`. Добавьте его и бэкенд в свой проект самостоятельно.'}")}

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>${pom.properties.slf4j.version}</version>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>${logbackVersion}</version>
</dependency>
```

${widget:import(path='${examples}/LoggerSetup.java', region='slf4j', wrap='true')}

${en:'Things to know:', ru:'Что нужно знать:'}

- ${en:'Location-aware backends, Logback and Log4j 2 among them, are told that the call site is the first frame after `LOG`. Their own `%file:%line`, `%method` and `%class` (Logback) or `%l` (Log4j 2) therefore show the code that called `LOG`, not `Slf4jLogger`, with no backend configuration. slf4j-simple prints no location at all.', ru:'Бэкендам, которые умеют определять место вызова, в том числе Logback и Log4j 2, передаётся, что место вызова — первый кадр после `LOG`. Поэтому их собственные `%file:%line`, `%method` и `%class` (Logback) или `%l` (Log4j 2) показывают код, вызвавший `LOG`, а не `Slf4jLogger`, и настраивать бэкенд для этого не нужно. slf4j-simple место вызова не печатает вовсе.'}
- ${en:'The message reaches SLF4J already formatted, so the backend does not see the `{}` arguments as structured data. SLF4J markers are not supported; MDC works as usual because the backend reads it itself.', ru:'Сообщение приходит в SLF4J уже отформатированным, поэтому бэкенд не видит `{}`-аргументы как структурные данные. Маркеры SLF4J не поддерживаются; MDC работает как обычно, бэкенд читает его сам.'}
- ${en:'An entry without a message is passed with an empty message, not the word `null`.', ru:'Запись без сообщения передаётся с пустым сообщением, а не со словом `null`.'}
- ${en:'An entry without a source class goes to the SLF4J `ROOT` logger.', ru:'Запись без класса-источника уходит в `ROOT`-логгер SLF4J.'}
- ${en:'Loggers are cached per source class in a `ConcurrentHashMap` and never evicted, so with dynamic class reloading the cache retains `Class` references.', ru:'Логгеры кешируются по классу-источнику в `ConcurrentHashMap` и не вытесняются, поэтому при динамической перезагрузке классов кеш удерживает ссылки на `Class`.'}

### MemoryLogger ${en:'and', ru:'и'} CompositeLogger

`MemoryLogger` ${en:'keeps entries in memory for tests. Every level is enabled, so entries are captured whatever the production configuration says, and nothing is printed.', ru:'хранит записи в памяти для тестов. Включены все уровни, поэтому записи перехватываются при любой боевой конфигурации, и ничего не печатается.'}

${widget:import(path='${examples}/CapturingLogs.java', region='capture', wrap='true')}

- `install()` ${en:'replaces the logger of `LOG` and remembers the previous one; `close()` puts it back, also when the test fails. `installForwarding()` does the same but still passes every entry to the previous logger, so the usual output stays visible.', ru:'заменяет логгер `LOG` и запоминает прежний; `close()` возвращает его, даже если тест упал. `installForwarding()` делает то же самое, но продолжает передавать каждую запись прежнему логгеру, и обычный вывод остаётся на месте.'}
- `getEntries()`, `getEntries(level)`, `getMessages()`, `contains(level, text)`, `clear()` — ${en:'snapshots and short assertions; the logger is thread-safe.', ru:'снимки и короткие проверки; логгер потокобезопасен.'}
- `new MemoryLogger(capacity)` ${en:'keeps only the newest entries.', ru:'хранит только самые новые записи.'}
- ${en:'`LOG.init` is JVM-wide, so tests that install a logger must not run in parallel with each other.', ru:'`LOG.init` действует на всю JVM, поэтому тесты, устанавливающие логгер, нельзя запускать параллельно друг с другом.'}

`CompositeLogger` ${en:'sends every entry to several loggers. Each of them receives only the levels it has enabled, and a lazy message is built only if at least one of them wants it:', ru:'отправляет каждую запись в несколько логгеров. Каждый получает только включённые у него уровни, а ленивое сообщение вычисляется, только если оно нужно хотя бы одному из них:'}

${widget:import(path='${examples}/CapturingLogs.java', region='composite', wrap='true')}

## ${en:'Message formatting', ru:'Форматирование сообщений'}

${widget:import(path='${examples}/Formatting.java', region='formatting', wrap='true')}

- ${en:'`{}` is replaced by the next argument; `\{}` is a literal `{}` and consumes no argument; `\\{}` is a literal backslash followed by a substitution.', ru:'`{}` заменяется очередным аргументом; `\{}` — литеральные `{}`, аргумент не расходуется; `\\{}` — литеральный обратный слеш и подстановка.'}
- ${en:'A `Throwable` in the last position that no `{}` consumes is not an argument: it becomes the exception of the entry, and its stack trace is logged, as in SLF4J. In `LOG.error("Cause: {}", e)` the placeholder takes the exception, so only `e.toString()` is printed. An exception passed first always wins; a trailing one then stays an ordinary argument.', ru:'`Throwable` на последнем месте, который не занял ни один `{}`, не считается аргументом: он становится исключением записи, и его трассировка стека попадает в лог, как в SLF4J. В `LOG.error("Cause: {}", e)` исключение занимает плейсхолдер, поэтому печатается только `e.toString()`. Исключение, переданное первым, всегда главнее; исключение в конце тогда остаётся обычным аргументом.'}
- ${en:'Primitive arrays are rendered with', ru:'Примитивные массивы выводятся через'} `Arrays.toString`, ${en:'object arrays with', ru:'объектные через'} `Arrays.deepToString`.
- ${en:'Every `toString()` is guarded: if it throws, the value becomes `[FAILED toString()]` and nothing propagates to the caller. A throwable whose stack trace cannot be printed becomes `[FAILED printStackTrace()]`.', ru:'Каждый `toString()` защищён: если он бросает исключение, значение превращается в `[FAILED toString()]`, и наружу ничего не вылетает. Исключение, трассировку которого не удаётся напечатать, превращается в `[FAILED printStackTrace()]`.'}
- ${en:'The message object itself may be anything, not only a', ru:'Сам объект сообщения может быть любым, не только'} `String`; ${en:'it is converted with the same guarded', ru:'он преобразуется тем же защищённым'} `toString()`.

### ${en:'Checking placeholders in tests', ru:'Проверка плейсхолдеров в тестах'}

Нестрогость к `{}` правильна в продакшене, но в разработке она скрывает ошибки: потерянный аргумент<!--ru-->
не оставляет в выводе никакого следа. С `nanolog.strict=true` (или `NANOLOG_STRICT=true`) каждый<!--ru-->
вызов с аргументами, у которого число плейсхолдеров `{}` не совпадает с числом аргументов,<!--ru-->
дополнительно печатает в `System.err` одну строку с местом вызова:<!--ru-->
Leniency about `{}` is right in production, but during development it hides mistakes: a dropped argument<!--en-->
leaves no trace in the output at all. With `nanolog.strict=true` (or `NANOLOG_STRICT=true`), every call<!--en-->
with arguments whose number of `{}` placeholders differs from the number of arguments additionally<!--en-->
prints one line with the call site to `System.err`:<!--en-->

```
[NANOLOG] com.acme.PaymentService.charge(PaymentService.java:87): 2 placeholders, 1 argument in "Charge of {} for {}"
```

- ${en:'The entry itself is logged exactly as without the check, and nothing is thrown.', ru:'Сама запись логируется так же, как без проверки, и ничего не бросается.'}
- ${en:'Each template is reported once; the 1000 most recent reported templates are remembered.', ru:'О каждом шаблоне сообщается один раз; запоминаются 1000 последних шаблонов, о которых уже сообщено.'}
- ${en:'Only calls with arguments and enabled levels are checked. Escaped `\{}` does not count, and a trailing exception taken as the exception of the entry is not an argument.', ru:'Проверяются только вызовы с аргументами на включённых уровнях. Экранированный `\{}` не считается, а исключение в конце, ставшее исключением записи, не считается аргументом.'}
- ${en:'When the check is off, it costs one boolean test per call. The setting is read by `LOG.init()`.', ru:'Выключенная проверка стоит одной проверки флага на вызов. Настройка читается в `LOG.init()`.'}

${en:'Turn it on for test runs, so mismatches show up in the output that is read anyway:', ru:'Включайте её для тестовых запусков, чтобы несовпадения появлялись в выводе, который и так читают:'}

```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <nanolog.strict>true</nanolog.strict>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

```groovy
test {
    systemProperty 'nanolog.strict', 'true'
}
```

## ${en:'Caller detection', ru:'Определение вызывающего класса'}

Когда класс не передан явно, `LOG` создаёт `Throwable`, проходит по его стеку<!--ru-->
и берёт первый кадр, который не принадлежит `LOG`, `LogEntry` или реализации `ILogger`.<!--ru-->
When no class is passed explicitly, `LOG` creates a `Throwable`, walks its stack trace<!--en-->
and takes the first frame that does not belong to `LOG`, `LogEntry` or an `ILogger` implementation.<!--en-->

- ${en:'Nested, anonymous and lambda classes are attributed to their top-level enclosing class.', ru:'Вложенные, анонимные классы и лямбды относятся к своему внешнему классу верхнего уровня.'}
- ${en:'The frame found by the walk is kept as `LogEntry.getCaller()`: the exact class, method, file and line of the call, including nested classes and lambdas. It costs nothing extra, and it is `null` when the class was passed explicitly.', ru:'Найденный кадр сохраняется в `LogEntry.getCaller()`: точный класс, метод, файл и строка вызова, в том числе для вложенных классов и лямбд. Это ничего не стоит сверх обхода стека; при явно переданном классе значение равно `null`.'}
- ${en:'Frame classes are resolved through the context class loader of the thread first and the class loader of `LOG` second, so the detection works when Nanolog lives in a shared loader of an application server and the caller in a web application. A frame neither loader can see is skipped.', ru:'Классы кадров ищутся сначала контекстным загрузчиком потока, затем загрузчиком `LOG`, поэтому определение работает, когда Nanolog лежит в общем загрузчике сервера приложений, а вызывающий код — в веб-приложении. Кадр, невидимый обоим загрузчикам, пропускается.'}
- ${en:'The stack walk happens on every call without an explicit class, including', ru:'Обход стека происходит при каждом вызове без явного класса, включая'} `LOG.isDebugEnabled()`. ${en:'In hot paths pass the class explicitly:', ru:'В горячих местах передавайте класс явно:'} `LOG.debug(MyClass.class, ...)`.
- ${en:'Any class implementing `ILogger` is skipped, so a custom logger that logs through `LOG` must pass its source class explicitly.', ru:'Любой класс, реализующий `ILogger`, пропускается, поэтому свой логгер, который сам пишет через `LOG`, должен передавать класс-источник явно.'}

## ${en:'Writing your own logger', ru:'Собственный логгер'}

`ILogger` ${en:'has a single abstract method, so the simplest implementation is a lambda:', ru:'содержит один абстрактный метод, поэтому простейшая реализация — лямбда:'}

${widget:import(path='${examples}/LoggerSetup.java', region='lambda', wrap='true')}

${en:'A full implementation also filters levels:', ru:'Полная реализация ещё и фильтрует уровни:'}

${widget:import(path='${examples}/CustomLogger.java', region='custom', wrap='true')}

${en:'What a `LogEntry` carries:', ru:'Что содержит `LogEntry`:'}

- `getLevel()` — `TRACE`, `DEBUG`, `INFO`, `WARN` ${en:'or', ru:'или'} `ERROR`.
- `getSourceClass()` — ${en:'the detected or explicitly passed class;', ru:'определённый или явно переданный класс;'} `null` ${en:'only if', ru:'только если'} `null` ${en:'was passed', ru:'передали явно'}.
- `getCaller()` — ${en:'the `StackTraceElement` of the logging call;', ru:'`StackTraceElement` вызова логирования;'} `null` ${en:'when the source class was passed explicitly.', ru:'при явно переданном классе-источнике.'}
- `getThrowable()` — ${en:'the exception or', ru:'исключение или'} `null`.
- `getMessage()` — ${en:'the raw message object, already resolved from its', ru:'исходный объект сообщения, уже полученный из'} `Supplier`.
- `getTimestamp()`, `getThreadName()` — ${en:'when and in which thread the entry was created, so a logger that writes later or elsewhere still knows.', ru:'когда и в каком потоке создана запись, чтобы это знал и логгер, который пишет позже или в другом месте.'}
- `getArgs()` — ${en:'the substitution arguments.', ru:'аргументы подстановки.'}
- `getFormattedMessage()` — ${en:'the final string with `{}` substituted.', ru:'итоговая строка с подставленными `{}`.'}

${en:'`isEnabled(...)` is called before the `LogEntry` is built and before any `Supplier` is resolved, so returning `false` skips the whole cost of the message.', ru:'`isEnabled(...)` вызывается до создания `LogEntry` и до раскрытия `Supplier`, поэтому возврат `false` избавляет от всей стоимости сообщения.'}

## ${en:'Thread safety and performance', ru:'Потокобезопасность и производительность'}

- ${en:'The static', ru:'Статические методы'} `LOG` ${en:'methods are safe to call from any thread as long as the installed', ru:'можно вызывать из любого потока, пока установленный'} `ILogger` ${en:'is;', ru:'потокобезопасен;'} `ConsoleLogger` ${en:'and', ru:'и'} `Slf4jLogger` ${en:'are.', ru:'таковы.'}
- `LOG.init(...)` ${en:'and the', ru:'и сеттеры'} `ConsoleLogger` ${en:'setters are meant for startup, not for concurrent reconfiguration.', ru:'рассчитаны на старт приложения, а не на параллельную перенастройку.'}
- ${en:'A call with a disabled level costs one', ru:'Вызов с отключённым уровнем стоит один'} `isEnabled` ${en:'check plus, without an explicit class, one stack walk. Message formatting and', ru:'плюс, без явного класса, один обход стека. Форматирование сообщения и'} `Supplier` ${en:'evaluation happen only for enabled levels.', ru:'вычисляются только для включённых уровней.'}

${widget:if(cond='endsWith(${devVersion}, -SNAPSHOT)')}
## ${en:'Using SNAPSHOT versions', ru:'Использование SNAPSHOT-версий'}

${en:'Development builds are published to the Sonatype snapshot repository. Add it and depend on the `-SNAPSHOT` version:', ru:'Сборки в разработке публикуются в snapshot-репозиторий Sonatype. Подключите его и укажите версию с суффиксом `-SNAPSHOT`:'}

**Maven (pom.xml)**

```xml
<repositories>
    <repository>
        <id>central.sonatype.com-snapshot</id>
        <url>https://central.sonatype.com/repository/maven-snapshots</url>
        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </snapshots>
    </repository>
</repositories>

<dependency>
    <groupId>${pom.groupId}</groupId>
    <artifactId>${pom.artifactId}</artifactId>
    <version>${pom.version}</version>
</dependency>
```

**Gradle (build.gradle)**

```groovy
repositories {
    maven {
        url 'https://central.sonatype.com/repository/maven-snapshots'
    }
}

dependencies {
    implementation '${coords}:${pom.version}'
}
```
${widget:endIf}

## Changelog

${widget:if(cond='endsWith(${devVersion}, -SNAPSHOT)')}
### ${pom.version}

${en:'In development, not released yet.', ru:'В разработке, ещё не выпущена.'}

- ${en:'A trailing `Throwable` that no `{}` consumes becomes the exception of the entry, as in SLF4J: `LOG.error("Failed", e)` and `LOG.error("Failed {}", id, e)` no longer drop the stack trace.', ru:'`Throwable` в конце списка аргументов, который не занял ни один `{}`, становится исключением записи, как в SLF4J: `LOG.error("Failed", e)` и `LOG.error("Failed {}", id, e)` больше не теряют трассировку стека.'}
- ${en:'`LogEntry.getCaller()` returns the frame of the logging call, and `ConsoleLogger.setShowLocation(true)` prints it as `[PaymentService.charge(PaymentService.java:87)]`.', ru:'`LogEntry.getCaller()` возвращает кадр вызова логирования, а `ConsoleLogger.setShowLocation(true)` печатает его как `[PaymentService.charge(PaymentService.java:87)]`.'}
- ${en:'`MemoryLogger` captures entries in tests (`try (MemoryLogger log = MemoryLogger.install()) { ... }`), `CompositeLogger` sends entries to several loggers.', ru:'`MemoryLogger` перехватывает записи в тестах (`try (MemoryLogger log = MemoryLogger.install()) { ... }`), `CompositeLogger` отправляет записи в несколько логгеров.'}
- ${en:'`nanolog.strict=true` reports calls whose `{}` placeholders and arguments do not match, once per template, with the call site.', ru:'`nanolog.strict=true` сообщает о вызовах, у которых плейсхолдеры `{}` не совпадают с аргументами, по разу на шаблон и с местом вызова.'}
- ${en:'The whole public API has Javadoc, and the build fails on a missing comment.', ru:'У всего публичного API есть Javadoc, и сборка падает, если комментария не хватает.'}
- ${en:'[`llms.txt`](llms.txt) and the agent skill [`skills/nanolog/SKILL.md`](skills/nanolog/SKILL.md) give coding agents the rules of the library; both are generated with the README.', ru:'[`llms.txt`](llms.txt) и навык агента [`skills/nanolog/SKILL.md`](skills/nanolog/SKILL.md) дают кодовым агентам правила работы с библиотекой; оба генерируются вместе с README.'}
- ${en:'`ConsoleLogger` reads its levels and options from properties and environment variables: `nanolog.level`, `nanolog.level.<package or class>`, `nanolog.levels` and `nanolog.console.*`. Every property, `nanolog.logger` and `nanolog.config` included, can be set as an environment variable (`NANOLOG_LEVEL`), which beats `nanolog.properties`.', ru:'`ConsoleLogger` читает уровни и настройки из свойств и переменных окружения: `nanolog.level`, `nanolog.level.<пакет или класс>`, `nanolog.levels` и `nanolog.console.*`. Любое свойство, включая `nanolog.logger` и `nanolog.config`, можно задать переменной окружения (`NANOLOG_LEVEL`), и она главнее `nanolog.properties`.'}
- ${en:'`JsonLogger` writes one JSON object per line. `LogEntry.getTimestamp()` and `LogEntry.getThreadName()` tell when and where an entry was created.', ru:'`JsonLogger` пишет один JSON-объект в строке. `LogEntry.getTimestamp()` и `LogEntry.getThreadName()` сообщают, когда и где создана запись.'}
- `ConsoleLogger`: ${en:'a throwable whose `toString()` throws no longer escapes from the logging call; it is printed as `[FAILED printStackTrace()]`.', ru:'исключение, у которого бросает `toString()`, больше не вылетает из вызова логирования, а печатается как `[FAILED printStackTrace()]`.'}
- `Slf4jLogger`: ${en:'location-aware backends (Logback, Log4j 2) report the code that called `LOG` in `%file:%line` and `%l`, instead of `Slf4jLogger.log`.', ru:'бэкенды, определяющие место вызова (Logback, Log4j 2), показывают в `%file:%line` и `%l` код, вызвавший `LOG`, а не `Slf4jLogger.log`.'}
${widget:endIf}

### 1.1

- ${en:'New overloads `LOG.info(throwable, "... {} ...", args)` and `LOG.info(sourceClass, throwable, "... {} ...", args)` for every level: an exception together with a parameterized message.', ru:'Новые перегрузки `LOG.info(throwable, "... {} ...", args)` и `LOG.info(sourceClass, throwable, "... {} ...", args)` для каждого уровня: исключение вместе с параметризованным сообщением.'}
- `nanolog.properties`: ${en:'only `nanolog.` keys are copied into system properties; other keys are ignored.', ru:'в системные свойства копируются только ключи с префиксом `nanolog.`; остальные игнорируются.'}
- `Slf4jLogger`: ${en:'a `null` source class goes to the `ROOT` logger instead of throwing; an entry without a message no longer logs the word `null`.', ru:'`null` в качестве класса-источника уходит в `ROOT`-логгер вместо исключения; запись без сообщения больше не пишет слово `null`.'}
- ${en:'Caller detection resolves classes through the context class loader first and skips frames it cannot see, instead of failing in application servers and plugin frameworks.', ru:'Определение вызывающего класса сначала использует контекстный загрузчик и пропускает невидимые кадры вместо падения в серверах приложений и плагинных фреймворках.'}
- `ConsoleLogger`: ${en:'timestamp formatting is thread-safe.', ru:'форматирование даты потокобезопасно.'}
- ${en:'A broken `toString()` inside an `Object[]` argument no longer propagates to the caller.', ru:'Сломанный `toString()` внутри `Object[]`-аргумента больше не пробрасывается наружу.'}
- ${en:'A `null` context class loader at startup is tolerated.', ru:'`null` в качестве контекстного загрузчика при старте больше не роняет инициализацию.'}
- ${en:'The diagnostic for a missing configuration file names the right property.', ru:'Диагностика об отсутствующем файле конфигурации называет правильное свойство.'}
- ${en:'The jar declares', ru:'В манифесте jar объявлено'} `Automatic-Module-Name: com.nanolaba.nanolog`.

### 1.0

${en:'First release.', ru:'Первый выпуск.'}

## ${en:'Contributing', ru:'Участие в разработке'}

${widget:alert(type='warning', text="${en:'`README.md` and `README.ru.md` are generated from `README.src.md` by the readme-generator Maven plugin. Edit `README.src.md` and run `mvn compile`; CI fails when the generated files are stale.', ru:'`README.md` и `README.ru.md` генерируются из `README.src.md` Maven-плагином readme-generator. Правьте `README.src.md` и запускайте `mvn compile`; CI падает, если сгенерированные файлы устарели.'}")}

- ${en:'Build and test:', ru:'Сборка и тесты:'} `mvn verify -Dgpg.skip=true`. ${en:'The', ru:'Фаза'} `test` ${en:'phase runs JUnit and then pitest with a 100% mutation threshold, so every change to production code needs tests that kill the corresponding mutants.', ru:'запускает JUnit, а затем pitest с порогом 100% убитых мутантов, поэтому любое изменение кода библиотеки нужно сопровождать тестами, убивающими соответствующие мутанты.'}
- ${en:'The library targets Java', ru:'Библиотека собирается под Java'} ${pom.properties.maven.compiler.release}; ${en:'do not use newer APIs.', ru:'не используйте более новые API.'}
- ${en:'The code samples in this file are imported from', ru:'Примеры кода в этом файле импортируются из'} `${examples}` ${en:'and compiled with the tests, so they cannot go stale.', ru:'и компилируются вместе с тестами, поэтому не устаревают.'}
- ${en:'Questions, bug reports and feature requests:', ru:'Вопросы, ошибки и пожелания:'} [${repo}/issues](${repo}/issues).

## ${en:'License', ru:'Лицензия'}

[Apache License 2.0](LICENSE).

---
*${en:'Generated with', ru:'Сгенерировано с помощью'} [nanolaba/readme-generator](https://github.com/nanolaba/readme-generator)*
