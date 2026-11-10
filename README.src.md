<!--@nrg.languages=en,ru-->
<!--@nrg.defaultLanguage=en-->

<!--@name=**Nanolog**-->
<!--@stableVersion=1.1-->
<!--@devVersion=${pom.version}-->
<!--@logbackVersion=1.5.38-->
<!--@coords=${pom.groupId}:${pom.artifactId}-->
<!--@repo=${pom.scm.url}-->
<!--@javadoc=https://javadoc.io/doc/${pom.groupId}/${pom.artifactId}-->
<!--@examples=src/test/java/com/nanolaba/logging/examples-->
<!--@quickStartFile.en=QuickStart.java-->
<!--@quickStartFile.ru=QuickStartRu.java-->
Maintainer note: bump stableVersion after every release. The development version is read from pom.xml.<!--nrg.ignore-->

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
- **${en:'Zero dependencies.', ru:'Ноль зависимостей.'}** ${en:'Six classes, no transitive libraries;', ru:'Шесть классов, никаких транзитивных библиотек;'} `slf4j-api` ${en:'is needed only if you use', ru:'нужен, только если вы используете'} `Slf4jLogger`.
- **${en:'Pluggable.', ru:'Расширяемый.'}** ${en:'A custom backend is one lambda or one class implementing', ru:'Свой бэкенд — это одна лямбда или один класс, реализующий'} `ILogger`.
- **${en:'Fits the agent loop.', ru:'Удобен агентам.'}** ${en:'One line per log call, output from the first run, an API that fits in one context window; see', ru:'Одна строка на вызов, вывод с первого запуска, API целиком помещается в контекстное окно; см.'} [${en:'Agent-driven development', ru:'Агентная разработка'}](#${en:'agent-driven-development', ru:'агентная-разработка'}).

${en:'The whole library:', ru:'Вся библиотека целиком:'}

${widget:fileTree(path='src/main/java/com/nanolaba/logging', depth='1')}

- `LOG` — ${en:'the static entry point with all the', ru:'статическая точка входа со всеми'} `trace` / `debug` / `info` / `warn` / `error` ${en:'overloads', ru:'перегрузками'}.
- `ILogger` — ${en:'the extension point: one abstract method plus an optional level filter', ru:'точка расширения: один абстрактный метод и необязательный фильтр уровней'}.
- `LogEntry` — ${en:'one log record and the message formatter', ru:'одна запись лога и форматирование сообщения'}.
- `ConsoleLogger`, `Slf4jLogger` — ${en:'the bundled implementations', ru:'встроенные реализации'}.
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
| `LOG.info(SomeClass.class, ...)` | ${en:'any form above with an explicit source class', ru:'любая форма выше с явным классом-источником'} |
| `LOG.isInfoEnabled()` | ${en:'level check, with or without an explicit class', ru:'проверка уровня, с явным классом или без'} |
| `LOG.log(level, sourceClass, throwable, supplier, args)` | ${en:'the universal entry point every overload delegates to', ru:'универсальная точка входа, к которой сводятся все перегрузки'} |

${en:'The full signatures are in the', ru:'Полные сигнатуры — в'} [Javadoc](${javadoc}/${stableVersion}).

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
- **Lines that are easy to read back.** One entry is one line of the form `LEVEL date [SourceClass] message`, followed by the stack trace if there is one. The output is simple to `grep` and to match against the sources.<!--en-->
- **Строки, которые легко прочитать обратно.** Одна запись занимает одну строку вида `LEVEL дата [КлассИсточник] сообщение`, за ней идёт трассировка стека, если она есть. Такой вывод легко искать через `grep` и сопоставлять с исходниками.<!--ru-->
- **A log call cannot fail the run.** Logging never throws: a broken `toString()`, a `null` or a wrong number of `{}` arguments ends up as a readable message, not as an exception. A log statement is never the reason a test turns red.<!--en-->
- **Вызов лога не может уронить запуск.** Логирование никогда не бросает исключений: сломанный `toString()`, `null` или неверное число аргументов для `{}` превращаются в читаемое сообщение, а не в исключение. Строка лога никогда не становится причиной красного теста.<!--ru-->
- **The whole library fits in a context window.** Six classes, one entry point, this README and the [Javadoc](${javadoc}/${stableVersion}). An agent reads everything it needs in one pass and does not have to map a framework first.<!--en-->
- **Вся библиотека помещается в контекстное окно.** Шесть классов, одна точка входа, этот README и [Javadoc](${javadoc}/${stableVersion}). Агент читает всё нужное за один проход, и ему не приходится сначала строить карту фреймворка.<!--ru-->
- **Capturing the log in a test takes one method reference.** `ILogger` has a single abstract method, so what was logged can be asserted without a mocking framework:<!--en-->
- **Для перехвата лога в тесте хватает одной ссылки на метод.** У `ILogger` один абстрактный метод, поэтому записанное в лог проверяется без mock-фреймворка:<!--ru-->

  ${widget:import(path='${examples}/CapturingLogs.java', region='capture', wrap='true')}

- **The repository itself is ready for agents.** [AGENTS.md](AGENTS.md) describes the build, the invariants and the test requirements (JUnit plus 100% mutation coverage), so an agent can fix and extend ${name}, not only use it.<!--en-->
- **Сам репозиторий готов к работе агентов.** [AGENTS.md](AGENTS.md) описывает сборку, инварианты и требования к тестам (JUnit и 100% мутационное покрытие), поэтому агент может не только использовать ${name}, но и чинить и расширять его.<!--ru-->

> [!WARNING]
> One SLF4J habit does not carry over: the exception goes **first**, as in `LOG.error(e, "Failed to load {}", id)`. In `LOG.error("Failed", e)` the exception is an unused `{}` argument, so the message is printed without the stack trace. Tracked in [#1](${repo}/issues/1).<!--en-->
> Одна привычка из SLF4J здесь не работает: исключение передаётся **первым**, как в `LOG.error(e, "Failed to load {}", id)`. В вызове `LOG.error("Failed", e)` исключение становится лишним аргументом для `{}`, и сообщение печатается без трассировки стека. Исправление отслеживается в [#1](${repo}/issues/1).<!--ru-->

Features planned for this workflow are tracked under the [`agents` label](${repo}/issues?q=is%3Aissue+label%3Aagents):<!--en-->
the call site in the output, an in-memory logger for tests, JSON lines, levels set through properties<!--en-->
and environment variables, an `llms.txt` and an installable agent skill.<!--en-->
Возможности, запланированные для этого сценария, собраны под [меткой `agents`](${repo}/issues?q=is%3Aissue+label%3Aagents):<!--ru-->
место вызова в выводе, логгер в памяти для тестов, JSON-строки, уровни через свойства<!--ru-->
и переменные окружения, `llms.txt` и устанавливаемый навык для агентов.<!--ru-->

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
   в системные свойства, не перезаписывая те, что уже заданы через `-D`.<!--ru-->
   Остальные ключи игнорируются. Путь к файлу переопределяется свойством `nanolog.config`.<!--ru-->
   When `LOG` initialises, the keys of the file with the `nanolog.` prefix are copied<!--en-->
   into system properties without overwriting the ones already set via `-D`.<!--en-->
   Other keys are ignored. The file path can be overridden with the `nanolog.config` property.<!--en-->

${en:'Precedence, highest first:', ru:'Приоритет, от высшего:'} `LOG.init(logger)` → `-Dnanolog.logger` → `nanolog.properties` → `ConsoleLogger`.
${en:'A no-argument', ru:'Вызов'} `LOG.init()` ${en:'re-reads the property and the file.', ru:'без аргументов перечитывает свойство и файл.'}

| ${en:'Property', ru:'Свойство'} | ${en:'Meaning', ru:'Назначение'} | ${en:'Default', ru:'По умолчанию'} |
|---|---|---|
| `nanolog.logger` | ${en:'class name of the `ILogger` implementation', ru:'имя класса реализации `ILogger`'} | `com.nanolaba.logging.ConsoleLogger` |
| `nanolog.config` | ${en:'classpath resource with the configuration', ru:'ресурс в classpath с конфигурацией'} | `nanolog.properties` |

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
- `traceEnabled` … `errorEnabled` — ${en:'per-level switches; everything is enabled by default, so switch `TRACE` and `DEBUG` off in production. Disabled levels are rejected before the `LogEntry` is built, so lazy messages are never evaluated.', ru:'переключатели по уровням; по умолчанию включено всё, поэтому в продакшене отключите `TRACE` и `DEBUG`. Отключённые уровни отсекаются до создания `LogEntry`, поэтому ленивые сообщения не вычисляются.'}

Для своего формата (цвет, JSON, другая раскладка) унаследуйтесь от `ConsoleLogger`<!--ru-->
и переопределите `getOutputStream`, `writeLevel`, `writeDate`, `writeSource`,<!--ru-->
`writeMessage`, `writeThrowable` или `addDelimiter`.<!--ru-->
For a custom layout (colours, JSON, a different order) extend `ConsoleLogger` and<!--en-->
override `getOutputStream`, `writeLevel`, `writeDate`, `writeSource`, `writeMessage`,<!--en-->
`writeThrowable` or `addDelimiter`.<!--en-->

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

- ${en:'The message reaches SLF4J already formatted, so the backend does not see the `{}` arguments as structured data. SLF4J markers are not supported; MDC works as usual because the backend reads it itself.', ru:'Сообщение приходит в SLF4J уже отформатированным, поэтому бэкенд не видит `{}`-аргументы как структурные данные. Маркеры SLF4J не поддерживаются; MDC работает как обычно, бэкенд читает его сам.'}
- ${en:'An entry without a message is passed with an empty message, not the word `null`.', ru:'Запись без сообщения передаётся с пустым сообщением, а не со словом `null`.'}
- ${en:'An entry without a source class goes to the SLF4J `ROOT` logger.', ru:'Запись без класса-источника уходит в `ROOT`-логгер SLF4J.'}
- ${en:'Loggers are cached per source class in a `ConcurrentHashMap` and never evicted, so with dynamic class reloading the cache retains `Class` references.', ru:'Логгеры кешируются по классу-источнику в `ConcurrentHashMap` и не вытесняются, поэтому при динамической перезагрузке классов кеш удерживает ссылки на `Class`.'}

## ${en:'Message formatting', ru:'Форматирование сообщений'}

${widget:import(path='${examples}/Formatting.java', region='formatting', wrap='true')}

- ${en:'`{}` is replaced by the next argument; `\{}` is a literal `{}` and consumes no argument; `\\{}` is a literal backslash followed by a substitution.', ru:'`{}` заменяется очередным аргументом; `\{}` — литеральные `{}`, аргумент не расходуется; `\\{}` — литеральный обратный слеш и подстановка.'}
- ${en:'Primitive arrays are rendered with', ru:'Примитивные массивы выводятся через'} `Arrays.toString`, ${en:'object arrays with', ru:'объектные через'} `Arrays.deepToString`.
- ${en:'Every `toString()` is guarded: if it throws, the value becomes `[FAILED toString()]` and nothing propagates to the caller.', ru:'Каждый `toString()` защищён: если он бросает исключение, значение превращается в `[FAILED toString()]`, и наружу ничего не вылетает.'}
- ${en:'The message object itself may be anything, not only a', ru:'Сам объект сообщения может быть любым, не только'} `String`; ${en:'it is converted with the same guarded', ru:'он преобразуется тем же защищённым'} `toString()`.

## ${en:'Caller detection', ru:'Определение вызывающего класса'}

Когда класс не передан явно, `LOG` создаёт `Throwable`, проходит по его стеку<!--ru-->
и берёт первый кадр, который не принадлежит `LOG`, `LogEntry` или реализации `ILogger`.<!--ru-->
When no class is passed explicitly, `LOG` creates a `Throwable`, walks its stack trace<!--en-->
and takes the first frame that does not belong to `LOG`, `LogEntry` or an `ILogger` implementation.<!--en-->

- ${en:'Nested, anonymous and lambda classes are attributed to their top-level enclosing class.', ru:'Вложенные, анонимные классы и лямбды относятся к своему внешнему классу верхнего уровня.'}
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
- `getThrowable()` — ${en:'the exception or', ru:'исключение или'} `null`.
- `getMessage()` — ${en:'the raw message object, already resolved from its', ru:'исходный объект сообщения, уже полученный из'} `Supplier`.
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
