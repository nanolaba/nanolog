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
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/16916/badge)](https://www.bestpractices.dev/projects/16916)
${widget:badge(type='github-stars', repo='nanolaba/nanolog')}

# ${en:'Nanolog: Java logging with fewer tokens for AI coding agents', ru:'Nanolog: логирование в Java с экономией токенов для ИИ-агентов'}

${name} — библиотека логирования для Java 8+ без зависимостей, которая сокращает вывод в консоль, когда ИИ-агент<!--ru-->
запускает ваш код. Под Claude Code, Codex, Gemini CLI или Cursor CLI в консоли остаются предупреждения,<!--ru-->
ошибки и `LOG.dbg`, по строке на запись, а запуск заканчивается сводкой; полный лог уходит в файл.<!--ru-->
Упавший тестовый запуск напечатал 1 058 токенов вместо 13 577 (o200k_base, см.<!--ru-->
[Меньше токенов](#меньше-токенов-логи-для-ии-агентов)). Без агента консоль печатает все уровни.<!--ru-->
${name} is a zero-dependency logging library for Java 8+ that keeps console output short when an AI<!--en-->
coding agent runs your code. Under Claude Code, Codex, Gemini CLI or Cursor CLI the console keeps<!--en-->
warnings, errors and `LOG.dbg`, one line each, and the run ends with a summary; the full log goes<!--en-->
to a file. A failing test run printed 1,058 tokens instead of 13,577 (o200k_base, see<!--en-->
[Fewer tokens](#fewer-tokens-logs-for-ai-coding-agents)). Without an agent the console prints every level.<!--en-->

Все вызовы идут через статические методы класса `LOG`, класс-источник определяется автоматически,<!--ru-->
поэтому поле логгера в каждом классе больше не нужно. Библиотека печатает в консоль, пишет JSON-строки,<!--ru-->
перехватывает записи в тестах и работает сама по себе или поверх **SLF4J**, а значит и поверх<!--ru-->
Logback, Log4j 2, JBoss Logging и других.<!--ru-->
Every call goes through the static methods of `LOG`, and the source class is detected automatically,<!--en-->
so no class needs a logger field. The library prints to the console, writes JSON lines, captures entries<!--en-->
in tests, and works on its own or on top of **SLF4J**, and therefore on top of Logback, Log4j 2,<!--en-->
JBoss Logging and others.<!--en-->

**${en:'Questions and feedback', ru:'Вопросы и отзывы'}:** [Discussions](${repo}/discussions) · **${en:'bugs', ru:'ошибки'}:** [Issues](${repo}/issues/new/choose) · ${en:'English or Russian', ru:'на русском или английском'}

${en:'Nanolog for Java (`com.nanolaba:nanolog`) is not related to [NanoLog](https://github.com/PlatformLab/NanoLog), the nanosecond-scale logger for C++.', ru:'Nanolog для Java (`com.nanolaba:nanolog`) не связан с [NanoLog](https://github.com/PlatformLab/NanoLog), наносекундным логгером для C++.'}

## ${en:'Try it', ru:'Попробовать'}

```xml
<dependency>
    <groupId>${pom.groupId}</groupId>
    <artifactId>${pom.artifactId}</artifactId>
    <version>${stableVersion}</version>
</dependency>
```

```kotlin
implementation("${coords}:${stableVersion}")
```

${widget:import(path='${examples}/TryIt.java', region='tryit', wrap='true')}

```
INFO 17.11.2026 10:15:02 [TryIt] Checkout of 3 items
DBG 17.11.2026 10:15:02 [TryIt.main(TryIt.java:11)] price.multiply(BigDecimal.valueOf(quantity)) = 129.90 (BigDecimal)
[NANOLOG] 1 LOG.dbg call ran in this JVM: TryIt.java:11
```

${en:'`LOG.dbg` prints the expression as written, its value and type, and the call site, and returns the value; the last line reminds you to remove the call. More in', ru:'`LOG.dbg` печатает выражение так, как оно написано, его значение, тип и место вызова и возвращает значение; последняя строка напоминает убрать вызов. Подробнее —'} [${en:'Print debugging', ru:'Отладочная печать'}](#${en:'print-debugging-logdbg-prints-the-expression-and-its-value', ru:'отладочная-печать-logdbg-печатает-выражение-и-его-значение'}) ${en:'and', ru:'и'} [${en:'Quick start', ru:'Быстрый старт'}](#${en:'quick-start', ru:'быстрый-старт'}).

${en:'When a coding agent runs the same program (here with `AI_AGENT=claude-code`), the `INFO` line goes only to the run log, and the run ends with a summary:', ru:'Когда ту же программу запускает кодовый агент (здесь с `AI_AGENT=claude-code`), строка `INFO` остаётся только в журнале запуска, а запуск заканчивается сводкой:'}

```
[NANOLOG] Agent mode (AI_AGENT=claude-code): the console shows WARN, ERROR and every LOG.dbg; the full log is in /home/me/shop/target/nanolog/latest.log, WARN and ERROR also in /home/me/shop/target/nanolog/latest.errors.log; nanolog.agent=off turns it off
DBG [TryIt.main(TryIt.java:11)] price.multiply(BigDecimal.valueOf(quantity)) = 129.90 (BigDecimal)
[NANOLOG] Run summary: 2 entries: 1 INFO, 1 DEBUG; the full log is in /home/me/shop/target/nanolog/latest.log
[NANOLOG]   1 LOG.dbg call ran in this JVM: TryIt.java:11
```

${en:'More in', ru:'Подробнее —'} [${en:'Agent mode', ru:'Агентский режим'}](#${en:'agent-mode-nanolog-detects-coding-agents', ru:'агентский-режим-nanolog-распознаёт-кодовых-агентов'}) ${en:'and', ru:'и'} [${en:'Fewer tokens', ru:'Меньше токенов'}](${en:'#fewer-tokens-logs-for-ai-coding-agents', ru:'#меньше-токенов-логи-для-ии-агентов'}).

## ${en:'Why Nanolog', ru:'Зачем нужен Nanolog'}

- **${en:'Fewer tokens for AI coding agents.', ru:'Меньше токенов для ИИ-агентов.'}** ${en:'*Since 1.4.*', ru:'*С версии 1.4.*'} ${en:'When Claude Code, Gemini CLI, Codex, Cursor CLI or another agent that sets `AI_AGENT` runs the program, the console keeps `WARN`, `ERROR` and `LOG.dbg`, and the full log goes to `target/nanolog/latest.log`. A failing test run printed 1,058 tokens instead of 13,577, the run summary included, and a green one 178 instead of 16,550 (o200k_base); see', ru:'Когда программу запускает Claude Code, Gemini CLI, Codex, Cursor CLI или другой агент, задающий `AI_AGENT`, в консоли остаются `WARN`, `ERROR` и `LOG.dbg`, а полный лог уходит в `target/nanolog/latest.log`. Упавший тестовый запуск напечатал 1 058 токенов вместо 13 577, вместе со сводкой запуска, а зелёный — 178 вместо 16 550 (o200k_base); см.'} [${en:'Fewer tokens', ru:'Меньше токенов'}](${en:'#fewer-tokens-logs-for-ai-coding-agents', ru:'#меньше-токенов-логи-для-ии-агентов'}).
- **${en:'A summary that survives truncation.', ru:'Сводка, которая переживает обрезку.'}** ${en:'*Since 1.4.*', ru:'*С версии 1.4.*'} ${en:'Agents often keep only the head and the tail of a long output; in agent mode the run ends with the counts by level, each kind of warning and error with its call site, and the trace of the first error; see', ru:'Агенты часто оставляют только начало и конец длинного вывода; в агентском режиме в конце запуска печатаются число записей по уровням, каждый вид предупреждений и ошибок с местом вызова и трассировка первой ошибки; см.'} [${en:'Run summary', ru:'Сводка запуска'}](#${en:'run-summary-the-part-of-the-output-that-survives-truncation', ru:'сводка-запуска-часть-вывода-которая-переживает-обрезку'}).
- **${en:'Print debugging that names its values.', ru:'Отладочная печать, которая называет значения.'}** ${en:'*Since 1.4.*', ru:'*С версии 1.4.*'} ${en:'`LOG.dbg(order.total())` prints `order.total() = 129.90 (BigDecimal)` with the file and line of the call and returns the value, like `dbg!` in Rust and icecream in Python; objects without `toString()` are shown field by field instead of `Order@2f92e0f4`; see', ru:'`LOG.dbg(order.total())` печатает `order.total() = 129.90 (BigDecimal)` с файлом и строкой вызова и возвращает значение, как `dbg!` в Rust и icecream в Python; объекты без `toString()` показываются поле за полем, а не как `Order@2f92e0f4`; см.'} [${en:'Print debugging', ru:'Отладочная печать'}](#${en:'print-debugging-logdbg-prints-the-expression-and-its-value', ru:'отладочная-печать-logdbg-печатает-выражение-и-его-значение'}).
- **${en:'Wide events, one line per request.', ru:'Wide events: одна строка на запрос.'}** ${en:'*Since 1.4.*', ru:'*С версии 1.4.*'} ${en:'`LOG.unit("checkout")` writes one canonical log line per request or job when it ends: outcome, duration, your fields and the count of warnings and errors logged inside it, `checkout ok 182 ms orderId=A-17 warn=1`; failed units carry a stable error key; see', ru:'`LOG.unit("checkout")` пишет одну каноническую строку лога на запрос или задание, когда оно заканчивается: исход, длительность, ваши поля и число предупреждений и ошибок внутри, `checkout ok 182 ms orderId=A-17 warn=1`; у неудачных единиц есть стабильный ключ ошибки; см.'} [${en:'Wide events', ru:'Wide events'}](#${en:'wide-events-one-line-per-request-canonical-log-lines', ru:'wide-events-одна-строка-на-запрос-canonical-log-lines'}).
- **${en:'No logger fields.', ru:'Нет полей логгера.'}** `LOG.info("...")` ${en:'works from any class; the caller is found by walking the stack', ru:'работает из любого класса, вызывающий определяется по стеку'}.
- **${en:'Zero dependencies.', ru:'Без зависимостей.'}** ${en:'No transitive libraries;', ru:'Никаких транзитивных библиотек;'} `slf4j-api` ${en:'is needed only if you use', ru:'нужен, только если вы используете'} `Slf4jLogger`.
- **${en:'SLF4J-style placeholders.', ru:'Подстановка в стиле SLF4J.'}** `{}` ${en:'with escaping, array rendering and sane handling of missing or extra arguments', ru:'с экранированием, выводом массивов и разумной обработкой лишних и недостающих аргументов'}.
- **${en:'JSON lines and tests built in.', ru:'JSON и тесты из коробки.'}** ${en:'`JsonLogger` writes one JSON object per line for `jq` and log collectors; `MemoryLogger` captures entries in a test and asserts on them, with the whole log in the failure message; see', ru:'`JsonLogger` пишет один JSON-объект в строке для `jq` и сборщиков логов; `MemoryLogger` перехватывает записи в тесте и проверяет их, показывая весь лог в сообщении об ошибке; см.'} [JsonLogger](#jsonlogger) ${en:'and', ru:'и'} [MemoryLogger ${en:'and', ru:'и'} CompositeLogger](#${en:'memorylogger-and-compositelogger', ru:'memorylogger-и-compositelogger'}).
- **${en:'Levels change at run time.', ru:'Уровни меняются на лету.'}** ${en:'`LOG.setLevel("com.acme.payments", DEBUG)` from an admin endpoint or a test takes effect in every thread, and `LOG.withLevel(...)` puts the old level back when the block ends; see', ru:'`LOG.setLevel("com.acme.payments", DEBUG)` из админского эндпоинта или теста действует во всех потоках, а `LOG.withLevel(...)` возвращает прежний уровень в конце блока; см.'} [${en:'Changing levels at run time', ru:'Изменение уровней во время работы'}](#${en:'changing-levels-at-run-time', ru:'изменение-уровней-во-время-работы'}).
- **${en:'Logging never throws.', ru:'Логирование никогда не падает.'}** ${en:'A broken', ru:'Сломанный'} `toString()` ${en:'becomes', ru:'превращается в'} `[FAILED toString()]` ${en:'instead of an exception in your code', ru:'вместо исключения в вашем коде'}.
- **${en:'Lazy messages.', ru:'Ленивые сообщения.'}** `LOG.debug(() -> ...)` ${en:'is evaluated only when the level is enabled', ru:'вычисляется только при включённом уровне'}.
- **${en:'Pluggable.', ru:'Расширяемый.'}** ${en:'A custom backend is one lambda or one class implementing', ru:'Свой бэкенд — это одна лямбда или один класс, реализующий'} `ILogger`.
- **${en:'Fits the agent loop.', ru:'Удобен агентам.'}** ${en:'One line per log call apart from stack traces, output from the first run, an API that fits in one context window; see', ru:'Одна строка на вызов, не считая трассировок стека, вывод с первого запуска, API целиком помещается в контекстное окно; см.'} [${en:'Agent-driven development', ru:'Агентная разработка'}](#${en:'agent-driven-development', ru:'агентная-разработка'}).

How Nanolog compares with tinylog, SLF4J, Log4j 2, Flogger and others: [Related projects](#related-projects).<!--en-->
Как Nanolog выглядит рядом с tinylog, SLF4J, Log4j 2, Flogger и другими: [Похожие проекты](#похожие-проекты).<!--ru-->

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

${en:'Requirements: Java', ru:'Требования: Java'} ${pom.properties.maven.compiler.release}+. ${en:'On Java 9+ the jar is the module `com.nanolaba.nanolog`, which needs only `java.base` (SLF4J and JUnit are optional), so it works on the module path and in a runtime image built with `jlink`. A logger class named in `nanolog.logger` must be in a package its module exports. On the module path JUnit does not find `NanologExtension` by itself: register it with `@ExtendWith(NanologExtension.class)`.', ru:'На Java 9+ jar — это модуль `com.nanolaba.nanolog`, которому нужен только `java.base` (SLF4J и JUnit необязательны), поэтому он работает на module path и в образе среды выполнения, собранном `jlink`. Класс логгера, названный в `nanolog.logger`, должен лежать в пакете, который экспортирует его модуль. На module path JUnit не находит `NanologExtension` сам: подключите его через `@ExtendWith(NanologExtension.class)`.'}

${en:'The same jar serves Kotlin: CI compiles every kind of `LOG` call from Kotlin, `LOG.info { "lazy" }` among them, so no overload is ambiguous there. A block given to `LOG.timed` or `LOG.unit` takes the overload that returns nothing; for the value of the block write `val n = LOG.timed("Count users", Supplier { repo.count() })`.', ru:'Тот же jar подходит и для Kotlin: CI компилирует из Kotlin вызовы `LOG` всех форм, в том числе `LOG.info { "lazy" }`, поэтому неоднозначных перегрузок там нет. Блок, переданный в `LOG.timed` или `LOG.unit`, выбирает перегрузку без возвращаемого значения; чтобы получить значение блока, пишите `val n = LOG.timed("Count users", Supplier { repo.count() })`.'}

### ${en:'Native image and Android', ru:'Native image и Android'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

- ${en:'**GraalVM native image.** The jar carries its metadata in `META-INF/native-image`: the constructors of the loggers that `nanolog.logger` names, the stack walker of caller detection and the resource `nanolog.properties`. CI builds a native image on GraalVM 21 and 25 that logs through `ConsoleLogger` and through `JsonLogger` chosen with `-Dnanolog.logger`. A class of your own named in `nanolog.logger` or `nanolog.context.provider` needs its constructor registered by you, and a class that `LOG.dbg` or `nanolog.dump` prints field by field needs its fields registered. `LOG.dbg` prints values without the expression text, since there are no source files at run time.', ru:'**GraalVM native image.** Метаданные лежат в jar, в `META-INF/native-image`: конструкторы логгеров, которые называет `nanolog.logger`, обход стека для определения вызывающего класса и ресурс `nanolog.properties`. CI собирает native image на GraalVM 21 и 25, который пишет через `ConsoleLogger` и через `JsonLogger`, выбранный `-Dnanolog.logger`. Конструктор собственного класса, названного в `nanolog.logger` или `nanolog.context.provider`, нужно зарегистрировать самостоятельно, а у классов, которые `LOG.dbg` или `nanolog.dump` печатают по полям, — их поля. `LOG.dbg` печатает значения без текста выражения: исходников во время работы нет.'}
- ${en:'**Android and R8.** The jar carries `META-INF/proguard/nanolog.pro`, which R8 and ProGuard apply by themselves: it keeps the constructors of the loggers and the attributes that caller detection reads, and silences the warnings about SLF4J and JUnit, which an app may not have. Obfuscation renames your classes in the log as it does in stack traces; the mapping file restores them (`retrace`). Nanolog is not tested on Android.', ru:'**Android и R8.** В jar есть `META-INF/proguard/nanolog.pro`, который R8 и ProGuard применяют сами: он сохраняет конструкторы логгеров и атрибуты, которые читает определение вызывающего класса, и убирает предупреждения об SLF4J и JUnit, которых в приложении может не быть. Обфускация переименовывает ваши классы в логе так же, как в трассировках стека; восстанавливает их файл маппинга (`retrace`). На Android Nanolog не тестируется.'}

## ${en:'Quick start', ru:'Быстрый старт'}

${en:'No setup is needed:', ru:'Настройка не нужна:'} `ConsoleLogger` ${en:'is active by default and prints every level, including', ru:'включён по умолчанию и печатает все уровни, включая'} `TRACE`${en:'; when a coding agent runs the program, the console is shorter, see', ru:'; когда программу запускает кодовый агент, консоль короче, см.'} [${en:'Agent mode', ru:'Агентский режим'}](#${en:'agent-mode-nanolog-detects-coding-agents', ru:'агентский-режим-nanolog-распознаёт-кодовых-агентов'}).

${widget:import(path='${examples}/${quickStartFile}', region='quickstart', wrap='true')}

${en:'Console output (', ru:'Вывод в консоль ('}`ERROR` ${en:'goes to', ru:'уходит в'} `System.err`, ${en:'everything else to', ru:'всё остальное — в'} `System.out`):

```
INFO 10.11.2026 21:30:00 [QuickStart] Application started<!--en-->
DEBUG 10.11.2026 21:30:00 [QuickStart] Loaded 42 items in 17 ms<!--en-->
DEBUG 10.11.2026 21:30:00 [QuickStart] Expensive report: ...<!--en-->
WARN 10.11.2026 21:30:00 [QuickStart] Disk space is low<!--en-->
ERROR 10.11.2026 21:30:00 [QuickStart] Failed to parse "not a number" java.lang.NumberFormatException: For input string: "not a number"<!--en-->
INFO 10.11.2026 21:30:00 [QuickStartRu] Приложение запущено<!--ru-->
DEBUG 10.11.2026 21:30:00 [QuickStartRu] Загружено 42 элементов за 17 мс<!--ru-->
DEBUG 10.11.2026 21:30:00 [QuickStartRu] Дорогой отчёт: ...<!--ru-->
WARN 10.11.2026 21:30:00 [QuickStartRu] Мало места на диске<!--ru-->
ERROR 10.11.2026 21:30:00 [QuickStartRu] Не удалось разобрать "not a number" java.lang.NumberFormatException: For input string: "not a number"<!--ru-->
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
| `LOG.debug("Stats: {}", LOG.lazy(() -> summarize(stats)))` | ${en:'one lazy argument: computed only if the level is enabled, and the template stays a template, see', ru:'один ленивый аргумент: вычисляется только при включённом уровне, а шаблон остаётся шаблоном, см.'} [${en:'Message formatting', ru:'Форматирование сообщений'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) |
| `LOG.info(throwable)` | ${en:'stack trace without a message', ru:'трассировка стека без сообщения'} |
| `LOG.info(throwable, message)` | ${en:'message plus stack trace; the message may also be a `Supplier`', ru:'сообщение и трассировка; сообщение может быть и `Supplier`'} |
| `LOG.info(throwable, "... {} ...", args)` | ${en:'exception plus a parameterized message', ru:'исключение и параметризованное сообщение'} |
| `LOG.info("... {} ...", args, throwable)` | ${en:'the SLF4J order: a trailing exception that no `{}` consumes becomes the exception of the entry', ru:'порядок SLF4J: исключение в конце, которое не занял ни один `{}`, становится исключением записи'} |
| `LOG.info("Payment accepted", LOG.kv("orderId", id))` | ${en:'a named argument: `orderId=A-17` after the message, a field in JSON; in a `{}` it prints its value, see', ru:'именованный аргумент: `orderId=A-17` после сообщения и поле в JSON; в `{}` печатается его значение, см.'} [${en:'Fields', ru:'Поля'}](#${en:'fields-named-arguments', ru:'поля-именованные-аргументы'}) |
| `LOG.info("Login with {}", LOG.secret(token))` | ${en:'a secret written as `***` by every logger; fields and context named like secrets are masked too, see', ru:'секрет, который любой логгер пишет как `***`; поля и контекст с именами секретов маскируются тоже, см.'} [${en:'Masking secrets', ru:'Маскирование секретов'}](#${en:'masking-secrets', ru:'маскирование-секретов'}) |
| `LOG.debug("Prompt: {}", LOG.content(prompt))` | ${en:'a payload written as its length and hash unless `nanolog.content` shows it, see', ru:'содержимое, которое пишется как длина и хеш, пока `nanolog.content` не покажет его, см.'} [${en:'Logging payloads safely', ru:'Безопасное логирование содержимого'}](#${en:'logging-payloads-safely', ru:'безопасное-логирование-содержимого'}) |
| `LOG.info(SomeClass.class, ...)` | ${en:'any form above with an explicit source class', ru:'любая форма выше с явным классом-источником'} |
| `LOG.isInfoEnabled()` | ${en:'level check, with or without an explicit class', ru:'проверка уровня, с явным классом или без'} |
| `LOG.setLevel(level)`, `LOG.setLevel("com.acme", level)` | ${en:'change the global level or the level of a package while the application runs, see', ru:'смена глобального уровня или уровня пакета во время работы, см.'} [${en:'Changing levels at run time', ru:'Изменение уровней во время работы'}](#${en:'changing-levels-at-run-time', ru:'изменение-уровней-во-время-работы'}) |
| `LOG.withLevel("com.acme", level)` | ${en:'the same for a `try` block, restored at its end', ru:'то же на время блока `try`, с восстановлением в конце'} |
| `LOG.dbg(expression)` | ${en:'print the expression, its value and the call site, and return the value, see', ru:'печать выражения, его значения и места вызова с возвратом значения, см.'} [${en:'Print debugging', ru:'Отладочная печать'}](#${en:'print-debugging-logdbg-prints-the-expression-and-its-value', ru:'отладочная-печать-logdbg-печатает-выражение-и-его-значение'}) |
| `LOG.once().warn(...)` | ${en:'log a statement only the first time it runs, see', ru:'запись только при первом выполнении строки, см.'} [${en:'Rate limiting', ru:'Ограничение частоты'}](#${en:'rate-limiting', ru:'ограничение-частоты'}) |
| `LOG.atMostEvery(10, TimeUnit.SECONDS).warn(...)` | ${en:'at most once per period, with the number of skipped calls on the next entry', ru:'не чаще раза за период, с числом пропущенных вызовов в следующей записи'} |
| `try (LOG.Timer t = LOG.time("Load users"))` | ${en:'`Load users took 123 ms` when the block ends; `LOG.timed("Count", () -> repo.count())` returns the value, see', ru:'`Load users took 123 ms`, когда блок заканчивается; `LOG.timed("Count", () -> repo.count())` возвращает значение, см.'} [${en:'Timing a block', ru:'Замер времени блока'}](#${en:'timing-a-block', ru:'замер-времени-блока'}) |
| `try (LOG.Unit u = LOG.unit("checkout"))` | ${en:'one line when the unit closes: name, outcome, duration, fields and the counts of warnings and errors inside, see', ru:'одна строка, когда единица закрывается: имя, исход, длительность, поля и число предупреждений и ошибок внутри, см.'} [${en:'Wide events', ru:'Wide events'}](#${en:'wide-events-one-line-per-request-canonical-log-lines', ru:'wide-events-одна-строка-на-запрос-canonical-log-lines'}) |
| `LOG.unit("import", () -> importer.run())` | ${en:'the same for a lambda: a thrown exception fails the unit and is thrown on', ru:'то же для лямбды: брошенное исключение делает единицу неудачной и пробрасывается дальше'} |
| `LOG.unit(WARN, "sync", () -> client.sync())` | ${en:'a failed unit writes `WARN` instead of `ERROR`, for failures that are expected', ru:'неудачная единица пишет `WARN` вместо `ERROR`, для ожидаемых неудач'} |
| `try (LOG.Context c = LOG.context("requestId", id))` | ${en:'adds `requestId=...` to every entry the thread logs until the block ends, see', ru:'добавляет `requestId=...` ко всем записям потока до конца блока, см.'} [${en:'Context', ru:'Контекст'}](#${en:'context-tag-the-lines-of-one-request', ru:'контекст-метка-для-строк-одного-запроса'}) |
| `LOG.addContextProvider(() -> ids)` | ${en:'adds the keys of a context of its own, such as the trace ID of OpenTelemetry, to every entry, see', ru:'добавляет ко всем записям ключи из другого контекста, например trace ID из OpenTelemetry, см.'} [${en:'Context providers', ru:'Провайдеры контекста'}](#${en:'context-providers', ru:'провайдеры-контекста'}) |
| `LOG.tag("audit").info(...)` | ${en:'an entry of a concern rather than of a class, with levels per tag in `nanolog.tags`, see', ru:'запись, относящаяся к теме, а не к классу, с уровнями по тегам в `nanolog.tags`, см.'} [${en:'Tags', ru:'Теги'}](#${en:'tags', ru:'теги'}) |
| `executor.submit(LOG.wrap(task))` | ${en:'runs the task with the context of the code that submitted it', ru:'выполняет задачу с контекстом кода, который её отправил'} |
| `LOG.catching(() -> connection.close())` | ${en:'run an action, log an exception at `ERROR` and carry on; `LOG.catching(() -> parse(s), -1)` returns a default, see', ru:'выполнить действие, записать исключение в `ERROR` и продолжить; `LOG.catching(() -> parse(s), -1)` возвращает значение по умолчанию, см.'} [${en:'Catching exceptions', ru:'Перехват исключений'}](#${en:'catching-exceptions', ru:'перехват-исключений'}) |
| `LOG.captureSystemStreams()` | ${en:'turns the lines printed to `System.out` and `System.err` into entries, see', ru:'превращает строки, напечатанные в `System.out` и `System.err`, в записи, см.'} [${en:'System.out and System.err', ru:'System.out и System.err'}](#${en:'systemout-and-systemerr', ru:'systemout-и-systemerr'}) |
| `LOG.installUncaughtExceptionHandler()` | ${en:'log the exceptions that kill threads, then pass them to the previous handler', ru:'записывать исключения, которые убивают потоки, и передавать их прежнему обработчику'} |
| `LOG.log(level, sourceClass, throwable, supplier, args)` | ${en:'the universal entry point every overload delegates to', ru:'универсальная точка входа, к которой сводятся все перегрузки'} |

${en:'Every class and method is documented in the', ru:'Каждый класс и метод описан в'} [Javadoc](${javadoc}/${stableVersion}), ${en:'which IDEs also show from the sources jar.', ru:'который IDE показывают и из jar с исходниками.'}

## ${en:'Fewer tokens: logs for AI coding agents', ru:'Меньше токенов: логи для ИИ-агентов'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

A coding agent pays for every token of output it reads, and a test run competes with the code for its context window. When an agent runs the program, ${name} switches to [agent mode](#agent-mode-nanolog-detects-coding-agents) and keeps the console short. The table counts what `mvn -q test` printed for the same runs of a small project, first without agent mode, then as Claude Code runs them (`AI_AGENT=claude-code`):<!--en-->
Кодовый агент платит за каждый токен вывода, который читает, а тестовый запуск отнимает место в окне контекста у кода. Когда программу запускает агент, ${name} переходит в [агентский режим](#агентский-режим-nanolog-распознаёт-кодовых-агентов) и сокращает вывод в консоль. В таблице — объём вывода `mvn -q test` для одних и тех же запусков небольшого проекта, сначала без агентского режима, затем так, как их запускает Claude Code (`AI_AGENT=claude-code`):<!--ru-->

| ${en:'Run', ru:'Запуск'} | ${en:'Lines', ru:'Строки'} | ${en:'Tokens, o200k_base', ru:'Токены, o200k_base'} |
|---|---|---|
| ${en:'A test logs 200 orders and fails; the error has a cause', ru:'Тест логирует 200 заказов и падает; у ошибки есть причина'} | 558 → 43 | ${en:'13,577 → 1,058 (12.8×)', ru:'13 577 → 1 058 (12,8×)'} |
| ${en:'A green test of a job that logs `INFO` and `DEBUG` for each of 300 records', ru:'Зелёный тест задачи, которая логирует `INFO` и `DEBUG` на каждую из 300 записей'} | 602 → 2 | ${en:'16,550 → 178 (93×)', ru:'16 550 → 178 (93×)'} |
| ${en:'A green test whose retry loop logs the same `WARN` 300 times', ru:'Зелёный тест, цикл повторов которого 300 раз логирует один и тот же `WARN`'} | 301 → 5 | ${en:'9,925 → 259 (38.3×)', ru:'9 925 → 259 (38,3×)'} |
| ${en:'A test fails with an exception thrown inside a stream pipeline', ru:'Тест падает с исключением изнутри цепочки стримов'} | 120 → 45 | ${en:'2,427 → 1,052 (2.3×)', ru:'2 427 → 1 052 (2,3×)'} |

Measured on 2026-11-19 on Windows 10, JDK 8, Maven 3.9 and Surefire 3.2.5, with the default settings. The counts include everything Maven printed, Surefire's report of a failure too, and agent mode leaves that report as it is; that is why the last run saves the least. o200k_base is the tokenizer of OpenAI's GPT-4o and later models; Claude tokens have not been counted yet. The project, the script and the full table with character counts are in [`src/tokens`](src/tokens) ([`RESULTS.md`](src/tokens/RESULTS.md)): `python src/tokens/measure.py` repeats the runs, and counts Claude tokens as well when an Anthropic API key is set.<!--en-->
Замерено 19.11.2026 на Windows 10, JDK 8, Maven 3.9 и Surefire 3.2.5, с настройками по умолчанию. В счёт входит всё, что напечатал Maven, в том числе отчёт Surefire о падении, а агентский режим оставляет его как есть; поэтому последний запуск экономит меньше всех. o200k_base — токенизатор OpenAI для GPT-4o и более новых моделей; токены Claude пока не подсчитаны. Проект, скрипт и полная таблица с числом символов лежат в [`src/tokens`](src/tokens) ([`RESULTS.md`](src/tokens/RESULTS.md)): `python src/tokens/measure.py` повторяет запуски и, если задан ключ Anthropic API, считает и токены Claude.<!--ru-->

${en:'Where the tokens go:', ru:'Куда уходят токены:'}

- ${en:'**The console keeps `WARN` and above** in agent mode, and the run log every level: a green run prints one line about agent mode and the run summary. See', ru:'**Консоль оставляет `WARN` и выше** в агентском режиме, а журнал запуска — все уровни: зелёный запуск печатает строку об агентском режиме и сводку. См.'} [${en:'Agent mode', ru:'Агентский режим'}](#${en:'agent-mode-nanolog-detects-coding-agents', ru:'агентский-режим-nanolog-распознаёт-кодовых-агентов'}).
- ${en:'**One line per entry,** without the date and colours: line breaks inside an entry are written as `\n`.', ru:'**Одна строка на запись,** без даты и цветов: переводы строк внутри записи пишутся как `\n`.'}
- ${en:'**Repeats collapse.** A line repeated by a loop or a retry is printed once, then as `(repeated 299 more times in 53 ms)`.', ru:'**Повторы сворачиваются.** Строка, которую повторяет цикл или ретрай, печатается один раз, затем как `(repeated 299 more times in 53 ms)`.'}
- ${en:'**Short stack traces.** Frames of the JDK, test runners, build tools and frameworks collapse, at most 20 frames per throwable are printed, and every cause is kept. See', ru:'**Короткие трассировки стека.** Кадры JDK, средств запуска тестов, инструментов сборки и фреймворков сворачиваются, печатается не больше 20 кадров на исключение, и все причины сохраняются. См.'} [${en:'Shorter stack traces', ru:'Короткие трассировки стека'}](#${en:'shorter-stack-traces', ru:'короткие-трассировки-стека'}).
- ${en:'**A summary instead of another run.** The run ends with the counts and the first error, and `latest.log` holds the rest, so the agent reads a file instead of running again with another level. See', ru:'**Сводка вместо повторного запуска.** Запуск заканчивается счётчиками и первой ошибкой, а остальное лежит в `latest.log`, поэтому агент читает файл, а не запускает всё снова с другим уровнем. См.'} [${en:'Run summary', ru:'Сводка запуска'}](#${en:'run-summary-the-part-of-the-output-that-survives-truncation', ru:'сводка-запуска-часть-вывода-которая-переживает-обрезку'}) ${en:'and', ru:'и'} [${en:'Run log', ru:'Журнал запуска'}](#${en:'run-log', ru:'журнал-запуска'}).
- ${en:'**Fewer lines from the code itself.** `LOG.once()` and `LOG.atMostEvery(...)` thin out loops and polls, and `LOG.content(...)` writes a prompt or a response as its size and hash. See', ru:'**Меньше строк от самого кода.** `LOG.once()` и `LOG.atMostEvery(...)` прореживают циклы и опросы, а `LOG.content(...)` пишет промпт или ответ как его размер и хеш. См.'} [${en:'Rate limiting', ru:'Ограничение частоты'}](#${en:'rate-limiting', ru:'ограничение-частоты'}) ${en:'and', ru:'и'} [${en:'Logging payloads safely', ru:'Безопасное логирование содержимого'}](#${en:'logging-payloads-safely', ru:'безопасное-логирование-содержимого'}).

## ${en:'Agent-driven development', ru:'Агентная разработка'}

When a coding agent (Claude Code, Codex, Copilot and others) writes and debugs the code,<!--en-->
one short loop repeats many times: edit, run, read the output, edit again.<!--en-->
${name} keeps every step of that loop cheap.<!--en-->
Когда код пишет и отлаживает кодовый агент (Claude Code, Codex, Copilot и другие),<!--ru-->
много раз повторяется один короткий цикл: правка, запуск, чтение вывода, снова правка.<!--ru-->
${name} делает каждый шаг этого цикла дешёвым.<!--ru-->

**Nanolog detects coding agents.** When an agent runs the program, the console keeps what the agent needs: `WARN`, `ERROR` and `LOG.dbg`, one line per entry, without dates and with short stack traces, and every entry still goes to `target/nanolog/latest.log`. See [Agent mode](#agent-mode-nanolog-detects-coding-agents).<!--en-->
**Nanolog распознаёт кодовых агентов.** Когда программу запускает агент, в консоли остаётся то, что нужно агенту: `WARN`, `ERROR` и `LOG.dbg`, по строке на запись, без дат и с короткими трассировками стека, а каждая запись всё равно попадает в `target/nanolog/latest.log`. См. [Агентский режим](#агентский-режим-nanolog-распознаёт-кодовых-агентов).<!--ru-->

- **One line to add logging.** No logger field and no `getLogger(Foo.class)` that has to match the class name: one import and `LOG.info(...)`. Nothing goes stale when the code is moved or the class is renamed.<!--en-->
- **Логирование добавляется одной строкой.** Нет ни поля логгера, ни `getLogger(Foo.class)`, который обязан совпадать с именем класса: один импорт и `LOG.info(...)`. Ничего не устаревает при переносе кода и переименовании класса.<!--ru-->
- **Output from the first run.** `ConsoleLogger` works without configuration and prints every level, `TRACE` included; in agent mode the console keeps `WARN` and above, and the run log every level. A `LOG.debug(...)` added while investigating a problem is visible in the very next run, with no backend to set up first.<!--en-->
- **Вывод с первого запуска.** `ConsoleLogger` работает без настройки и печатает все уровни, включая `TRACE`; в агентском режиме в консоли остаются `WARN` и выше, а в журнале запуска — все уровни. `LOG.debug(...)`, добавленный при разборе проблемы, виден уже в следующем запуске, и настраивать бэкенд для этого не нужно.<!--ru-->
- **Levels per run, without code.** `NANOLOG_LEVEL=INFO` cuts the noise once the first run is read, and `-Dnanolog.level.com.acme.payments=DEBUG` turns detail up for one package, even if the startup code switched `DEBUG` off. Agents in sandboxes and CI can always set an environment variable; a temporary code edit tends to get committed. A mistyped setting is reported with the key it was probably meant to be, so a wrong name does not pass for a working one. See [Levels and options without code](#levels-and-options-without-code).<!--en-->
- **Уровни на один запуск, без правки кода.** `NANOLOG_LEVEL=INFO` убирает лишнее, когда первый запуск уже прочитан, а `-Dnanolog.level.com.acme.payments=DEBUG` добавляет подробностей для одного пакета, даже если код при старте выключил `DEBUG`. Переменную окружения агент может задать в любой песочнице и в CI, а временная правка кода обычно попадает в коммит. О настройке с опечаткой сообщается вместе с ключом, который, вероятно, имелся в виду, поэтому неверное имя не выдаёт себя за работающее. См. [Уровни и настройки без кода](#уровни-и-настройки-без-кода).<!--ru-->
- **Lines that are easy to read back.** One entry is one line of the form `LEVEL date [SourceClass] message`, followed by the stack trace if there is one. A message with line breaks would break that rule. `nanolog.console.multiline=escape` keeps every entry, its stack trace included, on one physical line, and `indent` starts every further line of an entry with a tab, so user data cannot forge an entry. Agent mode uses `escape`. The output is simple to `grep` and to match against the sources. With `setShowLocation(true)` every line also names the method and the line of the call, `[PaymentService.charge(PaymentService.java:87)]`, so the agent jumps from the output straight to the statement. Through SLF4J, the backend's own `%file:%line` reports the same call site.<!--en-->
- **Строки, которые легко читать.** Одна запись занимает одну строку вида `LEVEL дата [КлассИсточник] сообщение`, за ней идёт трассировка стека, если она есть. Сообщение с переводами строк нарушило бы это правило. `nanolog.console.multiline=escape` держит каждую запись вместе с трассировкой на одной физической строке, а `indent` начинает каждую следующую строку записи с табуляции, так что пользовательские данные не могут подделать запись. Агентский режим использует `escape`. Такой вывод легко искать через `grep` и сопоставлять с исходниками. С `setShowLocation(true)` каждая строка ещё и называет метод и строку вызова, `[PaymentService.charge(PaymentService.java:87)]`, и агент переходит от вывода прямо к нужному оператору. Через SLF4J то же место вызова показывает `%file:%line` самого бэкенда.<!--ru-->
- **Output a script can parse.** `-Dnanolog.logger=com.nanolaba.logging.JsonLogger` switches to one JSON object per line, with the timestamp, thread, call site and stack trace as fields, ready for `jq` or a script the agent writes on the fly. See [JsonLogger](#jsonlogger).<!--en-->
- **Вывод, который разбирается скриптом.** `-Dnanolog.logger=com.nanolaba.logging.JsonLogger` переключает вывод на один JSON-объект в строке, где время, поток, место вызова и трассировка стека — отдельные поля. Такой вывод готов для `jq` или скрипта, который агент напишет на ходу. См. [JsonLogger](#jsonlogger).<!--ru-->
- **SLF4J habits carry over.** The same `{}` placeholders, and the exception may go first or last: `LOG.error("Failed to load {}", id, e)` logs the stack trace exactly as SLF4J does.<!--en-->
- **Привычки из SLF4J работают.** Те же плейсхолдеры `{}`, а исключение можно передать первым или последним: `LOG.error("Failed to load {}", id, e)` печатает трассировку стека так же, как SLF4J.<!--ru-->
- **A log call cannot fail the run.** Logging never throws: a broken `toString()`, a lazy message whose lambda throws, a `null` or a wrong number of `{}` arguments ends up as a readable message, not as an exception. In test runs, `-Dnanolog.strict=true` still reports such mismatches with the call site, so they get fixed instead of going unnoticed.<!--en-->
- **Вызов лога не может уронить запуск.** Логирование никогда не бросает исключений: сломанный `toString()`, ленивое сообщение с падающей лямбдой, `null` или неверное число аргументов для `{}` превращаются в читаемое сообщение, а не в исключение. При этом в тестовых запусках `-Dnanolog.strict=true` сообщает о таких несовпадениях вместе с местом вызова, чтобы их исправили, а не оставили незамеченными.<!--ru-->
- **Repeated lines do not crowd out the rest.** A retry loop or a full queue can print the same line thousands of times and push the line that matters out of the context window. `LOG.once()` and `LOG.atMostEvery(10, TimeUnit.SECONDS)` keep such a statement to one line, or to one line per interval, and the next one that gets through says `[skipped 241]`; see [Rate limiting](#rate-limiting).<!--en-->
- **Повторяющиеся строки не вытесняют остальное.** Цикл повторов или переполненная очередь может напечатать одну и ту же строку тысячи раз и вытолкнуть нужную строку из контекстного окна. `LOG.once()` и `LOG.atMostEvery(10, TimeUnit.SECONDS)` сводят такую строку к одной или к одной за интервал, а следующая напечатанная запись сообщает `[skipped 241]`; см. [Ограничение частоты](#ограничение-частоты).<!--ru-->
- **The whole library fits in a context window.** One package of small classes, one entry point, this README and the [Javadoc](${javadoc}/${stableVersion}). Every public method has Javadoc, so the IDE and the sources jar give the same answers as this file. An agent reads everything it needs in one pass and does not have to map a framework first.<!--en-->
- **Вся библиотека помещается в контекстное окно.** Один пакет небольших классов, одна точка входа, этот README и [Javadoc](${javadoc}/${stableVersion}). У каждого публичного метода есть Javadoc, поэтому IDE и jar с исходниками отвечают так же, как этот файл. Агент читает всё нужное за один проход, и ему не приходится сначала строить карту фреймворка.<!--ru-->
- **Green tests stay quiet.** `@ExtendWith(NanologExtension.class)` prints the log of a test only when the test fails, framed by its name, so a run of five hundred tests shows the log of the one that broke. See [Logs in tests](#logs-in-tests-only-the-failing-ones).<!--en-->
- **Зелёные тесты молчат.** `@ExtendWith(NanologExtension.class)` печатает лог теста, только если он упал, в рамке с его именем, поэтому прогон пятисот тестов показывает лог одного упавшего теста. См. [Логи в тестах](#логи-в-тестах-только-упавших).<!--ru-->
- **Capturing the log in a test takes one line.** `try (MemoryLogger log = MemoryLogger.install())` keeps the entries in memory and puts the previous logger back afterwards, so what was logged is asserted without a mocking framework; see [MemoryLogger and CompositeLogger](#memorylogger-and-compositelogger).<!--en-->
- **Для перехвата лога в тесте хватает одной строки.** `try (MemoryLogger log = MemoryLogger.install())` хранит записи в памяти и потом возвращает прежний логгер, поэтому записанное в лог проверяется без mock-фреймворка; см. [MemoryLogger и CompositeLogger](#memorylogger-и-compositelogger).<!--ru-->
- **The end of the output says what happened.** In agent mode the run ends with a summary: the counts by level, each kind of warning and error with its call site and count, the trace of the first error and the path of the full log. It survives when the agent keeps only the tail. See [Run summary](#run-summary-the-part-of-the-output-that-survives-truncation).<!--en-->
- **Конец вывода говорит, что произошло.** В агентском режиме запуск заканчивается сводкой: число записей по уровням, каждый вид предупреждений и ошибок с местом вызова и числом повторений, трассировка первой ошибки и путь к полному логу. Она сохраняется, когда агент оставляет только хвост. См. [Сводка запуска](#сводка-запуска-часть-вывода-которая-переживает-обрезку).<!--ru-->
- **The last run is kept in a file.** In agent mode, or with `nanolog.run=on`, the run also writes `target/nanolog/latest.log`, with warnings and errors in `latest.errors.log`, and prints the path. The agent reads the file instead of running a five-minute suite again for every `grep`, and follows a background server with `tail -f`. See [Run log](#run-log).<!--en-->
- **Последний запуск сохраняется в файле.** В агентском режиме или с `nanolog.run=on` запуск пишет ещё и `target/nanolog/latest.log`, а предупреждения и ошибки — в `latest.errors.log`, и печатает путь. Агент читает файл, а не перезапускает пятиминутный набор тестов ради каждого `grep`, и следит за фоновым сервером через `tail -f`. См. [Журнал запуска](#журнал-запуска).<!--ru-->
- **Debug prints do not stay behind.** A forgotten `System.out.println` looks like any other output; a `LOG.dbg` call has a name of its own. The run lists the ones that printed, `nanolog.dbg=off` silences them in production, and `NanologChecks.assertNoDbgCalls` fails a test while one is left in the sources, with the file and line the agent has to fix. See [Leftover calls](#leftover-calls).<!--en-->
- **Отладочная печать не остаётся в коде.** Забытый `System.out.println` ничем не отличается от обычного вывода, а у вызова `LOG.dbg` своё имя. Запуск перечисляет вызовы, которые печатали, `nanolog.dbg=off` заглушает их в продакшене, а `NanologChecks.assertNoDbgCalls` роняет тест, пока хоть один остаётся в исходниках, и называет файл и строку, которые агенту нужно исправить. См. [Забытые вызовы](#забытые-вызовы).<!--ru-->
- **The repository itself is ready for agents.** [AGENTS.md](AGENTS.md) describes the build, the invariants and the test requirements (JUnit plus 100% mutation coverage), so an agent can fix and extend ${name}, not only use it.<!--en-->
- **Сам репозиторий готов к работе агентов.** [AGENTS.md](AGENTS.md) описывает сборку, инварианты и требования к тестам (JUnit и 100% мутационное покрытие), поэтому агент может не только использовать ${name}, но и чинить и расширять его.<!--ru-->

### ${en:'Agent mode: Nanolog detects coding agents', ru:'Агентский режим: Nanolog распознаёт кодовых агентов'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

Coding agents read program output through a narrow window: a long output is cut down to its head and tail or summarised, and agents rarely ask for the rest. When the environment names a coding agent, ${name} keeps its own console short and writes the full log to a file.<!--en-->
Кодовые агенты читают вывод программы через узкое окно: длинный вывод урезается до начала и конца или пересказывается, а остальное агенты запрашивают редко. Когда окружение называет кодового агента, ${name} делает свою консоль короткой, а полный лог пишет в файл.<!--ru-->

${en:'A test that logs 200 orders and fails on order 150 prints this under Claude Code:', ru:'Тест, который логирует 200 заказов и падает на 150-м, печатает под Claude Code вот что:'}

```
[NANOLOG] Agent mode (AI_AGENT=claude-code): the console shows WARN, ERROR and every LOG.dbg; the full log is in /home/me/shop/target/nanolog/runs/20261117-101502-311-4821.log, WARN and ERROR also in /home/me/shop/target/nanolog/runs/20261117-101502-311-4821.errors.log; nanolog.agent=off turns it off
WARN [OrderService] Order 150: stock of SKU A-7 below the threshold
ERROR [OrderServiceTest] The batch failed java.lang.IllegalStateException: Cannot reserve stock for order 150\n	at com.acme.shop.OrderService.reserve(OrderService.java:32)\n	at com.acme.shop.OrderService.process(OrderService.java:20)\n	at com.acme.shop.OrderServiceTest.processesTheDailyBatch(OrderServiceTest.java:16)\n	... 71 frames excluded\nCaused by: java.sql.SQLException: Deadlock found when trying to get lock; order 150\n	at com.acme.shop.OrderService.update(OrderService.java:38)\n	at com.acme.shop.OrderService.reserve(OrderService.java:30)\n	... 73 more
```

The run then ends with the [run summary](#run-summary-the-part-of-the-output-that-survives-truncation).<!--en-->
Затем запуск заканчивается [сводкой](#сводка-запуска-часть-вывода-которая-переживает-обрезку).<!--ru-->

Without agent mode the same run printed 530 lines of ${name} output: three lines per order with dates, then the stack trace with every frame of JUnit, Surefire and reflection. The whole `mvn -q test` output went from 558 lines, 39,355 characters and 13,577 tokens to 43 lines, 3,843 characters and 1,058 tokens, the run summary included (JDK 8, Maven 3.9, Surefire 3.2.5, on Windows, with long absolute paths), and `latest.log` still holds all 451 entries. The project of this example is in [`src/tokens`](src/tokens); [Fewer tokens](#fewer-tokens-logs-for-ai-coding-agents) has three more runs.<!--en-->
Без агентского режима тот же запуск напечатал 530 строк вывода ${name}: по три строки на заказ с датами, затем трассировку стека со всеми кадрами JUnit, Surefire и рефлексии. Весь вывод `mvn -q test` сократился с 558 строк, 39 355 символов и 13 577 токенов до 43 строк, 3 843 символов и 1 058 токенов, вместе со сводкой запуска (JDK 8, Maven 3.9, Surefire 3.2.5, Windows, длинные абсолютные пути), а в `latest.log` по-прежнему лежат все 451 запись. Проект из этого примера лежит в [`src/tokens`](src/tokens); ещё три запуска — в разделе [Меньше токенов](#меньше-токенов-логи-для-ии-агентов).<!--ru-->

- **Detection.** Agent mode is on when one of these variables is set to anything but `0`, `false`, `no` or `off`: `AI_AGENT` and `AGENT`, the convention proposed in [agents.md #136](https://github.com/agentsmd/agents.md/issues/136), then `CLAUDECODE`, `GEMINI_CLI`, `CODEX_SANDBOX`, `CODEX_CI`, `CURSOR_AGENT`, `VSCODE_AGENT` and `CLINE_ACTIVE`. Claude Code, for one, sets `AI_AGENT` and `CLAUDECODE`. Environment variables are lost across `sudo`, `docker run` and `ssh`, so detection is a hint: `nanolog.agent=on` or `off` (`NANOLOG_AGENT`) decides instead.<!--en-->
- **Распознавание.** Агентский режим включается, когда одна из этих переменных задана и не равна `0`, `false`, `no` или `off`: `AI_AGENT` и `AGENT`, соглашение, предложенное в [agents.md #136](https://github.com/agentsmd/agents.md/issues/136), затем `CLAUDECODE`, `GEMINI_CLI`, `CODEX_SANDBOX`, `CODEX_CI`, `CURSOR_AGENT`, `VSCODE_AGENT` и `CLINE_ACTIVE`. Claude Code, например, задаёт `AI_AGENT` и `CLAUDECODE`. Переменные окружения теряются при `sudo`, `docker run` и `ssh`, поэтому распознавание — лишь подсказка: `nanolog.agent=on` или `off` (`NANOLOG_AGENT`) решает вместо него.<!--ru-->
- **The console.** `WARN` and above, unless `nanolog.level` or a package rule says otherwise. One line per entry, with line breaks written as `\n`, a Windows line separator as `\r\n` (`nanolog.console.multiline=escape`), no date and no colours, and consecutive repeats collapsed into one line with a count (`nanolog.console.repeats=collapse`), unless these settings are given. Stack traces collapse runs of frames of the JDK, Kotlin, test runners, build tools and common frameworks and servers (Spring, Tomcat, Jetty, Netty, Hibernate), print at most 20 frames per throwable and keep every cause; `nanolog.exception.exclude` and `nanolog.exception.maxFrames` replace these defaults.<!--en-->
- **Консоль.** `WARN` и выше, если `nanolog.level` или правило пакета не говорят иного. Одна строка на запись, переводы строк пишутся как `\n`, разделитель строк Windows — как `\r\n` (`nanolog.console.multiline=escape`), без даты и без цветов, а подряд идущие повторы сворачиваются в одну строку со счётчиком (`nanolog.console.repeats=collapse`), если эти настройки не заданы. Трассировки стека сворачивают подряд идущие кадры JDK, Kotlin, средств запуска тестов, систем сборки и распространённых фреймворков и серверов (Spring, Tomcat, Jetty, Netty, Hibernate), печатают не больше 20 кадров на исключение и сохраняют каждую причину; `nanolog.exception.exclude` и `nanolog.exception.maxFrames` заменяют эти значения.<!--ru-->
- **`LOG.dbg` prints whatever the levels**: the agent put it there to see the value. `nanolog.dbg=off` still silences it.<!--en-->
- **`LOG.dbg` печатает при любых уровнях**: агент вставил его, чтобы увидеть значение. `nanolog.dbg=off` по-прежнему его заглушает.<!--ru-->
- **The run log is on** and keeps every level, the date, the thread, the call site and the whole stack trace, so the agent reads it instead of running again with another level; `nanolog.run=off` turns it off. See [Run log](#run-log).<!--en-->
- **Журнал запуска включён** и хранит все уровни, дату, поток, место вызова и полную трассировку стека, поэтому агент читает его, а не перезапускает программу с другим уровнем; `nanolog.run=off` его выключает. См. [Журнал запуска](#журнал-запуска).<!--ru-->
- **One line says what happened**, once per JVM: what turned agent mode on, what the console shows and where the full log is.<!--en-->
- **Одна строка сообщает, что произошло**, один раз на JVM: что включило агентский режим, что показывает консоль и где полный лог.<!--ru-->
- **The run ends with a summary**: see [Run summary](#run-summary-the-part-of-the-output-that-survives-truncation); `nanolog.summary=off` turns it off.<!--en-->
- **Запуск заканчивается сводкой**: см. [Сводка запуска](#сводка-запуска-часть-вывода-которая-переживает-обрезку); `nanolog.summary=off` её выключает.<!--ru-->
- **Only Nanolog's own output changes.** The defaults above apply to the default logger, `ConsoleLogger` or `JsonLogger`; a logger set with `LOG.init(logger)`, or another class named by `nanolog.logger`, keeps its settings. The levels do not change either: `LOG.isDebugEnabled()` and the lazy arguments of a call answer as without agent mode, so code that checks a level behaves the same under an agent. A test that reads what the default logger prints to the console sees less under an agent: capture the entries with `MemoryLogger`, or set `NANOLOG_AGENT=off` for the test JVM (`environmentVariables` of Surefire).<!--en-->
- **Меняется только собственный вывод Nanolog.** Эти значения действуют на логгер по умолчанию, `ConsoleLogger` или `JsonLogger`; логгер, заданный через `LOG.init(logger)`, или другой класс из `nanolog.logger` сохраняет свои настройки. Не меняются и уровни: `LOG.isDebugEnabled()` и ленивые аргументы вызова отвечают так же, как без агентского режима, поэтому код, который проверяет уровень, под агентом ведёт себя так же. Тест, который читает то, что логгер по умолчанию печатает в консоль, под агентом видит меньше: перехватывайте записи через `MemoryLogger` или задайте `NANOLOG_AGENT=off` для тестовой JVM (`environmentVariables` у Surefire).<!--ru-->

### ${en:'Run summary: the part of the output that survives truncation', ru:'Сводка запуска: часть вывода, которая переживает обрезку'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

A coding agent often sees only the start and the end of a long output. Claude Code shows a failing command as an excerpt of its head and tail, about 10,000 characters ([tools reference](https://code.claude.com/docs/en/tools-reference)); Cursor cuts the output of a command at 20,000 characters ([forum](https://forum.cursor.com/t/the-20k-40k-character-loss-zone-for-the-shell-tool/163017)); agents add `| tail -30` themselves and rarely ask for the rest ([Agents Don't Paginate](https://arxiv.org/abs/2608.26130)). The error that matters is often in the part that was dropped. ${name} prints a summary of the run last, where it survives.<!--en-->
Кодовый агент часто видит только начало и конец длинного вывода. Claude Code показывает упавшую команду фрагментом из её начала и конца, около 10 000 символов ([справочник инструментов](https://code.claude.com/docs/en/tools-reference)); Cursor обрезает вывод команды на 20 000 символах ([форум](https://forum.cursor.com/t/the-20k-40k-character-loss-zone-for-the-shell-tool/163017)); агенты сами дописывают `| tail -30` и редко просят остальное ([Agents Don't Paginate](https://arxiv.org/abs/2608.26130)). Нужная ошибка часто оказывается в отброшенной части. ${name} печатает сводку запуска последней, там, где она сохраняется.<!--ru-->

```
[NANOLOG] Run summary: 451 entries: 1 ERROR, 1 WARN, 150 INFO, 299 DEBUG; the full log is in /home/me/shop/target/nanolog/runs/20261117-101502-311-4821.log
[NANOLOG]   ERROR x1 at OrderServiceTest.java:19: The batch failed
[NANOLOG]       java.lang.IllegalStateException: Cannot reserve stock for order 150
[NANOLOG]           at com.acme.shop.OrderService.reserve(OrderService.java:32)
[NANOLOG]           at com.acme.shop.OrderService.process(OrderService.java:20)
[NANOLOG]           at com.acme.shop.OrderServiceTest.processesTheDailyBatch(OrderServiceTest.java:16)
[NANOLOG]           ... 71 frames excluded
[NANOLOG]       Caused by: java.sql.SQLException: Deadlock found when trying to get lock; order 150
[NANOLOG]           at com.acme.shop.OrderService.update(OrderService.java:38)
[NANOLOG]           at com.acme.shop.OrderService.reserve(OrderService.java:30)
[NANOLOG]           ... 73 more
[NANOLOG]   WARN x1 at OrderService.java:27: Order {}: stock of SKU A-7 below the threshold
```

In the failing `mvn -q test` run of the [agent mode](#agent-mode-nanolog-detects-coding-agents) example these 12 lines end 14 lines before the end of the output, after Surefire has printed the failure, so `tail -30` keeps them.<!--en-->
В упавшем запуске `mvn -q test` из примера [агентского режима](#агентский-режим-nanolog-распознаёт-кодовых-агентов) эти 12 строк заканчиваются за 14 строк до конца вывода, после того как Surefire напечатал ошибку, поэтому `tail -30` их сохраняет.<!--ru-->

- **What.** The number of entries by level; each kind of warning and error, by level and call site, with how often it was logged, errors first: a call site that builds its messages from data, `"Merge of #" + id + " failed"`, is one kind with its first message and `(4 different messages)`, and a call with an explicit class, which has no call site, is told apart by its message; a message of several lines shows its first line, followed by `...`; the stack trace of the first error with an exception, without the frames of agent mode and with every cause; the `LOG.dbg` calls that ran; the path of the [run log](#run-log). It counts the entries that reach the logger: a warning that a test provokes inside `MemoryLogger.capture()`, which keeps it from the logger, is neither in the summary nor in the run log.<!--en-->
- **Что.** Число записей по уровням; каждый вид предупреждений и ошибок — по уровню и месту вызова — с числом повторений, сначала ошибки: место вызова, которое собирает сообщения из данных, `"Merge of #" + id + " failed"`, — один вид с первым сообщением и пометкой `(4 different messages)`, а вызов с явным классом, у которого нет места вызова, различается по сообщению; у сообщения из нескольких строк показывается первая строка и `...`; трассировка стека первой ошибки с исключением, без кадров агентского режима и со всеми причинами; вызовы `LOG.dbg`, которые выполнились; путь к [журналу запуска](#журнал-запуска). Сводка считает записи, которые дошли до логгера: предупреждения, которое тест вызывает внутри `MemoryLogger.capture()`, не пропускающего его к логгеру, нет ни в сводке, ни в журнале запуска.<!--ru-->
- **Bounded.** At most 10 kinds and 15 lines of the trace are shown; the rest is counted (`... and 7 more kinds of WARN and ERROR`). Counting keeps no entry, only a counter per level and per kind, for at most 1000 kinds, and costs nothing while the summary is off.<!--en-->
- **Ограничена.** Показывается не больше 10 видов и 15 строк трассировки; остальное подсчитывается (`... and 7 more kinds of WARN and ERROR`). Подсчёт не хранит записей, только счётчик на уровень и на вид, не больше 1000 видов, и ничего не стоит, пока сводка выключена.<!--ru-->
- **Every kind in a file.** With the [run log](#run-log) on, the summary also writes every kind it counted to `runs/<time>-<pid>.summary.log`, one line each as above, ordered by level and call site rather than as the run went, so the lists of two runs can be compared with `diff`. When kinds are left out, the line that counts them names the list: `... and 7 more kinds of WARN and ERROR; the full list is in /home/me/shop/target/nanolog/latest.summary.log`. `latest.summary.log` is a second name of the list of the run in `latest.log`, once that run has ended; a JVM in which Surefire, Failsafe or Gradle runs tests names its own list, as it names its own log.<!--en-->
- **Все виды в файле.** Со включённым [журналом запуска](#журнал-запуска) сводка пишет ещё и все подсчитанные виды в `runs/<время>-<pid>.summary.log`, по строке на вид, как выше, в порядке уровня и места вызова, а не в порядке запуска, поэтому списки двух запусков можно сравнить через `diff`. Когда часть видов не показана, строка, которая их считает, называет список: `... and 7 more kinds of WARN and ERROR; the full list is in /home/me/shop/target/nanolog/latest.summary.log`. `latest.summary.log` — второе имя списка того запуска, что записан в `latest.log`, когда этот запуск закончился; JVM, в которой тесты запускает Surefire, Failsafe или Gradle, называет свой список, как называет свой журнал.<!--ru-->
- **When.** `nanolog.summary=on` (`NANOLOG_SUMMARY`), the default in agent mode; `errors` prints it only after a `WARN` or `ERROR`; `off`, the default otherwise. It goes to `System.err` once per JVM: when JUnit has run the tests of that JVM, through `SummarySessionListener` and `SummaryListener`, which the JUnit Platform loads by itself in Maven, Gradle and the IDE; otherwise when the JVM exits. A Surefire fork that runs several test classes, with `forkCount` above 1, prints one summary after the last of them, in one piece, so the summaries of two forks that end together do not mix. Surefire drops what a shutdown hook of its forked JVM prints, which is why test runs need the listeners. Gradle prints the output of tests only with `testLogging.showStandardStreams = true`.<!--en-->
- **Когда.** `nanolog.summary=on` (`NANOLOG_SUMMARY`) — по умолчанию в агентском режиме; `errors` печатает её только после `WARN` или `ERROR`; `off` — по умолчанию в остальных случаях. Она выводится в `System.err` один раз на JVM: когда JUnit выполнил тесты этой JVM, через `SummarySessionListener` и `SummaryListener`, которые платформа JUnit сама загружает в Maven, Gradle и IDE; иначе при завершении JVM. Форк Surefire, который выполняет несколько тестовых классов (`forkCount` больше 1), печатает одну сводку после последнего из них, одним куском, поэтому сводки двух форков, закончивших одновременно, не перемешиваются. Surefire отбрасывает то, что печатает shutdown hook его отдельной JVM, поэтому тестовым запускам и нужны слушатели. Gradle печатает вывод тестов только с `testLogging.showStandardStreams = true`.<!--ru-->
- **A jar of your own.** `SummaryListener` and `SummarySessionListener` are compiled against the JUnit Platform Launcher API, which a test run provides, so ${name} still has no dependencies. A shaded jar that keeps `META-INF/services/org.junit.platform.launcher.TestExecutionListener` or `org.junit.platform.launcher.LauncherSessionListener` must keep the class it names too, or JUnit stops with a `ServiceConfigurationError`.<!--en-->
- **Собственный jar.** `SummaryListener` и `SummarySessionListener` скомпилированы против API JUnit Platform Launcher, который предоставляет тестовый запуск, поэтому у ${name} по-прежнему нет зависимостей. Shaded-jar, сохраняющий `META-INF/services/org.junit.platform.launcher.TestExecutionListener` или `org.junit.platform.launcher.LauncherSessionListener`, должен сохранить и названный там класс, иначе JUnit остановится с `ServiceConfigurationError`.<!--ru-->

### ${en:'Run log', ru:'Журнал запуска'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

With `nanolog.run=on` (`NANOLOG_RUN=on`) every entry of the run also goes to a file at a predictable place. A coding agent reads the output of the last run there instead of running the program again with another filter, and follows a server that runs in the background. Off by default, on in [agent mode](#agent-mode-nanolog-detects-coding-agents).<!--en-->
С `nanolog.run=on` (`NANOLOG_RUN=on`) каждая запись запуска попадает ещё и в файл в предсказуемом месте. Кодовый агент читает там вывод последнего запуска, а не перезапускает программу с другим фильтром, и следит за сервером, работающим в фоне. По умолчанию выключено, в [агентском режиме](#агентский-режим-nanolog-распознаёт-кодовых-агентов) включено.<!--ru-->

```
[NANOLOG] Run log: /home/me/shop/target/nanolog/latest.log; WARN and ERROR also in /home/me/shop/target/nanolog/latest.errors.log
```

- **Where.** `target/nanolog` in a Maven module, `build/nanolog` in a Gradle one, `.nanolog` otherwise, which gets a `.gitignore`; `nanolog.run.dir` names another directory. The absolute path is printed when `LOG` initialises, because search tools often skip ignored `*.log` files.<!--en-->
- **Где.** `target/nanolog` в модуле Maven, `build/nanolog` в модуле Gradle, иначе `.nanolog`, в который кладётся `.gitignore`; `nanolog.run.dir` задаёт другой каталог. Абсолютный путь печатается при инициализации `LOG`, потому что инструменты поиска часто пропускают игнорируемые файлы `*.log`.<!--ru-->
- **What.** `latest.log` holds every entry, with the thread and the call site; `nanolog.run.format=json` writes the lines of `JsonLogger` instead, ready for `jq`. `latest.errors.log` holds each `WARN` and `ERROR` as one line, cheap to follow with `tail -f`. With the run summary on, `latest.summary.log` lists every kind of warning and error of the run at its end, see [Run summary](#run-summary-the-part-of-the-output-that-survives-truncation).<!--en-->
- **Что.** В `latest.log` попадает каждая запись, с потоком и местом вызова; `nanolog.run.format=json` пишет вместо этого строки `JsonLogger`, готовые для `jq`. В `latest.errors.log` каждая запись `WARN` и `ERROR` занимает одну строку, и за этим файлом удобно следить через `tail -f`. Со включённой сводкой запуска `latest.summary.log` в конце запуска перечисляет все виды предупреждений и ошибок, см. [Сводка запуска](#сводка-запуска-часть-вывода-которая-переживает-обрезку).<!--ru-->
- **History.** Each run writes `runs/<time>-<pid>.log`, and `latest.log` is a second name of the newest one, a hard link, so it shows the run live and is written once. The last 10 runs are kept (`nanolog.run.keep`); a file stops at 100 MB with a note (`nanolog.run.maxSize`). Where hard links are not supported, or once a later run in the same directory has taken `latest.log`, the path of the run's own file is printed instead of `latest.log`, in the run summary as well. A JVM in which Maven Surefire or Failsafe or Gradle runs tests prints the path of its own file from the start: several of them may run in one module, side by side (`forkCount` above 1) or one after another (`reuseForks=false`), and each takes `latest.log` from the one before, so each fork names the file with its own entries.<!--en-->
- **История.** Каждый запуск пишет `runs/<время>-<pid>.log`, а `latest.log` — второе имя самого нового из них, жёсткая ссылка, поэтому он показывает запуск вживую и пишется один раз. Хранятся последние 10 запусков (`nanolog.run.keep`); файл обрывается на 100 МБ с пометкой (`nanolog.run.maxSize`). Там, где жёстких ссылок нет, или когда `latest.log` уже занял более поздний запуск в том же каталоге, вместо `latest.log` печатается путь к файлу самого запуска, и в сводке запуска тоже. JVM, в которой тесты запускает Maven Surefire, Failsafe или Gradle, с самого начала печатает путь к своему файлу: в одном модуле их может работать несколько, рядом (`forkCount` больше 1) или друг за другом (`reuseForks=false`), и каждая забирает `latest.log` у предыдущей, поэтому каждый форк называет файл со своими записями.<!--ru-->
- **Levels.** The run log reads `nanolog.level` and the package rules as `ConsoleLogger` does, and `LOG.setLevel` changes both. It takes what the logger takes, so `LOG.isDebugEnabled()` and the lazy arguments of a call answer as they would without it: a logger installed with `LOG.init(logger)` or named by `nanolog.logger`, `Slf4jLogger` for one, decides the levels of the file too. Only the short console of [agent mode](#agent-mode-nanolog-detects-coding-agents) leaves the rest to the file, which keeps the levels the console has without agent mode.<!--en-->
- **Уровни.** Журнал запуска читает `nanolog.level` и правила пакетов так же, как `ConsoleLogger`, а `LOG.setLevel` меняет оба. Он берёт то, что берёт логгер, поэтому `LOG.isDebugEnabled()` и ленивые аргументы вызова отвечают так же, как без него: логгер, установленный через `LOG.init(logger)` или заданный в `nanolog.logger`, например `Slf4jLogger`, определяет уровни и для файла. Только короткая консоль [агентского режима](#агентский-режим-nanolog-распознаёт-кодовых-агентов) оставляет остальное файлу, у которого уровни консоли без агентского режима.<!--ru-->
- **Safe.** An entry is on disk as soon as it is logged, even if the JVM is killed. An I/O error prints one `[NANOLOG]` line and turns the run log off; logging goes on. The file is opened once per JVM: `LOG.init()` keeps writing it, and so does a logger installed with `LOG.init(logger)` or `MemoryLogger.install()`, Spring's `Slf4jLogger` for one: the run log is added to it, and `LOG.getLogger()` returns a `CompositeLogger` of both. A decorator that passes each entry on to the logger `LOG.getLogger()` returned, and `MemoryLogger.installForwarding()`, write each entry to the file once.<!--en-->
- **Надёжность.** Запись оказывается на диске сразу, как только попала в лог, даже если JVM будет убита. Ошибка ввода-вывода печатает одну строку `[NANOLOG]` и выключает журнал запуска; логирование продолжается. Файл открывается один раз на JVM: `LOG.init()` продолжает писать в него, и так же пишет логгер, установленный через `LOG.init(logger)` или `MemoryLogger.install()`, например `Slf4jLogger` из Spring: к нему добавляется журнал запуска, а `LOG.getLogger()` возвращает `CompositeLogger` из обоих. Декоратор, который передаёт каждую запись дальше логгеру из `LOG.getLogger()`, и `MemoryLogger.installForwarding()` пишут каждую запись в файл один раз.<!--ru-->

### ${en:'Using Nanolog from a coding agent', ru:'Nanolog в работе кодового агента'}

Два файла дают агенту правила работы с библиотекой в нескольких сотнях строк, без пояснений этого README:<!--ru-->
Two files give an agent the rules of the library in a few hundred lines, without the explanations of this README:<!--en-->

- [`llms.txt`](llms.txt) — ${en:'a cheat sheet in the [llms.txt](https://llmstxt.org/) format: coordinates, call shapes, the rules that differ from SLF4J, settings without code changes and the test recipe.', ru:'шпаргалка в формате [llms.txt](https://llmstxt.org/): координаты, формы вызова, правила, отличающиеся от SLF4J, настройки без правки кода и рецепт для тестов.'}
- [`skills/nanolog/SKILL.md`](skills/nanolog/SKILL.md) — ${en:'the same guide as an agent skill, which the agent loads by itself when it works on code that logs through Nanolog. Install it in one of three ways:', ru:'то же руководство в виде навыка агента: агент сам подгружает его, когда работает с кодом, пишущим в лог через Nanolog. Установить его можно тремя способами:'}

  - ${en:'Claude Code, as a plugin that stays up to date:', ru:'Claude Code, как плагин, который обновляется сам:'}

    ```
    /plugin marketplace add nanolaba/nanolog
    /plugin install nanolog@nanolog
    ```

  - ${en:'Claude Code, Cursor, Codex and the other agents that read Agent Skills, with the [`skills`](https://github.com/vercel-labs/skills) CLI (`-a claude-code` limits it to one agent):', ru:'Claude Code, Cursor, Codex и другие агенты, читающие Agent Skills, через CLI [`skills`](https://github.com/vercel-labs/skills) (`-a claude-code` ограничивает установку одним агентом):'}

    ```
    npx skills add nanolaba/nanolog
    ```

  - ${en:'By hand, into the project:', ru:'Вручную, в проект:'}

    ```
    mkdir -p .claude/skills/nanolog
    curl -fsSL https://raw.githubusercontent.com/nanolaba/nanolog/main/skills/nanolog/SKILL.md -o .claude/skills/nanolog/SKILL.md
    ```

    ${en:'Other agents that support the Agent Skills format read the same file from their own skills directory.', ru:'Другие агенты, поддерживающие формат Agent Skills, читают тот же файл из своего каталога навыков.'}

${en:'Both files also tell the agent what to do when Nanolog itself misbehaves: report it to you, with a link to the issue forms and what to include, and never open an issue on its own. In a project without Nanolog the skill may suggest it, but never adds the dependency without your consent.', ru:'Оба файла также говорят агенту, что делать, если сам Nanolog ведёт себя неправильно: сообщить вам, дать ссылку на формы issue и перечислить, что приложить, и никогда не открывать issue самому. В проекте без Nanolog навык может его предложить, но никогда не добавит зависимость без вашего согласия.'}

Можно ничего не устанавливать: достаточно нескольких строк в собственном `AGENTS.md` (или `CLAUDE.md`) проекта:<!--ru-->
Or skip the installation: a few lines in `AGENTS.md` (or `CLAUDE.md`) of the project are enough:<!--en-->

```markdown
## Logging

This project logs with Nanolog (com.nanolaba:nanolog). Before adding or changing log calls,
read https://raw.githubusercontent.com/nanolaba/nanolog/main/llms.txt

For temporary debug output use LOG.dbg(...), never System.out.println, and remove every
LOG.dbg call before you finish: the test with NanologChecks.assertNoDbgCalls must pass.
```

Оба файла генерируются из `agent-guide.src.md` при каждой сборке: версии берутся из `pom.xml`, код —<!--ru-->
из компилируемых примеров, а CI падает, если файлы устарели. Идеи о том, что ещё облегчило бы работу агентов,<!--ru-->
собраны под [меткой `agents`](${repo}/issues?q=is%3Aissue+label%3Aagents).<!--ru-->
Both files are generated from `agent-guide.src.md` on every build: the versions come from `pom.xml`, the code<!--en-->
from the compiled examples, and CI fails when they are stale. Ideas for making the library easier for agents<!--en-->
are collected under the [`agents` label](${repo}/issues?q=is%3Aissue+label%3Aagents).<!--en-->

## ${en:'Print debugging: `LOG.dbg` prints the expression and its value', ru:'Отладочная печать: `LOG.dbg` печатает выражение и его значение'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

`LOG.dbg(...)` is the Java counterpart of Rust's `dbg!` and Python's icecream: it prints the expression as written in the source, its value and type, and the file and line of the call, and returns the value, so it wraps any expression in place.<!--en-->
`LOG.dbg(...)` — для Java то же, что `dbg!` в Rust и icecream в Python: печатает выражение так, как оно записано в исходнике, его значение и тип, файл и строку вызова и возвращает значение, поэтому им можно обернуть любое выражение прямо на месте.<!--ru-->

${widget:import(path='${examples}/PrintDebugging.java', region='dbg', wrap='true')}

```
DBG 17.11.2026 10:15:02 [PrintDebugging.checkout(PrintDebugging.java:21)] order.total() = 129.90 (BigDecimal)
DBG 17.11.2026 10:15:02 [PrintDebugging.checkout(PrintDebugging.java:22)] user = "alice", cart.size() = 3 (Integer)
DBG 17.11.2026 10:15:02 [PrintDebugging.checkout(PrintDebugging.java:23)] reached
```

- The text comes from the source file of the call site. The source directory is derived from where the class was loaded: `target/classes` leads to `src/main/java`, `target/test-classes` to `src/test/java`, Gradle's `build/classes/java/main` to `src/main/java`, and the same for Kotlin; for other layouts the usual directories of the working directory are tried. `nanolog.sources=dir1,dir2` names them explicitly. Each call site is read once, arguments spread over several lines included.<!--en-->
- Текст берётся из исходного файла места вызова. Каталог исходников выводится из того, откуда загружен класс: `target/classes` ведёт к `src/main/java`, `target/test-classes` — к `src/test/java`, `build/classes/java/main` из Gradle — к `src/main/java`, и так же для Kotlin; для других структур проекта пробуются обычные каталоги рабочей директории. `nanolog.sources=dir1,dir2` задаёт их явно. Каждое место вызова читается один раз, включая аргументы, разнесённые на несколько строк.<!--ru-->
- Without the text only the values are printed, never an error: when no source directory is found (a production jar), the source file is newer than its class, or the line holds two `dbg` calls, since Java reports the line of a call but not its column.<!--en-->
- Без текста печатаются только значения, и ошибки не бывает: когда каталог исходников не найден (jar в продакшене), исходник новее своего класса или на строке два вызова `dbg`, ведь Java сообщает строку вызова, но не колонку.<!--ru-->
- Strings and chars are quoted and escaped, so a value never breaks the line; collections, maps and arrays are printed item by item, objects without a `toString()` of their own field by field (see below); other values are followed by their type, unless their text starts with it, as `Invoice{id=17, ...}` does.<!--en-->
- Строки и символы печатаются в кавычках и с экранированием, поэтому значение никогда не разрывает строку; коллекции, словари и массивы печатаются поэлементно, объекты без собственного `toString()` — поле за полем (см. ниже); за остальными значениями следует их тип, если их текст не начинается с него, как у `Invoice{id=17, ...}`.<!--ru-->
- It is a `DEBUG` entry: `nanolog.level` and package rules apply, and a level that is off everywhere costs no stack walk. In [agent mode](#agent-mode-nanolog-detects-coding-agents) it prints whatever the levels. `ConsoleLogger` marks it `DBG` and prints the call site even without `showLocation`, `JsonLogger` adds `"dbg":true`, any logger tells it apart with `LogEntry.isDbg()`. `LOG.once().dbg(...)` and `LOG.atMostEvery(...).dbg(...)` limit it like any other statement.<!--en-->
- Это запись уровня `DEBUG`: действуют `nanolog.level` и правила пакетов, а уровень, выключенный везде, не требует обхода стека. В [агентском режиме](#агентский-режим-nanolog-распознаёт-кодовых-агентов) она печатается при любых уровнях. `ConsoleLogger` помечает её `DBG` и печатает место вызова даже без `showLocation`, `JsonLogger` добавляет `"dbg":true`, любой логгер отличает её по `LogEntry.isDbg()`. `LOG.once().dbg(...)` и `LOG.atMostEvery(...).dbg(...)` ограничивают её, как любую другую строку.<!--ru-->
- `dbg` is meant to be temporary: a debug print that names its values replaces a series of `System.out.println` calls, and its distinct name lets ${name} find the calls left behind, see [Leftover calls](#leftover-calls).<!--en-->
- `dbg` задуман как временный: отладочная печать, называющая свои значения, заменяет серию вызовов `System.out.println`, а по её особому имени ${name} находит забытые вызовы, см. [Забытые вызовы](#забытые-вызовы).<!--ru-->

Rust has had `dbg!` in its standard library since 1.32 (2019), Python has had `f"{x=}"` since 3.8 and the icecream package; the JDK has nothing comparable, and an IDE template such as IntelliJ's `soutv` types the name into the code, where it goes stale on a rename.<!--en-->
В Rust `dbg!` входит в стандартную библиотеку с версии 1.32 (2019), в Python есть `f"{x=}"` с версии 3.8 и пакет icecream; в JDK ничего похожего нет, а шаблон IDE вроде `soutv` в IntelliJ вписывает имя в код, где оно устаревает при переименовании.<!--ru-->

### ${en:'Objects without `toString()`', ru:'Объекты без `toString()`'}

An object whose class has no `toString()` of its own is printed field by field, not as `Invoice@2f92e0f4`. Since 1.4.<!--en-->
Объект, у класса которого нет собственного `toString()`, печатается поле за полем, а не как `Invoice@2f92e0f4`. Начиная с 1.4.<!--ru-->

${widget:import(path='${examples}/PrintDebugging.java', region='dump', wrap='true')}

```
DBG 17.11.2026 10:15:02 [PrintDebugging.inspect(PrintDebugging.java:52)] invoice = Invoice{id=17, lines=[Line{sku="A-1", qty=2}, Line{sku="B-7", qty=1}], customer=Customer{name="alice", password=***}}
```

- A class with a `toString()` of its own keeps it and is never reflected into. Classes of the JDK are never reflected into either: collections, maps, arrays and `Optional` are walked through their public API, any other JDK value prints its `toString()`. So the dump needs no `--add-opens` on JDK 17+ and cannot fail with `InaccessibleObjectException`, unlike Commons Lang `ReflectionToStringBuilder`. A field that cannot be read prints `<inaccessible>`.<!--en-->
- Класс с собственным `toString()` сохраняет его, и в него никогда не заглядывают через рефлексию. В классы JDK тоже: коллекции, словари, массивы и `Optional` обходятся через их публичный API, любое другое значение JDK печатает свой `toString()`. Поэтому дампу не нужен `--add-opens` на JDK 17+, и он не может упасть с `InaccessibleObjectException`, в отличие от `ReflectionToStringBuilder` из Commons Lang. Поле, которое не удалось прочитать, печатается как `<inaccessible>`.<!--ru-->
- The fields of superclasses come first; static and synthetic fields are left out. A record prints its components, `Point[x=1, ys=[2, 3]]`, with arrays element by element, which its generated `toString()` does not do.<!--en-->
- Поля суперклассов идут первыми; статические и синтетические поля пропускаются. Запись (record) печатает свои компоненты, `Point[x=1, ys=[2, 3]]`, причём массивы поэлементно, чего её сгенерированный `toString()` не делает.<!--ru-->
- Every limit says what it left out: an object met again inside itself prints `<cycle: Invoice>`; a container more than three levels below the printed value prints `Customer{... (+2 more)}`; a collection, map or array shows at most 100 items, then `... (+900 more)`; a string inside a dump shows at most 1000 characters, then `... (+24 more chars)`. A string passed to `dbg` itself is never cut.<!--en-->
- Каждое ограничение сообщает, что отброшено: объект, встреченный внутри самого себя, печатается как `<cycle: Invoice>`; контейнер глубже трёх уровней от печатаемого значения — как `Customer{... (+2 more)}`; у коллекции, словаря или массива показывается не больше 100 элементов, затем `... (+900 more)`; у строки внутри дампа — не больше 1000 символов, затем `... (+24 more chars)`. Строка, переданная в сам `dbg`, не обрезается никогда.<!--ru-->
- A field or a string map key whose name contains `password`, `passwd`, `secret`, `token`, `apiKey`, `credential` or `authorization`, ignoring case, `_` and `-`, prints `***` unless it is `null`, so `accessToken` and `X-Api-Key` are hidden too.<!--en-->
- Поле или строковый ключ словаря, в имени которого есть `password`, `passwd`, `secret`, `token`, `apiKey`, `credential` или `authorization` (без учёта регистра, `_` и `-`), печатается как `***`, если оно не `null`; поэтому скрыты и `accessToken`, и `X-Api-Key`.<!--ru-->
- Proxies and lambdas print only their class name: a Hibernate proxy (`$HibernateProxy$`), a CGLIB or Javassist class (`$$`) or a `java.lang.reflect.Proxy` is never called, so printing an entity never triggers lazy loading.<!--en-->
- Прокси и лямбды печатают только имя своего класса: прокси Hibernate (`$HibernateProxy$`), класс CGLIB или Javassist (`$$`) и `java.lang.reflect.Proxy` никогда не вызываются, поэтому печать сущности не запускает ленивую загрузку.<!--ru-->
- Nothing escapes: a failure while dumping a value prints `[FAILED dump()]`, as a broken `toString()` prints `[FAILED toString()]`.<!--en-->
- Наружу ничего не вылетает: сбой при выводе значения печатает `[FAILED dump()]`, как сломанный `toString()` печатает `[FAILED toString()]`.<!--ru-->

The same dump is available for `{}` arguments of every log call with `nanolog.dump=auto`, see [Message formatting](#message-formatting).<!--en-->
Тот же дамп доступен для аргументов `{}` любого вызова лога с `nanolog.dump=auto`, см. [Форматирование сообщений](#форматирование-сообщений).<!--ru-->

### ${en:'Leftover calls', ru:'Забытые вызовы'}

`dbg` is meant to be temporary, and debug prints are easy to leave behind, by people and coding agents alike. ${name} knows its calls by name, so it can find them. Since 1.4.<!--en-->
`dbg` задуман как временный, а отладочную печать легко забыть в коде, и человеку, и кодовому агенту. ${name} знает свои вызовы по имени и поэтому умеет их находить. Начиная с 1.4.<!--ru-->

At the end of the run, when JUnit has run the tests or when the JVM exits, one line on `System.err` lists the call sites that printed, in the order they first ran; with the [run summary](#run-summary-the-part-of-the-output-that-survives-truncation) on, the line is part of it:<!--en-->
В конце запуска, когда JUnit выполнил тесты или когда завершается JVM, одна строка в `System.err` перечисляет места вызова, которые печатали, в порядке первого запуска; при включённой [сводке запуска](#сводка-запуска-часть-вывода-которая-переживает-обрезку) строка входит в неё:<!--ru-->

```
[NANOLOG] 3 LOG.dbg calls ran in this JVM: OrderService.java:87, OrderService.java:88, CartService.java:31
```

A test in the project fails while the sources still call `LOG.dbg` and names every call:<!--en-->
Тест в проекте падает, пока в исходниках остаются вызовы `LOG.dbg`, и называет каждый из них:<!--ru-->

${widget:import(path='${examples}/NoDbgCallsLeftTest.java', region='test', wrap='true')}

```
java.lang.AssertionError: 2 LOG.dbg calls left in the code; remove them:
src/main/java/com/acme/OrderService.java:87: BigDecimal total = LOG.dbg(order.total());
src/main/java/com/acme/CartService.java:31: LOG.dbg();
```

- `NanologChecks.assertNoDbgCalls` reads the `.java` and `.kt` files under the directories as text: no compiler plugin, no annotation processor, no extra dependency. It finds `LOG.dbg(...)`, `LOG.once().dbg(...)`, `LOG.atMostEvery(...).dbg(...)`, `LOG::dbg` and a static import of `LOG.dbg`, also split over lines, and skips comments, strings and text blocks, so a Javadoc that shows `LOG.dbg` does not count.<!--en-->
- `NanologChecks.assertNoDbgCalls` читает файлы `.java` и `.kt` в каталогах как текст: без плагина компилятора, обработчика аннотаций и лишних зависимостей. Он находит `LOG.dbg(...)`, `LOG.once().dbg(...)`, `LOG.atMostEvery(...).dbg(...)`, `LOG::dbg` и статический импорт `LOG.dbg`, в том числе разнесённые на несколько строк, и пропускает комментарии, строки и текстовые блоки, поэтому Javadoc с примером `LOG.dbg` не считается.<!--ru-->
- A relative directory resolves against the working directory, which is the module directory in Maven and Gradle test runs. A directory that does not exist fails the test instead of passing it with nothing checked.<!--en-->
- Относительный каталог отсчитывается от рабочей директории, а в тестовых запусках Maven и Gradle это каталог модуля. Несуществующий каталог роняет тест, а не пропускает его без проверки.<!--ru-->

`nanolog.dbg` (`NANOLOG_DBG`) decides what the calls do at run time:<!--en-->
`nanolog.dbg` (`NANOLOG_DBG`) определяет, что делают вызовы во время работы:<!--ru-->

| `nanolog.dbg` | ${en:'Calls', ru:'Вызовы'} |
|---|---|
| `on` | ${en:'print; the default', ru:'печатают; значение по умолчанию'} |
| `off` | ${en:'print nothing and cost one check, before any stack walk; for production', ru:'ничего не печатают и требуют одной проверки, до обхода стека; для продакшена'} |
| `fail` | ${en:'still print and return their value, and each one is reported as a diagnostic', ru:'по-прежнему печатают и возвращают значение, и о каждом сообщается диагностикой'} |

With `fail`, each call site prints one line on `System.err`, and every call reaches the logger through `ILogger.onDiagnostic`, so `MemoryLogger.assertNoDiagnostics()` fails, as with [strict mode](#checking-placeholders-in-tests). Nothing is thrown:<!--en-->
С `fail` каждое место вызова печатает одну строку в `System.err`, а каждый вызов доходит до логгера через `ILogger.onDiagnostic`, поэтому `MemoryLogger.assertNoDiagnostics()` падает, как и в [строгом режиме](#проверка-плейсхолдеров-в-тестах). Исключение не бросается:<!--ru-->

```
[NANOLOG] com.acme.OrderService.checkout(OrderService.java:87): LOG.dbg call left in the code (nanolog.dbg=fail)
```

Like the entries themselves, the list at exit and the reports cover only calls whose `DEBUG` level is enabled; the test above finds every call.<!--en-->
Как и сами записи, список при выходе и диагностики учитывают только вызовы, у которых включён уровень `DEBUG`; тест выше находит все вызовы.<!--ru-->

## ${en:'Wide events: one line per request (canonical log lines)', ru:'Wide events: одна строка на запрос (canonical log lines)'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'Dozens of scattered lines per request are hard to read, for people and for agents alike. The idea of', ru:'Десятки разрозненных строк на запрос трудно читать и людям, и агентам. Идея'} [wide events](https://loggingsucks.com/) ${en:'and of', ru:'и'} [canonical log lines](https://stripe.com/blog/canonical-log-lines) ${en:'at Stripe is one rich line per request or job. `LOG.unit(name)` writes that line when the unit of work ends, next to the usual log, not instead of it:', ru:'Stripe — одна насыщенная строка на запрос или задание. `LOG.unit(name)` пишет такую строку, когда единица работы заканчивается, рядом с обычным логом, а не вместо него:'}

${widget:import(path='${examples}/WideEvents.java', region='unit', wrap='true')}

- ${en:'**The line** is `<name> <outcome> <duration> ms`, then the fields of `put(...)` and the counts of warnings (`warn`) and errors (`error`) logged inside the unit, in any thread it was carried into. Its fields are `unit`, `outcome`, `duration_ms` and the rest, so `JsonLogger` writes `"fields":{"unit":"checkout","outcome":"ok","duration_ms":182,...}` and `jq` selects the failed units by `.fields.outcome`.', ru:'**Строка** — это `<имя> <исход> <длительность> ms`, затем поля из `put(...)` и число предупреждений (`warn`) и ошибок (`error`), записанных внутри единицы, в любом потоке, куда её перенесли. Её поля — `unit`, `outcome`, `duration_ms` и остальные, поэтому `JsonLogger` пишет `"fields":{"unit":"checkout","outcome":"ok","duration_ms":182,...}`, а `jq` отбирает неудачные единицы по `.fields.outcome`.'}
- ${en:'**The outcome** is `ok`, an `INFO` line, or `error`, an `ERROR` line, once an `ERROR` entry was logged inside the unit or `fail(e)` was called. A failed unit adds `error.type`, `error.message` and `error.key`: eight hexadecimal digits made from the template and call site of the first error, the same in every run, so failures of one kind can be counted. `LOG.unit(WARN, "sync")` writes the line of a failed or interrupted unit at another level, for work whose failures are expected and handled elsewhere, a job that is retried or a call to a service that is often down, where every `ERROR` would raise an alert: `ok` stays `INFO`, and the outcome, the counts and the `error.*` fields do not change, so `outcome=error` still finds the unit.', ru:'**Исход** — `ok`, строка `INFO`, или `error`, строка `ERROR`, если внутри единицы была запись `ERROR` или вызван `fail(e)`. Неудачная единица добавляет `error.type`, `error.message` и `error.key`: восемь шестнадцатеричных цифр из шаблона и места вызова первой ошибки, одинаковые в каждом запуске, поэтому неудачи одного вида можно посчитать. `LOG.unit(WARN, "sync")` пишет строку неудачной или прерванной единицы на другом уровне — для работы, неудачи которой ожидаемы и обрабатываются в другом месте: задания, которое повторяется, или обращения к сервису, который часто недоступен, где каждый `ERROR` поднимал бы тревогу. `ok` остаётся `INFO`, а исход, счётчики и поля `error.*` не меняются, поэтому `outcome=error` по-прежнему находит такую единицу.'}
- ${en:'**Exceptions.** `close()` cannot see an exception that leaves a try-with-resources block: catch it and call `u.fail(e)`, or let `run(...)` do it. `LOG.unit("import", () -> importFile(file))` and `LOG.unit("report").put("month", m).run(() -> build(m))` mark the unit failed, attach the exception to its line and rethrow it.', ru:'**Исключения.** `close()` не видит исключения, которое покидает блок try-with-resources: перехватите его и вызовите `u.fail(e)` или поручите это `run(...)`. `LOG.unit("import", () -> importFile(file))` и `LOG.unit("report").put("month", m).run(() -> build(m))` помечают единицу неудачной, прикрепляют исключение к её строке и пробрасывают его дальше.'}
- ${en:'**Nesting.** A unit opened inside another names it as `parent`, and an entry counts in every unit around it. `LOG.wrap(task)` carries the open unit into a task for an executor together with the', ru:'**Вложенность.** Единица, открытая внутри другой, называет её в поле `parent`, а запись считается во всех единицах вокруг неё. `LOG.wrap(task)` переносит открытую единицу в задачу для пула потоков вместе с'} [${en:'context', ru:'контекстом'}](#${en:'context-tag-the-lines-of-one-request', ru:'контекст-метка-для-строк-одного-запроса'}), ${en:'so the tasks of a request count in its unit.', ru:'поэтому задачи запроса считаются в его единице.'}
- ${en:'**Not lost.** A unit still open when its thread dies of an uncaught exception, with', ru:'**Не теряется.** Единица, которая ещё открыта, когда её поток умирает от необработанного исключения, при'} [`LOG.installUncaughtExceptionHandler()`](#${en:'catching-exceptions', ru:'перехват-исключений'}), ${en:'or when the run ends (the JVM exits or JUnit has run the tests) is written with the outcome `interrupted`. That happens before the', ru:'или когда заканчивается запуск (JVM завершается или JUnit выполнил тесты), записывается с исходом `interrupted`. Это происходит до'} [${en:'run summary', ru:'сводки запуска'}](#${en:'run-summary-the-part-of-the-output-that-survives-truncation', ru:'сводка-запуска-часть-вывода-которая-переживает-обрезку'}), ${en:'which therefore counts it and lists a failed unit as `unit <name>`.', ru:'поэтому сводка её учитывает и показывает неудачную единицу как `unit <имя>`.'}
- ${en:'**Sampling.** `nanolog.unit.sample=0.05` writes 5% of the units that succeed fast and every failed one, and `nanolog.unit.slowMs=500` always writes those that take 500 ms or longer. By default every unit is written.', ru:'**Сэмплирование.** `nanolog.unit.sample=0.05` пишет 5% быстрых успешных единиц и все неудачные, а `nanolog.unit.slowMs=500` всегда пишет те, что длятся 500 мс и дольше. По умолчанию пишется каждая единица.'}
- ${en:'**Levels.** The line has the source class and call site of the `LOG.unit` call (for Logback and Log4j 2, see [`Slf4jLogger`](#slf4jlogger)) and the context of the thread. It follows the levels: an `ok` unit of a class set to `WARN` writes nothing, a failed one is still written, unless `LOG.unit(level, name)` gave it a level that is off too. In agent mode the console shows only the failed units, and the run log has them all.', ru:'**Уровни.** У строки класс-источник и место вызова `LOG.unit` (про Logback и Log4j 2 см. [`Slf4jLogger`](#slf4jlogger)) и контекст потока. Строка подчиняется уровням: успешная единица класса с уровнем `WARN` ничего не пишет, а неудачная пишется, если только `LOG.unit(level, name)` не дал ей уровень, который тоже выключен. В агентском режиме консоль показывает только неудачные единицы, а журнал запуска — все.'}
- ${en:'**Breadcrumbs.** With `nanolog.buffer=DEBUG` a failed unit is followed by the `DEBUG` entries logged inside it, and a successful one drops them; see', ru:'**Breadcrumbs.** С `nanolog.buffer=DEBUG` за неудачной единицей следуют записи `DEBUG`, сделанные внутри неё, а успешная их отбрасывает; см.'} [Breadcrumbs](#${en:'breadcrumbs-debug-only-when-something-fails', ru:'breadcrumbs-debug-только-когда-что-то-сломалось'}).
- ${en:'**Cost.** A unit costs one stack walk, to find where it was opened, and one line. Code outside units pays nothing beyond the `ThreadLocal` read of the context.', ru:'**Цена.** Единица стоит одного обхода стека, чтобы найти место открытия, и одной строки. Код вне единиц не платит ничего сверх чтения `ThreadLocal` для контекста.'}

## ${en:'Timing a block', ru:'Замер времени блока'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'"Which step is slow?" usually means two `System.nanoTime()` calls and a subtraction, written again each time. `LOG.time(name)` writes one uniform line that a person or an agent can compare between runs:', ru:'«Какой шаг медленный?» обычно означает два вызова `System.nanoTime()` и вычитание, каждый раз заново. `LOG.time(name)` пишет одну единообразную строку, которую человек или агент может сравнить между запусками:'}

```java
try (LOG.Timer t = LOG.time("Load users")) {
    users = repository.loadAll();
}
// INFO 16.11.2026 10:15:02 [UserService] Load users took 123 ms

int count = LOG.timed("Count users", () -> repository.count()); // returns the value
LOG.time(LogEntry.LogEntryLevel.DEBUG, "Parse").close();         // another level; INFO by default
```

- ${en:'The line has the fields `timer` and `duration_ms`, as a unit of work has its `duration_ms`, so `JsonLogger` writes `"fields":{"timer":"Load users","duration_ms":123}`. Its source class and call site are those of the `LOG.time` call; for the `%file:%line` of Logback and the `%l` of Log4j 2, see', ru:'У строки поля `timer` и `duration_ms`, как у единицы работы её `duration_ms`, поэтому `JsonLogger` пишет `"fields":{"timer":"Load users","duration_ms":123}`. Класс-источник и место вызова у неё те же, что у вызова `LOG.time`; про `%file:%line` в Logback и `%l` в Log4j 2 см.'} [`Slf4jLogger`](#slf4jlogger).
- ${en:'A level that is off for the class reads no clock and writes nothing, so a timer can stay in a hot path. The timer uses the clock of `atMostEvery`, which tests replace.', ru:'Уровень, выключенный для класса, не читает часы и ничего не пишет, поэтому таймер можно оставить и в горячем месте. Часы — те же, что у `atMostEvery`, и тесты их подменяют.'}
- ${en:'`LOG.timed(name, body)` writes `Count users failed after 12 ms` with the stack trace when the body throws, and rethrows the exception; `close()` of a try-with-resources block cannot see an exception. For a request or a job with an outcome and fields, use a', ru:'`LOG.timed(name, body)`, если тело бросает исключение, пишет `Count users failed after 12 ms` со стеком и пробрасывает исключение дальше; `close()` блока try-with-resources исключения не видит. Для запроса или задания с исходом и полями используйте'} [${en:'unit of work', ru:'единицу работы'}](#${en:'wide-events-one-line-per-request-canonical-log-lines', ru:'wide-events-одна-строка-на-запрос-canonical-log-lines'}).

## ${en:'Breadcrumbs: DEBUG only when something fails', ru:'Breadcrumbs: DEBUG, только когда что-то сломалось'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'With `DEBUG` off the log lacks the context of a failure, and with `DEBUG` on it drowns. `nanolog.buffer=DEBUG` keeps `DEBUG` and `TRACE` entries in memory and writes them only when an `ERROR` follows, before the error, as the log buffering of .NET, AWS Powertools and the breadcrumbs of Sentry do:', ru:'С выключенным `DEBUG` логу не хватает контекста сбоя, а с включённым он тонет в деталях. `nanolog.buffer=DEBUG` держит записи `DEBUG` и `TRACE` в памяти и пишет их, только когда следует `ERROR`, перед ошибкой, как это делают буферизация логов в .NET, AWS Powertools и breadcrumbs в Sentry:'}

```
java -Dnanolog.buffer=DEBUG -jar app.jar
```

```
INFO  [Checkout] Checkout of A-17 started
DEBUG --- 3 earlier DEBUG entries ---
DEBUG [Cart] Loaded 3 items
DEBUG [Pricing] Discount SPRING10 applied
DEBUG [Payment] Card ends with 4242
ERROR [Payment] Charge failed
```

- ${en:'The level is `TRACE`, `DEBUG`, `INFO` or `WARN`, and the levels below it are held back too; `off` is the default. `nanolog.buffer.size=200`, the default, keeps the newest entries, and the header says how many older ones were dropped.', ru:'Уровень — `TRACE`, `DEBUG`, `INFO` или `WARN`, и уровни ниже него тоже придерживаются; по умолчанию `off`. `nanolog.buffer.size=200` (значение по умолчанию) хранит самые новые записи, а заголовок говорит, сколько старых отброшено.'}
- ${en:'Held-back levels count as enabled: `LOG.isDebugEnabled()` is `true` and the message is built even when the logger has `DEBUG` off, as with `nanolog.level=INFO` in production. Building messages that are mostly thrown away costs what logging at `DEBUG` costs.', ru:'Придерживаемые уровни считаются включёнными: `LOG.isDebugEnabled()` возвращает `true`, и сообщение строится, даже если у логгера `DEBUG` выключен, как с `nanolog.level=INFO` в продакшене. Строить сообщения, которые в основном выбрасываются, так же дорого, как логировать на уровне `DEBUG`.'}
- ${en:'Outside units each thread has its own buffer, which its next `ERROR` writes. Inside a', ru:'Вне единиц работы у каждого потока свой буфер, и его пишет следующий `ERROR` этого потока. Внутри'} [${en:'unit', ru:'единицы работы'}](#${en:'wide-events-one-line-per-request-canonical-log-lines', ru:'wide-events-одна-строка-на-запрос-canonical-log-lines'}) ${en:'the unit has the buffer: a successful unit drops it, a failed or interrupted one writes it after its line. In a thread pool open a unit per task, or the buffer of a thread mixes its tasks.', ru:'буфер принадлежит единице: успешная его отбрасывает, неудачная или прерванная пишет после своей строки. В пуле потоков открывайте единицу на задачу, иначе буфер потока смешивает его задачи.'}
- ${en:'An entry is held back as a snapshot: its arguments, fields and context are rendered to text when it is logged, while numbers, booleans and strings keep their type, and text over 2000 characters is cut. Its time, thread and call site stay those of the call: the console prints the time of the call, and `JsonLogger` adds `"breadcrumb":true`.', ru:'Запись придерживается как снимок: её аргументы, поля и контекст превращаются в текст в момент вызова, но числа, логические значения и строки сохраняют тип, а текст длиннее 2000 символов обрезается. Время, поток и место вызова остаются исходными: консоль печатает время вызова, а `JsonLogger` добавляет `"breadcrumb":true`.'}
- ${en:'`LOG.dbg` is never held back, and a capture of `MemoryLogger` gets every entry at once. The buffer sits in front of the installed logger, so the run log too gets the held-back entries only before an error.', ru:'`LOG.dbg` никогда не придерживается, а захват `MemoryLogger` получает каждую запись сразу. Буфер стоит перед установленным логгером, поэтому и журнал запуска получает придержанные записи только перед ошибкой.'}
- ${en:'The run summary counts an entry once, when it is logged. The buffer of a thread that ends without an error is dropped, as is everything held back when the JVM exits normally; a thread that dies of an uncaught exception writes its buffer before the exception, with', ru:'Сводка запуска считает запись один раз, в момент вызова. Буфер потока, завершившегося без ошибки, отбрасывается, как и всё придержанное при обычном завершении JVM; поток, который умирает от необработанного исключения, пишет свой буфер перед исключением, при'} [`LOG.installUncaughtExceptionHandler()`](#${en:'catching-exceptions', ru:'перехват-исключений'}).

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
   Остальные ключи игнорируются, и о каждом сообщается (см. ниже). Путь к файлу переопределяется свойством `nanolog.config`.<!--ru-->
   Читается первый файл в порядке classpath; если файлов несколько и хотя бы один лежит в jar, то есть его принесла библиотека, одна строка `[NANOLOG]` перечисляет все и называет прочитанный.<!--ru-->
   В тестах `nanolog.properties` из `src/test/resources` заменяет файл из `src/main/resources`: Maven и Gradle ставят тестовые ресурсы в classpath тестов первыми.<!--ru-->
   Файл заменяется целиком, а не дополняется, поэтому повторите в нём ключи, которые тестам ещё нужны; одну настройку проще поменять через `-D` или переменную окружения, например в `systemPropertyVariables` у Surefire.<!--ru-->
   When `LOG` initialises, the keys of the file with the `nanolog.` prefix are copied<!--en-->
   into system properties without overwriting the ones already set via `-D` or an environment variable.<!--en-->
   Other keys are ignored, and each one is reported (see below). The file path can be overridden with the `nanolog.config` property.<!--en-->
   The first file in the order of the classpath is read; when there are several and one of them is in a jar, shipped by a library, one `[NANOLOG]` line lists them all and names the one that was read.<!--en-->
   In tests a `nanolog.properties` in `src/test/resources` replaces the one in `src/main/resources`: Maven and Gradle put the test resources first on the test classpath.<!--en-->
   The whole file is replaced, not merged, so repeat in it the keys the tests still need; a single setting is easier to change with `-D` or an environment variable, for example in the `systemPropertyVariables` of Surefire.<!--en-->

${en:'Every property can also be set as an environment variable: upper case, dots replaced by underscores, so', ru:'Любое свойство можно задать и переменной окружения: верхний регистр, точки заменены подчёркиваниями, то есть'} `nanolog.logger` ${en:'becomes', ru:'превращается в'} `NANOLOG_LOGGER`.
${en:'Precedence, highest first:', ru:'Приоритет, от высшего:'} `LOG.init(logger)` → `-Dnanolog.logger` → `NANOLOG_LOGGER` → `nanolog.properties` → `ConsoleLogger`.
${en:'A no-argument', ru:'Вызов'} `LOG.init()` ${en:'re-reads the property and the file.', ru:'без аргументов перечитывает свойство и файл.'}

| ${en:'Property', ru:'Свойство'} | ${en:'Meaning', ru:'Назначение'} | ${en:'Default', ru:'По умолчанию'} |
|---|---|---|
| `nanolog.logger.lock` | ${en:'`true` keeps the logger of the configuration: later `LOG.init` calls are ignored with a `[NANOLOG]` line, as after `LOG.lock()`', ru:'`true` сохраняет логгер из конфигурации: последующие вызовы `LOG.init` игнорируются со строкой `[NANOLOG]`, как после `LOG.lock()`'} | `false` |
| `nanolog.logger` | ${en:'class name of the `ILogger` implementation', ru:'имя класса реализации `ILogger`'} | `com.nanolaba.logging.ConsoleLogger` |
| `nanolog.config` | ${en:'classpath resource with the configuration', ru:'ресурс в classpath с конфигурацией'} | `nanolog.properties` |
| `nanolog.sources` | ${en:'source directories that `LOG.dbg` reads the text of its arguments from, comma-separated; empty turns the reading off', ru:'каталоги исходников, из которых `LOG.dbg` читает текст аргументов, через запятую; пустое значение отключает чтение'} | ${en:'derived from the class location', ru:'выводятся из расположения класса'} |
| `nanolog.dbg` | ${en:'`off` silences `LOG.dbg`, `fail` reports each call as a diagnostic, see', ru:'`off` заглушает `LOG.dbg`, `fail` сообщает о каждом вызове диагностикой, см.'} [${en:'Leftover calls', ru:'Забытые вызовы'}](#${en:'leftover-calls', ru:'забытые-вызовы'}) | `on` |
| `nanolog.agent` | ${en:'`auto` detects a coding agent from its environment variables, `on` and `off` decide instead, see', ru:'`auto` распознаёт кодового агента по его переменным окружения, `on` и `off` задают режим явно, см.'} [${en:'Agent mode', ru:'Агентский режим'}](#${en:'agent-mode-nanolog-detects-coding-agents', ru:'агентский-режим-nanolog-распознаёт-кодовых-агентов'}) | `auto` |
| `nanolog.junit.output` | ${en:'which logs of tests `NanologExtension` prints: `failed`, `all` or `none`, see', ru:'какие логи тестов печатает `NanologExtension`: `failed`, `all` или `none`, см.'} [${en:'Logs in tests', ru:'Логи в тестах'}](#${en:'logs-in-tests-only-the-failing-ones', ru:'логи-в-тестах-только-упавших'}) | `failed` |
| `nanolog.summary` | ${en:'`on` prints a summary at the end of the run, `errors` only after a `WARN` or `ERROR`, see', ru:'`on` печатает сводку в конце запуска, `errors` — только после `WARN` или `ERROR`, см.'} [${en:'Run summary', ru:'Сводка запуска'}](#${en:'run-summary-the-part-of-the-output-that-survives-truncation', ru:'сводка-запуска-часть-вывода-которая-переживает-обрезку'}) | `off`, ${en:'in agent mode', ru:'в агентском режиме'} `on` |
| `nanolog.run` | ${en:'`on` also writes every entry of the run to `latest.log`, see', ru:'`on` пишет каждую запись запуска ещё и в `latest.log`, см.'} [${en:'Run log', ru:'Журнал запуска'}](#${en:'run-log', ru:'журнал-запуска'}) | `off`, ${en:'in agent mode', ru:'в агентском режиме'} `on` |
| `nanolog.run.dir` | ${en:'the directory of the run log, relative to the working directory', ru:'каталог журнала запуска относительно рабочей директории'} | `target/nanolog`, `build/nanolog` ${en:'or', ru:'или'} `.nanolog` |
| `nanolog.run.format` | `text` ${en:'or', ru:'или'} `json` | `text` |
| `nanolog.run.keep` | ${en:'how many runs the run log keeps, this one included', ru:'сколько запусков хранит журнал, включая текущий'} | `10` |
| `nanolog.run.maxSize` | ${en:'the size in megabytes at which a file of the run log stops, with a note', ru:'размер в мегабайтах, на котором файл журнала запуска останавливается с пометкой'} | `100` |
| `nanolog.unit.sample` | ${en:'the share of fast successful units that are written, from `0` to `1`; failed and slow units are always written, see', ru:'доля быстрых успешных единиц, которые записываются, от `0` до `1`; неудачные и медленные пишутся всегда, см.'} [${en:'Wide events', ru:'Wide events'}](#${en:'wide-events-one-line-per-request-canonical-log-lines', ru:'wide-events-одна-строка-на-запрос-canonical-log-lines'}) | `1` |
| `nanolog.unit.slowMs` | ${en:'the duration in milliseconds from which a unit is slow and always written', ru:'длительность в миллисекундах, начиная с которой единица медленная и пишется всегда'} | ${en:'not set', ru:'не задана'} |
| `nanolog.buffer` | ${en:'`TRACE`, `DEBUG`, `INFO` or `WARN`: entries at that level and below are held back and written only before an `ERROR`, see', ru:'`TRACE`, `DEBUG`, `INFO` или `WARN`: записи этого уровня и ниже придерживаются и пишутся только перед `ERROR`, см.'} [Breadcrumbs](#${en:'breadcrumbs-debug-only-when-something-fails', ru:'breadcrumbs-debug-только-когда-что-то-сломалось'}) | `off` |
| `nanolog.buffer.size` | ${en:'how many entries the buffer keeps per thread or unit, the newest', ru:'сколько записей буфер хранит на поток или единицу работы, самые новые'} | `200` |
| `nanolog.redact` | ${en:'the words that name a secret, comma-separated: fields and context values whose key, or a word of it, is one of them are written as `***`; empty turns masking off, see', ru:'слова, которые называют секрет, через запятую: поля и значения контекста, чей ключ или его слово совпадает с одним из них, пишутся как `***`; пустое значение отключает маскирование, см.'} [${en:'Masking secrets', ru:'Маскирование секретов'}](#${en:'masking-secrets', ru:'маскирование-секретов'}) | `password,passwd,secret,token,apikey,credential,authorization` |
| `nanolog.content` | ${en:'how much of a payload of `LOG.content(...)` is written: `hide`, `truncate:<characters>` or `show`, see', ru:'сколько содержимого `LOG.content(...)` пишется: `hide`, `truncate:<символы>` или `show`, см.'} [${en:'Logging payloads safely', ru:'Безопасное логирование содержимого'}](#${en:'logging-payloads-safely', ru:'безопасное-логирование-содержимого'}) | `hide` |
| `nanolog.content.<${en:'package or class', ru:'пакет или класс'}>` | ${en:'the same for one package or class; the most specific rule wins', ru:'то же для одного пакета или класса; действует самое точное правило'} | — |
| `nanolog.dump` | ${en:'`auto` prints `{}` arguments field by field when their class has no `toString()` of its own, see', ru:'`auto` печатает аргументы `{}`, у класса которых нет собственного `toString()`, поле за полем, см.'} [${en:'Message formatting', ru:'Форматирование сообщений'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) | `off` |
| `nanolog.diagnostics` | ${en:'how often a `[NANOLOG]` diagnostic is printed: `once` per JVM, `all` every time or `off`; `ILogger.onDiagnostic` still gets the findings of strict mode', ru:'как часто печатается диагностика `[NANOLOG]`: `once` один раз на JVM, `all` каждый раз или `off`; `ILogger.onDiagnostic` по-прежнему получает находки строгого режима'} | `once` |
| `nanolog.caller.skip` | ${en:'classes and packages, comma-separated, whose frames caller detection skips like those of `LOG`, see', ru:'классы и пакеты через запятую, кадры которых определение вызывающего класса пропускает, как кадры `LOG`, см.'} [${en:'Logging helpers', ru:'Обёртки над LOG'}](#${en:'logging-helpers', ru:'обёртки-над-log'}) | — |
| `nanolog.context.provider` | ${en:'classes of context providers, comma-separated, see', ru:'классы провайдеров контекста через запятую, см.'} [${en:'Context providers', ru:'Провайдеры контекста'}](#${en:'context-providers', ru:'провайдеры-контекста'}) | — |
| `nanolog.captureSystemStreams` | ${en:'`true` turns the lines printed to `System.out` and `System.err` into `INFO` and `ERROR` entries, see', ru:'`true` превращает строки, напечатанные в `System.out` и `System.err`, в записи `INFO` и `ERROR`, см.'} [${en:'System.out and System.err', ru:'System.out и System.err'}](#${en:'systemout-and-systemerr', ru:'systemout-и-systemerr'}) | `false` |
| `nanolog.tags` | ${en:'levels per tag of `LOG.tag(...)`, comma-separated: `audit=WARN,sql=DEBUG`; a rule beats the levels of the class, see', ru:'уровни по тегам `LOG.tag(...)` через запятую: `audit=WARN,sql=DEBUG`; правило главнее уровней класса, см.'} [${en:'Tags', ru:'Теги'}](#${en:'tags', ru:'теги'}) | — |
| `nanolog.strict` | ${en:'report calls whose `{}` placeholders and arguments do not match, whose argument repeats the message of the exception or whose template is built from data, see', ru:'сообщать о вызовах, у которых плейсхолдеры `{}` не совпадают с аргументами, аргумент повторяет сообщение исключения или шаблон собран из данных, см.'} [${en:'Message formatting', ru:'Форматирование сообщений'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) | `false` |

Если класс из `nanolog.logger` не удалось создать, ${name} печатает в `System.err`<!--ru-->
диагностику с префиксом `[NANOLOG]` и продолжает работу с `ConsoleLogger`.<!--ru-->
If the class named in `nanolog.logger` cannot be instantiated, ${name} prints a<!--en-->
`[NANOLOG]`-prefixed diagnostic to `System.err` and carries on with `ConsoleLogger`.<!--en-->

A setting that ${name} does not know is reported, not ignored without a word. When `LOG` initialises, each unknown `nanolog.*` system property, `NANOLOG_*` environment variable and key of `nanolog.properties` prints one `[NANOLOG]` line, with a suggestion when a known key differs by a typo or two:<!--en-->
Настройка, которую ${name} не знает, не игнорируется молча. При инициализации `LOG` каждое неизвестное системное свойство `nanolog.*`, переменная окружения `NANOLOG_*` и ключ `nanolog.properties` печатают одну строку `[NANOLOG]` с подсказкой, если известный ключ отличается на одну-две опечатки:<!--ru-->

```
[NANOLOG] Unknown setting "nanolog.levle" (system property); did you mean "nanolog.level"?
[NANOLOG] Unknown environment variable NANOLOG_LEVEL_COM_ACME; per-package levels go into NANOLOG_LEVELS=com.acme=DEBUG
[NANOLOG] "level" in file:/app/classes/nanolog.properties is ignored: keys must start with "nanolog." (did you mean "nanolog.level"?)
```

`nanolog.buffer.*`, `nanolog.caller.*`, `nanolog.console.*`, `nanolog.content.*`, `nanolog.context.*`, `nanolog.exception.*`, `nanolog.file.*`, `nanolog.junit.*`, `nanolog.level.*`, `nanolog.run.*`, `nanolog.unit.*` and the keys directly under `nanolog.` belong to ${name}. A custom logger keeps settings of its own under a namespace of its own, such as `nanolog.mylogger.url`; such a key is reported only when it differs from a known one by a typo or two. `nanolog.version` and `NANOLOG_VERSION` are not reported: a build keeps the version of ${name} in them, and Surefire passes `-Dnanolog.version=1.5` of the Maven command line to the JVMs of the tests as a system property. Since 1.4.<!--en-->
`nanolog.buffer.*`, `nanolog.caller.*`, `nanolog.console.*`, `nanolog.content.*`, `nanolog.context.*`, `nanolog.exception.*`, `nanolog.file.*`, `nanolog.junit.*`, `nanolog.level.*`, `nanolog.run.*`, `nanolog.unit.*` и ключи непосредственно под `nanolog.` принадлежат ${name}. Собственный логгер держит настройки в своём пространстве имён, например `nanolog.mylogger.url`; о таком ключе сообщается, только если он отличается от известного на одну-две опечатки. О `nanolog.version` и `NANOLOG_VERSION` не сообщается: в них сборка держит версию ${name}, а Surefire передаёт `-Dnanolog.version=1.5` из командной строки Maven в JVM тестов как системное свойство. Начиная с 1.4.<!--ru-->

${widget:alert(type='important', text="${en:'`LOG.init` replaces the logger for the whole JVM. Call it once at application startup, before other threads start logging. Any library on the classpath can call it as well, so do not call it from library code. When code of another organization replaces a logger installed with `LOG.init(logger)`, a `[NANOLOG]` line names both call sites: `Logger replaced: ConsoleLogger (installed at com.app.Main.main(Main.java:12)) -> com.vendor.SdkLogger (installed at com.vendor.Sdk.init(Sdk.java:40))`. A call through reflection belongs to the code that made it. A container that installs the logger for the application, as `MethodInvokingBean` of Spring does, is named in `nanolog.caller.skip` (`org.springframework`): its calls count for the application and are never reported. `LOG.lock()` after your own `LOG.init`, or `nanolog.logger.lock=true` for the logger of the configuration, makes later `LOG.init` calls no-ops with such a line; `MemoryLogger.install()` still works in tests.', ru:'`LOG.init` заменяет логгер для всей JVM. Вызывайте его один раз при старте приложения, до того как другие потоки начнут писать в лог. Его может вызвать и любая библиотека в classpath, поэтому не вызывайте его из библиотечного кода. Когда код другой организации заменяет логгер, установленный через `LOG.init(logger)`, строка `[NANOLOG]` называет оба места вызова: `Logger replaced: ConsoleLogger (installed at com.app.Main.main(Main.java:12)) -> com.vendor.SdkLogger (installed at com.vendor.Sdk.init(Sdk.java:40))`. Вызов через рефлексию принадлежит коду, который его сделал. Контейнер, который устанавливает логгер за приложение, как `MethodInvokingBean` в Spring, указывается в `nanolog.caller.skip` (`org.springframework`): его вызовы считаются вызовами приложения, и о них никогда не сообщается. `LOG.lock()` после вашего `LOG.init` или `nanolog.logger.lock=true` для логгера из конфигурации превращают последующие вызовы `LOG.init` в пустые, с такой же строкой; `MemoryLogger.install()` в тестах продолжает работать.'}")}

- ${en:'The organization is the first two segments of the package of the calling class, `com.acme` for `com.acme.billing.Main`: the application and its tests may install their own loggers from anywhere without a line, and anybody may install the same logger again. The logger of the configuration, installed when `LOG` starts or by `LOG.init()`, may be replaced without a line as well.', ru:'Организация — это первые два сегмента пакета вызывающего класса, `com.acme` для `com.acme.billing.Main`: приложение и его тесты могут ставить свои логгеры откуда угодно без сообщения, а кто угодно может повторно поставить тот же логгер. Логгер из конфигурации, установленный при старте `LOG` или через `LOG.init()`, тоже заменяется без сообщения.'}

### ConsoleLogger

${en:'The default implementation. Writes', ru:'Реализация по умолчанию. Пишет'} `ERROR` ${en:'to', ru:'в'} `System.err` ${en:'and everything else to', ru:'и всё остальное в'} `System.out`, ${en:'one line per entry plus the stack trace:', ru:'по строке на запись плюс трассировка стека:'}

```
LEVEL dd.MM.yyyy HH:mm:ss [SourceClass] Message
```

${en:'Every component is controlled by a fluent setter:', ru:'Каждым компонентом управляет свой сеттер, и их можно вызывать цепочкой:'}

${widget:import(path='${examples}/LoggerSetup.java', region='console', wrap='true')}

- `showLevel` — ${en:'the level name', ru:'имя уровня'}.
- `showDate` + `dateFormat` — ${en:'the timestamp and its', ru:'временная метка и её'} `SimpleDateFormat`; ${en:'formatting is synchronized, so one shared format is safe', ru:'форматирование синхронизировано, общий экземпляр безопасен'}.
- `timeZone` — ${en:'the zone of the timestamp, `TimeZone.getTimeZone("UTC")` for one; the zone of the JVM by default. It is set on the current format and on any format set later. `JsonLogger` writes UTC whatever the zone.', ru:'часовой пояс метки, например `TimeZone.getTimeZone("UTC")`; по умолчанию пояс JVM. Он задаётся текущему формату и любому формату, заданному позже. `JsonLogger` пишет UTC при любом поясе.'}
- `showSource` / `showSourceFullName` — ${en:'the simple or the fully qualified source class name. An anonymous or local class passed explicitly has no such name and is shown by its binary name, `Foo$1` or `com.acme.Foo$1`', ru:'короткое или полное имя класса-источника. У анонимного или локального класса, переданного явно, такого имени нет, и он показывается по двоичному имени: `Foo$1` или `com.acme.Foo$1`'}.
- `sourceLength` — ${en:'abbreviates the fully qualified name as `%logger{n}` of Logback does: while it is longer than `n` characters, the segments of its package, from the left, are cut to their first letter. At 30 `com.acme.billing.payments.PaymentService` becomes `c.a.b.payments.PaymentService`, at 20 `c.a.b.p.PaymentService`; the name of the class is never cut. `0`, the default, keeps the full name, and the simple name is never abbreviated. Each class is abbreviated once.', ru:'сокращает полное имя, как `%logger{n}` в Logback: пока оно длиннее `n` символов, сегменты пакета слева направо сокращаются до первой буквы. При 30 `com.acme.billing.payments.PaymentService` становится `c.a.b.payments.PaymentService`, при 20 — `c.a.b.p.PaymentService`; имя класса не сокращается никогда. `0`, значение по умолчанию, оставляет полное имя, а короткое имя не сокращается вовсе. Каждый класс сокращается один раз.'}
- `showLocation` — ${en:'the call site after the class name, in the form stack traces use, so IDEs and terminals turn it into a link: `[PaymentService.charge(PaymentService.java:87)]`. Off by default. A call with an explicit source class has no call site and keeps the plain `[PaymentService]`.', ru:'место вызова после имени класса в том же виде, что и в трассировках стека, поэтому IDE и терминалы превращают его в ссылку: `[PaymentService.charge(PaymentService.java:87)]`. По умолчанию выключено. У вызова с явным классом-источником места вызова нет, и остаётся обычный `[PaymentService]`.'}
- `showSourceLine` — ${en:'the source line of the call on a line of its own under the entry, and under the first three frames of a stack trace outside the JDK, so a reader or an agent need not open the file:', ru:'строка исходника с вызовом на отдельной строке под записью и под первыми тремя кадрами трассировки вне JDK, чтобы читателю или агенту не открывать файл:'} `    > LOG.warn("Charge of {} failed", amount);`. ${en:'It needs the sources, so it works in development: they are found as `LOG.dbg` finds the text of its arguments, and nothing is written when they are missing or newer than the classes. `JsonLogger` adds `"source"` to its `"caller"`. Off by default; since 1.4.', ru:'Нужны исходники, поэтому это работает при разработке: они ищутся так же, как `LOG.dbg` ищет текст своих аргументов, и ничего не пишется, если их нет или они новее классов. `JsonLogger` добавляет `"source"` в свой `"caller"`. По умолчанию выключено; начиная с 1.4.'}
- `showThread` — ${en:'the name of the thread that logged, in square brackets after the date: `[main]`, `[pool-1-thread-3]`. Off by default; with a thread pool it tells the interleaved lines of different tasks apart. `JsonLogger` always writes the thread.', ru:'имя потока, который записал строку, в квадратных скобках после даты: `[main]`, `[pool-1-thread-3]`. По умолчанию выключено; при пуле потоков помогает различать перемешанные строки разных задач. `JsonLogger` пишет поток всегда.'}
- `showContext` — ${en:'the pairs of `LOG.context(...)` after the message, `requestId=r-17`. On by default; since 1.4.', ru:'пары `LOG.context(...)` после сообщения, `requestId=r-17`. По умолчанию включено; начиная с 1.4.'} ${en:'See', ru:'См.'} [${en:'Context', ru:'Контекст'}](#${en:'context-tag-the-lines-of-one-request', ru:'контекст-метка-для-строк-одного-запроса'}).
- `stream` — ${en:'which levels go to `System.err`: `err@ERROR` (the default) sends only errors there, `err@WARN` warnings and errors, `err` everything, `out` nothing. A single stream keeps errors in a pipe and the lines in order when standard output and standard error are captured separately.', ru:'какие уровни идут в `System.err`: `err@ERROR` (по умолчанию) отправляет туда только ошибки, `err@WARN` — предупреждения и ошибки, `err` — всё, `out` — ничего. Единый поток вывода сохраняет ошибки в конвейере и порядок строк, когда стандартный вывод и поток ошибок перехватываются раздельно.'}
- `multiline` — ${en:'what an entry does with line breaks and control characters: `keep` (the default) writes them as they are; `indent` starts every line of an entry after the first with a tab, like stack frames, so a line without a tab begins an entry and a message cannot forge one; `escape` writes them as `\r` and `\n`, so an entry and its stack trace are one physical line for `grep` and `tail`. Both modes write other control characters, the escape sequences of a terminal among them, as `\u001b`, and keep the tab. `JsonLogger` escapes its lines anyway. Since 1.4.', ru:'что запись делает с переводами строк и управляющими символами: `keep` (по умолчанию) пишет их как есть; `indent` начинает каждую строку записи после первой с табуляции, как строки трассировки, поэтому строка без табуляции начинает запись и сообщение не может подделать запись; `escape` пишет их как `\r` и `\n`, поэтому запись вместе с трассировкой занимает одну физическую строку для `grep` и `tail`. Оба режима пишут остальные управляющие символы, в том числе escape-последовательности терминала, в виде `\u001b` и оставляют табуляцию. `JsonLogger` и так экранирует свои строки. Начиная с 1.4.'}
- `color` — ${en:'when the level word is coloured: red `ERROR`, yellow `WARN`, green `INFO`, dim `DEBUG` and `TRACE`, magenta `DBG`. `auto` (the default) colours only a terminal and follows the [NO_COLOR](https://no-color.org/) and `FORCE_COLOR` conventions: no colours when `NO_COLOR` is set, colours when `FORCE_COLOR` is set to anything but `0` or `false`, otherwise only when standard output is a terminal and `TERM` is not `dumb`. A pipe, a file, the run log and `JsonLogger` never get escape codes. On Windows `auto` does not colour without `FORCE_COLOR`: the console shows the codes as text unless the program turns their processing on, which Java does not do. `always` and `never` ignore the environment; agent mode uses `never`. Since 1.4.', ru:'когда раскрашивается имя уровня: красный `ERROR`, жёлтый `WARN`, зелёный `INFO`, приглушённые `DEBUG` и `TRACE`, пурпурный `DBG`. `auto` (по умолчанию) раскрашивает только терминал и следует соглашениям [NO_COLOR](https://no-color.org/) и `FORCE_COLOR`: без цветов, если задана `NO_COLOR`; с цветами, если `FORCE_COLOR` задана и не равна `0` или `false`; иначе только когда стандартный вывод — терминал, а `TERM` не равна `dumb`. В конвейер, файл, журнал запуска и `JsonLogger` escape-последовательности не попадают никогда. В Windows `auto` без `FORCE_COLOR` не раскрашивает: консоль показывает коды как текст, если программа не включила их обработку, а Java её не включает. `always` и `never` окружение не учитывают; агентский режим использует `never`. Начиная с 1.4.'}
- `repeats` — ${en:'what happens to consecutive repeats of an entry, the same level, source, call site, message, fields, context and exception: `keep` (the default) writes each; `collapse` writes the first in full; when another entry comes, the logger is replaced or the run ends, it writes one line with the count, without the stack trace: `WARN [Poller] Connection refused: db:5432 (repeated 41 more times in 20.3 s)`. A series that goes on writes its count every ten seconds, so `tail -f` still shows it. Only the console collapses: the run log, `JsonLogger` and `MemoryLogger` get every entry, and the run summary counts them all. Agent mode uses `collapse`; since 1.4.', ru:'что происходит с подряд идущими повторами записи, с тем же уровнем, источником, местом вызова, сообщением, полями, контекстом и исключением: `keep` (по умолчанию) пишет каждую; `collapse` пишет первую полностью, а затем, когда приходит другая запись, заменяется логгер или заканчивается запуск, — одну строку со счётчиком, без трассировки: `WARN [Poller] Connection refused: db:5432 (repeated 41 more times in 20.3 s)`. Затянувшаяся серия пишет свой счётчик каждые десять секунд, поэтому `tail -f` её видит. Сворачивается только консоль: журнал запуска, `JsonLogger` и `MemoryLogger` получают каждую запись, а сводка запуска считает их все. Агентский режим использует `collapse`; начиная с 1.4.'}
- `exceptionExclude` / `exceptionMaxFrames` — ${en:'shorter stack traces, see', ru:'короткие трассировки стека, см.'} [${en:'Shorter stack traces', ru:'Короткие трассировки стека'}](#${en:'shorter-stack-traces', ru:'короткие-трассировки-стека'}).
- `traceEnabled` … `errorEnabled` — ${en:'per-level switches; everything is enabled by default, so switch `TRACE` and `DEBUG` off in production, in code or with `nanolog.level=INFO`. Disabled levels are rejected before the `LogEntry` is built, so lazy messages are never evaluated. `setLevel(level)` sets all five at once, and the switches may change while other threads log; see [Changing levels at run time](#changing-levels-at-run-time).', ru:'переключатели по уровням; по умолчанию включено всё, поэтому в продакшене отключите `TRACE` и `DEBUG` в коде или через `nanolog.level=INFO`. Отключённые уровни отсекаются до создания `LogEntry`, поэтому ленивые сообщения не вычисляются. `setLevel(level)` задаёт все пять сразу, а менять переключатели можно и пока другие потоки пишут в лог; см. [Изменение уровней во время работы](#изменение-уровней-во-время-работы).'}

Для своего формата (другие цвета, другая раскладка) унаследуйтесь от `ConsoleLogger`<!--ru-->
и переопределите `getOutputStream`, `writeLevel`, `writeDate`, `writeSource`, `writeTags`,<!--ru-->
`writeMessage`, `writeFields`, `writeContext`, `writeSourceLine`, `writeThrowable` или `addDelimiter`.<!--ru-->
For a custom layout (other colours, a different order) extend `ConsoleLogger` and<!--en-->
override `getOutputStream`, `writeLevel`, `writeDate`, `writeSource`, `writeTags`, `writeMessage`,<!--en-->
`writeFields`, `writeContext`, `writeSourceLine`, `writeThrowable` or `addDelimiter`.<!--en-->

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
| `nanolog.console.sourceLength` | `NANOLOG_CONSOLE_SOURCELENGTH` | ${en:'a number of characters, `0` (default) for the full name, like the setter', ru:'число символов, `0` (по умолчанию) — полное имя, как одноимённый сеттер'} |
| `nanolog.console.showLocation` | `NANOLOG_CONSOLE_SHOWLOCATION` | ${en:'the same', ru:'то же'} |
| `nanolog.console.showThread` | `NANOLOG_CONSOLE_SHOWTHREAD` | ${en:'the same', ru:'то же'} |
| `nanolog.console.showSourceLine` | `NANOLOG_CONSOLE_SHOWSOURCELINE` | ${en:'the same', ru:'то же'} |
| `nanolog.console.showContext` | `NANOLOG_CONSOLE_SHOWCONTEXT` | ${en:'the same', ru:'то же'} |
| `nanolog.console.stream` | `NANOLOG_CONSOLE_STREAM` | `out`, `err` ${en:'or', ru:'или'} `err@LEVEL` (${en:'default', ru:'по умолчанию'} `err@ERROR`), ${en:'like the setter', ru:'как одноимённый сеттер'} |
| `nanolog.console.multiline` | `NANOLOG_CONSOLE_MULTILINE` | `keep` (${en:'default', ru:'по умолчанию'}), `indent` ${en:'or', ru:'или'} `escape`, ${en:'like the setter', ru:'как одноимённый сеттер'} |
| `nanolog.console.repeats` | `NANOLOG_CONSOLE_REPEATS` | `keep` (${en:'default', ru:'по умолчанию'}) ${en:'or', ru:'или'} `collapse`, ${en:'like the setter', ru:'как одноимённый сеттер'} |
| `nanolog.console.color` | `NANOLOG_CONSOLE_COLOR` | `auto` (${en:'default', ru:'по умолчанию'}), `always` ${en:'or', ru:'или'} `never`, ${en:'like the setter', ru:'как одноимённый сеттер'} |
| `nanolog.console.dateFormat` | `NANOLOG_CONSOLE_DATEFORMAT` | ${en:'a `SimpleDateFormat` pattern, or', ru:'шаблон `SimpleDateFormat` или'} `iso` ${en:'for', ru:'для'} `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`, `2026-11-18T10:30:00.000+01:00` |
| `nanolog.console.timeZone` | `NANOLOG_CONSOLE_TIMEZONE` | ${en:'a time zone ID such as `UTC`, `Europe/Berlin` or `GMT+03:00`, like the setter; an ID that Java does not know is reported instead of being taken for GMT', ru:'идентификатор часового пояса, например `UTC`, `Europe/Berlin` или `GMT+03:00`, как одноимённый сеттер; о неизвестном для Java идентификаторе сообщается, а не подставляется GMT'} |
| `nanolog.exception.exclude` | `NANOLOG_EXCEPTION_EXCLUDE` | ${en:'package or class prefixes whose stack frames are collapsed, comma-separated', ru:'префиксы пакетов или классов, чьи кадры стека сворачиваются, через запятую'} |
| `nanolog.exception.maxFrames` | `NANOLOG_EXCEPTION_MAXFRAMES` | ${en:'the most stack frames per throwable;', ru:'наибольшее число кадров стека на исключение;'} `0` ${en:'(default) for no limit', ru:'(по умолчанию) — без ограничения'} |
| `nanolog.file` | `NANOLOG_FILE` | ${en:'the file of', ru:'файл'} [FileLogger](#filelogger), `nanolog.log` ${en:'by default', ru:'по умолчанию'} |
| `nanolog.file.append` | `NANOLOG_FILE_APPEND` | `true` (${en:'default', ru:'по умолчанию'}) ${en:'or', ru:'или'} `false`, ${en:'which empties the file when it opens', ru:'что очищает файл при открытии'} |
| `nanolog.file.maxSize` | `NANOLOG_FILE_MAXSIZE` | ${en:'the size at which the file rolls over, such as', ru:'размер, при котором файл переносится в резервный, например'} `10MB`; `0` ${en:'(default) for none', ru:'(по умолчанию) — без переноса'} |
| `nanolog.file.backups` | `NANOLOG_FILE_BACKUPS` | ${en:'how many rolled-over files are kept,', ru:'сколько перенесённых файлов хранится,'} `3` ${en:'by default', ru:'по умолчанию'} |
| `nanolog.file.flush` | `NANOLOG_FILE_FLUSH` | `entry` (${en:'default, each entry at once', ru:'по умолчанию, каждая запись сразу'}) ${en:'or', ru:'или'} `buffer` |
| `nanolog.file.format` | `NANOLOG_FILE_FORMAT` | `text` (${en:'default', ru:'по умолчанию'}) ${en:'or', ru:'или'} `json` |

```
java -Dnanolog.level=WARN -Dnanolog.level.com.acme.payments=DEBUG -jar app.jar
NANOLOG_LEVEL=WARN NANOLOG_LEVELS=com.acme.payments=DEBUG java -jar app.jar
```

- ${en:'A package or class rule beats the global level, however that level was set, setters included. Turning `DEBUG` on for one package therefore needs no code change.', ru:'Правило для пакета или класса главнее глобального уровня, как бы тот ни был задан, в том числе сеттерами. Поэтому `DEBUG` для одного пакета включается без правки кода.'}
- ${en:'The most specific rule wins: the class itself, then its enclosing classes, then its packages from the innermost outwards. A rule matches whole name segments only, so `com.acme.pay` does not cover `com.acme.payments`.', ru:'Действует самое точное правило: сам класс, затем внешние классы, затем пакеты от ближайшего к дальнему. Правило совпадает только с целыми частями имени, поэтому `com.acme.pay` не распространяется на `com.acme.payments`.'}
- ${en:'`nanolog.level.<name>` beats an item for the same name in `nanolog.levels`.', ru:'`nanolog.level.<имя>` главнее элемента с тем же именем в `nanolog.levels`.'}
- ${en:'Setters called after the constructor override the values it read, so `new ConsoleLogger().setDebugEnabled(false)` in code wins over `nanolog.level`; package and class rules still apply on top.', ru:'Сеттеры, вызванные после конструктора, переопределяют прочитанные им значения, поэтому `new ConsoleLogger().setDebugEnabled(false)` в коде главнее `nanolog.level`; правила для пакетов и классов при этом продолжают действовать.'}
- ${en:'`[NANOLOG]` lines are notices of what Nanolog does and diagnostics of its own problems. A diagnostic says where it comes from: a setting and where it was set (`set as a system property`, `set by the environment variable NANOLOG_LEVEL`, `set in file:/.../nanolog.properties`), a logger class or a call site. Each distinct diagnostic is printed once per JVM; `nanolog.diagnostics=all` prints every one, including each failure of a logger, and `off` none, while `ILogger.onDiagnostic` still gets the findings of strict mode and of `nanolog.dbg=fail`. The run summary, the line of agent mode and the path of the run log are notices with switches of their own.', ru:'Строки `[NANOLOG]` — это сообщения о том, что делает Nanolog, и диагностики его собственных проблем. Диагностика говорит, откуда она: настройка и где она задана (`set as a system property`, `set by the environment variable NANOLOG_LEVEL`, `set in file:/.../nanolog.properties`), класс логгера или место вызова. Каждая отдельная диагностика печатается один раз на JVM; `nanolog.diagnostics=all` печатает все, в том числе каждый сбой логгера, а `off` — ни одной, при этом `ILogger.onDiagnostic` по-прежнему получает находки строгого режима и `nanolog.dbg=fail`. Сводка запуска, строка агентского режима и путь к журналу запуска — это сообщения со своими переключателями.'}
- ${en:'An invalid value prints a `[NANOLOG]` diagnostic to `System.err` and is ignored; logging goes on. A diagnostic is always one line: control characters in the value it repeats are printed as escapes such as `\u000a`.', ru:'Неверное значение печатает в `System.err` диагностику с префиксом `[NANOLOG]` и игнорируется; логирование продолжается. Диагностика всегда занимает одну строку: управляющие символы в значении, которое она повторяет, печатаются как экранированные последовательности вроде `\u000a`.'}
- ${en:'`nanolog.properties` is read when `LOG` initialises, so a `ConsoleLogger` created before that sees only `-D` and the environment.', ru:'`nanolog.properties` читается при инициализации `LOG`, поэтому `ConsoleLogger`, созданный раньше, видит только `-D` и переменные окружения.'}

### ${en:'Changing levels at run time', ru:'Изменение уровней во время работы'}

${en:'The levels of `ConsoleLogger` and `JsonLogger` can change while the application runs, for a verbose mode, an admin endpoint or a test:', ru:'Уровни `ConsoleLogger` и `JsonLogger` можно менять во время работы приложения — для подробного режима, из админского эндпоинта или в тесте:'}

${widget:import(path='${examples}/RuntimeLevels.java', region='runtime', wrap='true')}

- ${en:'A change takes effect on the next logging call in every thread, threads that are already running included.', ru:'Изменение действует со следующего вызова логирования во всех потоках, в том числе уже запущенных.'}
- ${en:'A level set for a package or class works exactly like `nanolog.level.<name>`: it beats the global level, and the most specific rule wins. `LOG.setLevel(level)` changes only the global level and leaves such rules alone.', ru:'Уровень пакета или класса работает так же, как `nanolog.level.<имя>`: он главнее глобального уровня, и действует самое точное правило. `LOG.setLevel(level)` меняет только глобальный уровень и такие правила не трогает.'}
- ${en:'`LOG.withLevel(...)` restores the rule the name had when the `try` block ends, even when it ends with an exception. The change is JVM-wide. Nested overrides of the same name restore correctly when they close in reverse order, as try-with-resources does.', ru:'`LOG.withLevel(...)` возвращает прежнее правило в конце блока `try`, даже если блок завершился исключением. Изменение действует на всю JVM. Вложенные переопределения одного имени восстанавливаются правильно, если закрываются в обратном порядке, как в try-with-resources.'}
- ${en:'`LOG.resetLevels()` reads `nanolog.level`, `nanolog.levels` and `nanolog.level.<name>` again: from `-D` and the environment as they are now, from `nanolog.properties` as it was loaded at startup. It undoes every change made at run time, the level setters of `ConsoleLogger` included; the display options stay as they are.', ru:'`LOG.resetLevels()` заново читает `nanolog.level`, `nanolog.levels` и `nanolog.level.<имя>`: из `-D` и переменных окружения в их текущем виде, из `nanolog.properties` — в том виде, в каком файл был загружен при старте. Это отменяет все изменения, сделанные во время работы, включая сеттеры уровней `ConsoleLogger`; настройки вывода остаются как есть.'}
- ${en:'`CompositeLogger` passes each change to those of its delegates that support it. The other loggers have no levels to change: `Slf4jLogger` takes them from the SLF4J backend, and `MemoryLogger` captures everything. When such a logger is installed directly, the first call prints one `[NANOLOG]` line and nothing changes.', ru:'`CompositeLogger` передаёт каждое изменение тем делегатам, которые его поддерживают. У остальных логгеров нет уровней, которые можно поменять: `Slf4jLogger` берёт их из бэкенда SLF4J, а `MemoryLogger` перехватывает всё. Если такой логгер установлен напрямую, первый вызов печатает одну строку `[NANOLOG]`, и ничего не меняется.'}
- ${en:'A level may also be given as a string, as in the settings: `TRACE` to `OFF`, ignoring case. `LOG.setLevel("com.acme.noisy", "OFF")` switches a package off, and a value from a request or a command line needs no parsing of your own.', ru:'Уровень можно передать и строкой, как в настройках: от `TRACE` до `OFF`, без учёта регистра. `LOG.setLevel("com.acme.noisy", "OFF")` выключает пакет целиком, а значение из запроса или командной строки не нужно разбирать самому.'}
- ${en:'A `null` or blank name and a `null` or unknown level print a `[NANOLOG]` diagnostic and change nothing.', ru:'Пустое имя, `null` вместо имени или уровня и неизвестный уровень печатают диагностику `[NANOLOG]` и ничего не меняют.'}
- ${en:'The same methods exist on `ConsoleLogger`, and so on `JsonLogger` and `FileLogger`, and on `CompositeLogger`. A custom logger supports `LOG.setLevel(...)` by implementing `ILevelControl`.', ru:'Те же методы есть у самих `ConsoleLogger`, а значит, у `JsonLogger` и `FileLogger`, и у `CompositeLogger`. Свой логгер поддерживает `LOG.setLevel(...)`, если реализует `ILevelControl`.'}

### ${en:'Shorter stack traces', ru:'Короткие трассировки стека'}

${en:'Most frames of a stack trace carry no signal: test runners, reflection, proxies. Two settings shorten the traces that `ConsoleLogger` and `JsonLogger` print:', ru:'Большинство кадров трассировки ничего не сообщают: запуск тестов, рефлексия, прокси. Две настройки укорачивают трассировки, которые печатают `ConsoleLogger` и `JsonLogger`:'}

```
-Dnanolog.exception.exclude=org.junit,sun.reflect,jdk.internal.reflect,java.lang.reflect -Dnanolog.exception.maxFrames=40
```

```
java.lang.IllegalStateException: connection refused
	at com.acme.OrderService.load(OrderService.java:87)
	at com.acme.OrderServiceTest.loads(OrderServiceTest.java:21)
	... 68 frames excluded
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
	... 23 frames excluded
```

- ${en:'`exclude` lists package or class prefixes. A run of two or more consecutive frames that match becomes one `... N frames excluded` line; a single matching frame stays, and so does the first frame of every throwable. A prefix matches whole name segments, like a level rule: `org.junit` covers `org.junit.runners.ParentRunner`, not `org.junitx`.', ru:'`exclude` — список префиксов пакетов или классов. Два и больше подряд идущих подходящих кадра превращаются в одну строку `... N frames excluded`; одиночный подходящий кадр остаётся, как и первый кадр каждого исключения. Префикс совпадает только с целыми частями имени, как правило уровня: `org.junit` распространяется на `org.junit.runners.ParentRunner`, но не на `org.junitx`.'}
- ${en:'`maxFrames` prints at most that many frames of each throwable, its causes and suppressed exceptions counted separately; the rest becomes `... N more`, as the frames shared with the enclosing trace already do.', ru:'`maxFrames` печатает не больше указанного числа кадров у каждого исключения, причины и подавленные исключения считаются отдельно; остальное превращается в `... N more`, как уже происходит с кадрами, общими с внешней трассировкой.'}
- ${en:'The layout stays that of `printStackTrace`: `Caused by:`, `Suppressed:` with its indentation, circular references marked. Without either setting the text is exactly what `printStackTrace` prints. A trace that cannot be shortened, because the throwable breaks while it is read, is printed the usual way.', ru:'Раскладка остаётся как у `printStackTrace`: `Caused by:`, `Suppressed:` с отступом, циклические ссылки отмечаются. Без обеих настроек текст в точности тот, что печатает `printStackTrace`. Трассировка, которую не удалось укоротить, потому что исключение ломается при чтении, печатается как обычно.'}
- ${en:'In code: `setExceptionExclude("org.junit", ...)` and `setExceptionMaxFrames(40)` on `ConsoleLogger` and `JsonLogger`. `Slf4jLogger` is not affected: its backend prints exceptions itself.', ru:'В коде: `setExceptionExclude("org.junit", ...)` и `setExceptionMaxFrames(40)` у `ConsoleLogger` и `JsonLogger`. На `Slf4jLogger` это не влияет: исключения печатает его бэкенд.'}

### JsonLogger

${en:'One JSON object per line, for `jq`, log collectors and scripts. It needs no JSON library and is selected without code:', ru:'Один JSON-объект в строке для `jq`, сборщиков логов и скриптов. Библиотека JSON не нужна, а включается логгер без правки кода:'}

```
-Dnanolog.logger=com.nanolaba.logging.JsonLogger
```

```json
{"ts":"2026-11-11T02:30:31.412Z","level":"WARN","logger":"com.acme.PaymentService","thread":"main","message":"Charge of 42 failed, retrying","template":"Charge of {} failed, retrying","args":["42"],"fields":{"amount":42},"context":{"requestId":"r-17"},"caller":{"class":"com.acme.PaymentService","method":"charge","file":"PaymentService.java","line":87}}
```

- `ts` — ${en:'ISO-8601 in UTC with milliseconds, sortable as text;', ru:'ISO-8601 в UTC с миллисекундами, сортируется как текст;'} `level`, `logger` (${en:'the source class', ru:'класс-источник'}), `thread`, `tags` (${en:'the tag of `LOG.tag(...)`, when there is one', ru:'тег `LOG.tag(...)`, если он есть'}), `message` (${en:'the formatted message', ru:'отформатированное сообщение'}).
- `template` ${en:'and', ru:'и'} `args` — ${en:'the raw message and the arguments as strings, for parameterized messages only; equal events can be grouped whatever their arguments.', ru:'исходное сообщение и аргументы строками, только для параметризованных сообщений; одинаковые события можно группировать независимо от аргументов.'}
- `dbg`, `breadcrumb` — `true` ${en:'for an entry of `LOG.dbg` and for one held back by `nanolog.buffer`, see', ru:'для записи `LOG.dbg` и для записи, придержанной `nanolog.buffer`, см.'} [Breadcrumbs](#${en:'breadcrumbs-debug-only-when-something-fails', ru:'breadcrumbs-debug-только-когда-что-то-сломалось'}).
- `fields` — ${en:'the arguments of `LOG.kv(...)` as a nested object, when there are any; `args` leaves out those that no `{}` takes. Select on them with', ru:'аргументы `LOG.kv(...)` вложенным объектом, если они есть; в `args` нет тех, что не занял ни один `{}`. Отбирать по ним можно так:'} `jq 'select(.fields.orderId=="A-17")'`; ${en:'see', ru:'см.'} [${en:'Fields', ru:'Поля'}](#${en:'fields-named-arguments', ru:'поля-именованные-аргументы'}).
- `context` — ${en:'the pairs of `LOG.context(...)` as a nested object, when there are any. Numbers and booleans keep their JSON type, so `jq` compares them as numbers:', ru:'пары `LOG.context(...)` вложенным объектом, если они есть. Числа и логические значения сохраняют свой тип JSON, поэтому `jq` сравнивает их как числа:'} `jq 'select(.context.attempt > 1)'`.
- `caller` — ${en:'the call site, when the source class was detected, with its `source` line under `showSourceLine`;', ru:'место вызова, если класс-источник определялся автоматически, с полем `source` при `showSourceLine`;'} `exception` — `class`, `message` ${en:'and', ru:'и'} `stackTrace`, ${en:'when there is a throwable. Fields without a value are left out.', ru:'если есть исключение. Поля без значения не выводятся.'}
- ${en:'Line breaks and control characters are escaped, so one entry is always exactly one line, stack trace included.', ru:'Переводы строк и управляющие символы экранируются, поэтому одна запись — всегда ровно одна строка, вместе с трассировкой стека.'}
- `JsonLogger` ${en:'extends `ConsoleLogger`: the same levels, the same level properties and the same streams (`ERROR` to `System.err` by default). The display options do not apply.', ru:'наследует `ConsoleLogger`: те же уровни, те же свойства уровней и те же потоки (по умолчанию `ERROR` в `System.err`). Настройки отображения на него не действуют.'}
- ${en:'In a pipe only standard output reaches the next program, so `java -jar app.jar | jq` would lose every `ERROR` entry. `-Dnanolog.console.stream=out` writes them to standard output with the rest, in the order they were logged.', ru:'В конвейер попадает только стандартный вывод, поэтому `java -jar app.jar | jq` потерял бы все записи `ERROR`. `-Dnanolog.console.stream=out` пишет их в стандартный вывод вместе с остальными, в том порядке, в каком они попали в лог.'}

### FileLogger

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'Writes the entries to a file, for an application with no console to redirect (a desktop or an Android app) or a service that keeps a log of its own:', ru:'Пишет записи в файл: для приложения, у которого нет консоли для перенаправления, например настольного или Android-приложения, или для сервиса, который ведёт собственный лог:'}

```properties
nanolog.logger=com.nanolaba.logging.FileLogger
nanolog.file=logs/app.log
nanolog.file.maxSize=10MB
nanolog.file.backups=3
```

- ${en:'The file is appended to, and its directory created, when the first entry comes; `nanolog.file.append=false` empties it then. Without `nanolog.file` it is `nanolog.log` in the working directory.', ru:'Файл дописывается, а его каталог создаётся, когда приходит первая запись; `nanolog.file.append=false` в этот момент очищает файл. Без `nanolog.file` это `nanolog.log` в рабочем каталоге.'}
- ${en:'Each entry is written through at once, so a crash loses nothing that was logged. `nanolog.file.flush=buffer` keeps entries in memory until `LOG.flush()`, a full buffer of 8 KB or the exit of the JVM, for a program that logs a lot.', ru:'Каждая запись пишется в файл сразу, поэтому при падении ничего из залогированного не теряется. `nanolog.file.flush=buffer` держит записи в памяти до `LOG.flush()`, заполнения буфера в 8 КБ или завершения JVM — для программы, которая пишет много.'}
- ${en:'`nanolog.file.maxSize` rolls the file over: `app.log` becomes `app.log.1`, the older files move one up, and the oldest beyond `nanolog.file.backups` is deleted. There is no rolling by date, no compression and no asynchronous writing.', ru:'`nanolog.file.maxSize` включает перенос: `app.log` становится `app.log.1`, более старые файлы сдвигаются на один номер, а самый старый сверх `nanolog.file.backups` удаляется. Переноса по дате, сжатия и асинхронной записи нет.'}
- ${en:'The lines have the layout of `ConsoleLogger` and follow its settings, levels included; `nanolog.file.format=json` writes the lines of `JsonLogger` instead. Repeats are not collapsed, there are no colours, and agent mode leaves the file as it is.', ru:'Строки имеют формат `ConsoleLogger` и следуют его настройкам, включая уровни; `nanolog.file.format=json` пишет вместо них строки `JsonLogger`. Повторы не сворачиваются, цветов нет, а агентский режим файл не меняет.'}
- ${en:'A file that cannot be written is reported once with a `[NANOLOG]` line, and its entries go to `System.err`; a rollover that fails is reported too, and the file keeps growing. Nothing is thrown.', ru:'О файле, в который нельзя писать, один раз сообщает строка `[NANOLOG]`, и его записи идут в `System.err`; о неудачном переносе тоже сообщается, и файл продолжает расти. Исключения не бросаются.'}
- ${en:'The console and a file together, in code: `LOG.init(new CompositeLogger(new ConsoleLogger(), new FileLogger(Paths.get("logs/app.log"))))`. The setters `setAppend`, `setMaxSize`, `setBackups`, `setFlush` and `setFormat` change the settings, and `close()` closes the file until the next entry.', ru:'Консоль и файл вместе, в коде: `LOG.init(new CompositeLogger(new ConsoleLogger(), new FileLogger(Paths.get("logs/app.log"))))`. Сеттеры `setAppend`, `setMaxSize`, `setBackups`, `setFlush` и `setFormat` меняют настройки, а `close()` закрывает файл до следующей записи.'}

### Slf4jLogger

${en:'The bridge to', ru:'Мост к'} **SLF4J**. ${en:'Each entry is forwarded to the', ru:'Каждая запись передаётся в'} `org.slf4j.Logger` ${en:'named after the source class, so your SLF4J backend sees the real caller, not', ru:'с именем класса-источника, поэтому SLF4J-бэкенд видит реального вызывающего, а не'} `LOG`. ${en:'Levels, patterns, appenders and files are configured in that backend as usual. SLF4J 1.7 and 2.x both work: CI runs the tests against both.', ru:'Уровни, шаблоны, аппендеры и файлы настраиваются в этом бэкенде как обычно. Подходят и SLF4J 1.7, и 2.x: CI прогоняет тесты на обеих версиях.'}

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

- ${en:'Location-aware backends, Logback and Log4j 2 among them, are told that the call site is the first frame after `LOG`. Their own `%file:%line`, `%method` and `%class` (Logback) or `%l` (Log4j 2) therefore show the code that called `LOG`, not `Slf4jLogger`, with no backend configuration. slf4j-simple prints no location at all.', ru:'Бэкенды, которые умеют определять место вызова, в том числе Logback и Log4j 2, узнают, что место вызова — первый кадр после `LOG`. Поэтому их собственные `%file:%line`, `%method` и `%class` (Logback) или `%l` (Log4j 2) показывают код, вызвавший `LOG`, а не `Slf4jLogger`, и настраивать бэкенд для этого не нужно. slf4j-simple место вызова не печатает вовсе.'}
- ${en:'A timer or a unit of work writes its line when it ends, and the backend reads the call site from the stack at that moment. `LOG.timed(...)`, `LOG.unit(name, body)` and `run(...)` therefore show their own call. A timer or a unit closed by a try-with-resources block shows the line where the block ends, in the same method: the `LOG.time` or `LOG.unit` call is no longer on the stack, and SLF4J has no way to pass a call site. A unit written as `interrupted` at the end of the run shows a frame of Nanolog. `ConsoleLogger`, `JsonLogger` and the run log show the `LOG.time` or `LOG.unit` call in every case.', ru:'Таймер и единица работы пишут строку, когда заканчиваются, и бэкенд берёт место вызова из стека в этот момент. Поэтому `LOG.timed(...)`, `LOG.unit(name, body)` и `run(...)` показывают собственный вызов. Таймер или единица, которые закрывает блок try-with-resources, показывают строку, где блок заканчивается, в том же методе: вызова `LOG.time` или `LOG.unit` на стеке уже нет, а передать место вызова через SLF4J нельзя. Единица, записанная с исходом `interrupted` в конце запуска, показывает кадр самого Nanolog. `ConsoleLogger`, `JsonLogger` и журнал запуска всегда показывают вызов `LOG.time` или `LOG.unit`.'}
- ${en:'The message reaches SLF4J already formatted, so the backend does not see the `{}` arguments as structured data. Fields of `LOG.kv(...)` that no `{}` takes are appended to the text as `key=value`, as `ConsoleLogger` writes them; the key-value pairs of SLF4J 2 are not used, since backends treat them differently. The tag of `LOG.tag(...)` is passed as an SLF4J `Marker` of that name, so `%marker` of Logback prints it and the filters of the backend can select on it.', ru:'Сообщение приходит в SLF4J уже отформатированным, поэтому бэкенд не видит `{}`-аргументы как структурные данные. Поля `LOG.kv(...)`, которые не занял ни один `{}`, дописываются к тексту как `key=value`, как их пишет `ConsoleLogger`; пары ключ-значение SLF4J 2 не используются, потому что бэкенды обращаются с ними по-разному. Тег `LOG.tag(...)` передаётся как `Marker` SLF4J с тем же именем, поэтому `%marker` в Logback его печатает, а фильтры бэкенда могут по нему отбирать записи.'}
- ${en:'The pairs of `LOG.context(...)` are put into the MDC while an entry is forwarded and taken out afterwards; values the MDC already held for those keys are put back. Backends that support MDC (Logback and Log4j 2) print them through their own patterns such as `%X{requestId}`, like values set through `org.slf4j.MDC`; slf4j-simple and slf4j-nop silently discard them, and the JUL binding (slf4j-jdk14) stores them without printing.', ru:'Пары `LOG.context(...)` кладутся в MDC, пока запись передаётся, и убираются после; значения, которые MDC уже хранил для этих ключей, возвращаются. Бэкенды с поддержкой MDC (Logback и Log4j 2) печатают их через свои шаблоны вроде `%X{requestId}`, как и значения, заданные через `org.slf4j.MDC`; slf4j-simple и slf4j-nop молча их отбрасывают, а привязка к JUL (slf4j-jdk14) хранит их, но не печатает.'}
- ${en:'An entry without a message is passed with an empty message, not the word `null`.', ru:'Запись без сообщения передаётся с пустым сообщением, а не со словом `null`.'}
- ${en:'An entry without a source class goes to the SLF4J `ROOT` logger.', ru:'Запись без класса-источника уходит в `ROOT`-логгер SLF4J.'}
- ${en:'Loggers are cached per source class in a `ConcurrentHashMap` and never evicted, so with dynamic class reloading the cache retains `Class` references.', ru:'Логгеры кешируются по классу-источнику в `ConcurrentHashMap` и не вытесняются, поэтому при динамической перезагрузке классов кеш удерживает ссылки на `Class`.'}

### JulLogger

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'Writes the entries to `java.util.logging`, for an application that configures its logging there, with no SLF4J and no binding:', ru:'Пишет записи в `java.util.logging` — для приложения, которое настраивает логирование там, без SLF4J и без привязки:'}

```properties
nanolog.logger=com.nanolaba.logging.JulLogger
```

- ${en:'Each entry goes to the JUL logger named after its source class. JUL decides the levels (`logging.properties`), and `LOG.isDebugEnabled()` asks it. `TRACE`, `DEBUG`, `INFO`, `WARN` and `ERROR` become `FINEST`, `FINE`, `INFO`, `WARNING` and `SEVERE`, so `LOG.dbg` is a `FINE` record.', ru:'Каждая запись идёт в JUL-логгер, названный по классу-источнику. Уровнями управляет JUL (`logging.properties`), и `LOG.isDebugEnabled()` спрашивает его. `TRACE`, `DEBUG`, `INFO`, `WARN` и `ERROR` становятся `FINEST`, `FINE`, `INFO`, `WARNING` и `SEVERE`, так что `LOG.dbg` — запись уровня `FINE`.'}
- ${en:'The message arrives formatted, followed by the fields of `LOG.kv` and the context as `key=value`, since JUL has no place for them; the record names the call site as its source class and method, which `%2$s` of `SimpleFormatter` prints.', ru:'Сообщение приходит уже отформатированным, а за ним поля `LOG.kv` и контекст в виде `key=value`, поскольку в JUL для них нет места; в записи классом и методом-источником указано место вызова, их печатает `%2$s` у `SimpleFormatter`.'}
- ${en:'On the module path the application needs the module `java.logging`.', ru:'На module path приложению нужен модуль `java.logging`.'}

### MemoryLogger ${en:'and', ru:'и'} CompositeLogger

`MemoryLogger` ${en:'keeps entries in memory for tests. Every level is enabled, so entries are captured whatever the production configuration says, and nothing is printed.', ru:'хранит записи в памяти для тестов. Включены все уровни, поэтому записи перехватываются при любой боевой конфигурации, и ничего не печатается.'}

${widget:import(path='${examples}/CapturingLogs.java', region='capture', wrap='true')}

- `install()` ${en:'replaces the logger of `LOG` and remembers the previous one; `close()` puts it back, even when the test fails. `installForwarding()` does the same but still passes every entry to the previous logger, so the usual output stays visible.', ru:'заменяет логгер `LOG` и запоминает прежний; `close()` возвращает его, даже если тест упал. `installForwarding()` делает то же самое, но продолжает передавать каждую запись прежнему логгеру, и обычный вывод остаётся на месте.'}
- `getEntries()`, `getEntries(level)`, `getEntries(SomeClass.class)`, `getMessages()`, `contains(level, text)`, `clear()` — ${en:'snapshots and short assertions; the logger is thread-safe.', ru:'снимки и короткие проверки; логгер потокобезопасен.'}
- `assertNoneAtOrAbove(WARN)`, `assertLogged(level, text)`, `assertTemplateLogged(level, "Retry {} of {}")` — ${en:'assertions that throw `AssertionError` without JUnit or AssertJ. The message lists every captured entry with its call site and stack trace, so the failing test shows what was logged without a second run. `assertNoneAtOrAbove(WARN)` checks that the code logged no warning and no error; `assertTemplateLogged` matches the message before its arguments were substituted.', ru:'проверки, которые бросают `AssertionError` без JUnit и AssertJ. Сообщение перечисляет все перехваченные записи с местом вызова и трассировкой стека, поэтому упавший тест сразу показывает, что было в логе, без повторного запуска. `assertNoneAtOrAbove(WARN)` проверяет, что код не записал ни одного предупреждения и ни одной ошибки; `assertTemplateLogged` сравнивает сообщение до подстановки аргументов.'}
- `new MemoryLogger(capacity)` ${en:'keeps only the newest entries.', ru:'хранит только самые новые записи.'}
- ${en:'`LOG.init` and `install()` are JVM-wide, so tests that install a logger must not run in parallel with each other. For parallel tests use `capture()`, below.', ru:'`LOG.init` и `install()` действуют на всю JVM, поэтому тесты, устанавливающие логгер, нельзя запускать параллельно друг с другом. Для параллельных тестов есть `capture()`, см. ниже.'}

${en:'Since 1.4, `capture()` keeps the entries of the current thread only, so tests that JUnit runs in parallel in one JVM each see their own, while the logger of `LOG` stays installed for the others:', ru:'Начиная с 1.4 `capture()` перехватывает записи только текущего потока, поэтому тесты, которые JUnit запускает параллельно в одной JVM, видят каждый свои, а логгер `LOG` остаётся установленным для остальных:'}

${widget:import(path='${examples}/CapturingLogs.java', region='parallel', wrap='true')}

- ${en:'The capture takes every level, whatever the levels of the logger. The entries of this thread do not reach that logger, the run log or the run summary, though a logger installed while the capture is open, `MemoryLogger.install()` for one, receives them; `captureForwarding()` passes them on as well, at the levels the logger has enabled. The findings of strict mode and of `nanolog.dbg=fail` on this thread go to the capture too, for `assertNoDiagnostics()`.', ru:'Захват получает все уровни, какие бы уровни ни были у логгера. Записи этого потока не доходят ни до логгера, ни до журнала запуска, ни до сводки, хотя логгер, установленный при открытом захвате, например `MemoryLogger.install()`, их получает; `captureForwarding()` передаёт их и ему, на уровнях, которые у него включены. Находки строгого режима и `nanolog.dbg=fail` в этом потоке тоже попадают в захват, для `assertNoDiagnostics()`.'}
- ${en:'Only the current thread is captured: entries that the code under test logs from a thread pool reach the logger of `LOG`. Captures nest, and `close()` may run on any thread.', ru:'Перехватывается только текущий поток: записи, которые тестируемый код пишет из пула потоков, уходят в логгер `LOG`. Захваты вкладываются друг в друга, а `close()` можно вызвать из любого потока.'}
- ${en:'Without an open capture logging reads one counter and nothing else of the captures.', ru:'Пока ни одного захвата нет, логирование читает один счётчик и больше ничего из захватов.'}

`CompositeLogger` ${en:'sends every entry to several loggers. Each of them receives only the levels it has enabled, and a lazy message is built only if at least one of them wants it. A logger that throws is reported once with a `[NANOLOG]` line and skipped; the others still receive the entry:', ru:'отправляет каждую запись в несколько логгеров. Каждый получает только включённые у него уровни, а ленивое сообщение вычисляется, только если оно нужно хотя бы одному из них. Логгер, который бросает исключение, один раз отмечается строкой `[NANOLOG]` и пропускается, остальные запись всё равно получают:'}

${widget:import(path='${examples}/CapturingLogs.java', region='composite', wrap='true')}

## ${en:'Logs in tests: only the failing ones', ru:'Логи в тестах: только упавших'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

The log of a passing test is noise; the log of a failing one is the evidence. A Spring Boot test suite can print thousands of lines per green run, and the agent, or the person, reading it cannot find the failure in them. `NanologExtension` holds back what each test writes and prints it only when the test fails.<!--en-->
Лог прошедшего теста — шум, лог упавшего — улика. Набор тестов Spring Boot может печатать тысячи строк за зелёный прогон, и агент или человек, который его читает, не находит в них ошибку. `NanologExtension` придерживает то, что пишет каждый тест, и печатает это, только если тест упал.<!--ru-->

```java
@ExtendWith(NanologExtension.class)
class PaymentServiceTest {

    @Test
    void chargeIsRetried() { ... }
}
```

```
--- log of PaymentServiceTest.chargeIsRetried ---
INFO 17.11.2026 10:15:02 [PaymentService] Charging order 17
WARN 17.11.2026 10:15:02 [PaymentService] Charge of 42 failed, retrying
--- end of the log of PaymentServiceTest.chargeIsRetried ---
```

- **What is held back.** What the thread of the test writes to the logger of `LOG`, at the levels of that logger, from the `@BeforeEach` methods to the `@AfterEach` ones. When the test fails, it is written to that logger after all, between the two lines above; when it passes or is aborted, it goes only to the [run log](#run-log), if that is on. A failed test that logged nothing prints nothing.<!--en-->
- **Что придерживается.** То, что поток теста пишет в логгер `LOG`, на уровнях этого логгера, от методов `@BeforeEach` до методов `@AfterEach`. Если тест упал, это всё же пишется в тот логгер, между двумя строками выше; если прошёл или был прерван — только в [журнал запуска](#журнал-запуска), если он включён. Упавший тест, который ничего не залогировал, ничего и не печатает.<!--ru-->
- **The log of the class.** What the class logs outside its tests is held back too: its `@BeforeAll` and `@AfterAll` methods, and the creation of each test instance, where `SpringExtension` starts the application context and the beans log their start-up. When a test of the class fails, or the class itself does, as when a `@BeforeAll` method throws, it is printed after the tests of the class, between `--- log of class PaymentServiceTest ---` and `--- end of the log of class PaymentServiceTest ---`; otherwise it goes only to the run log. Spring caches the context, so the class that happens to start it holds its log. With `@TestInstance(Lifecycle.PER_CLASS)` JUnit creates the instance before the extension starts, so what is logged then is printed as usual.<!--en-->
- **Лог класса.** То, что класс пишет вне своих тестов, тоже придерживается: его методы `@BeforeAll` и `@AfterAll` и создание каждого экземпляра теста, где `SpringExtension` поднимает контекст приложения, а бины пишут о своём запуске. Если упал тест класса или сам класс, например когда метод `@BeforeAll` бросил исключение, этот лог печатается после тестов класса, между `--- log of class PaymentServiceTest ---` и `--- end of the log of class PaymentServiceTest ---`; иначе он попадает только в журнал запуска. Spring кэширует контекст, поэтому его лог достаётся тому классу, который поднял его первым. С `@TestInstance(Lifecycle.PER_CLASS)` JUnit создаёт экземпляр раньше, чем расширение начинает работу, поэтому то, что пишется в этот момент, печатается как обычно.<!--ru-->
- **Parallel tests.** Each test holds back the entries of its own thread, so tests that JUnit runs in parallel keep their logs apart. Entries that the code under test logs from a thread pool are not held back.<!--en-->
- **Параллельные тесты.** Каждый тест придерживает записи своего потока, поэтому тесты, которые JUnit запускает параллельно, не смешивают логи. Записи, которые тестируемый код пишет из пула потоков, не придерживаются.<!--ru-->
- **MemoryLogger works as usual.** A test that installs a `MemoryLogger` or opens `MemoryLogger.capture()` sees its entries; see [MemoryLogger and CompositeLogger](#${en:'memorylogger-and-compositelogger', ru:'memorylogger-и-compositelogger'}).<!--en-->
- **MemoryLogger работает как обычно.** Тест, который устанавливает `MemoryLogger` или открывает `MemoryLogger.capture()`, видит свои записи; см. [MemoryLogger и CompositeLogger](#memorylogger-и-compositelogger).<!--ru-->
- **Settings.** `nanolog.junit.output` (`NANOLOG_JUNIT_OUTPUT`): `failed`, the default, `all` (every log, as it is written) or `none`. With `junit.jupiter.extensions.autodetection.enabled=true` in `junit-platform.properties` JUnit registers the extension for every test class, without `@ExtendWith`.<!--en-->
- **Настройки.** `nanolog.junit.output` (`NANOLOG_JUNIT_OUTPUT`): `failed` по умолчанию, `all` (каждый лог по мере записи) или `none`. С `junit.jupiter.extensions.autodetection.enabled=true` в `junit-platform.properties` JUnit подключает расширение ко всем тестовым классам, без `@ExtendWith`.<!--ru-->
- **No dependency.** The extension is compiled against the JUnit Jupiter API, which the tests have anyway; ${name} itself still has none.<!--en-->
- **Без зависимостей.** Расширение скомпилировано против API JUnit Jupiter, который у тестов и так есть; у самого ${name} зависимостей по-прежнему нет.<!--ru-->

## ${en:'Message formatting', ru:'Форматирование сообщений'}

${widget:import(path='${examples}/Formatting.java', region='formatting', wrap='true')}

- ${en:'`{}` is replaced by the next argument; `\{}` is a literal `{}` and consumes no argument; `\\{}` is a literal backslash followed by a substitution.', ru:'`{}` заменяется очередным аргументом; `\{}` — литеральные `{}`, аргумент не расходуется; `\\{}` — литеральный обратный слеш и подстановка.'}
- ${en:'A `Throwable` in the last position that no `{}` consumes is not an argument: it becomes the exception of the entry, and its stack trace is logged, as in SLF4J. In `LOG.error("Cause: {}", e)` the placeholder takes the exception, so only `e.toString()` is printed. An exception passed first always wins; a trailing one then stays an ordinary argument.', ru:'`Throwable` на последнем месте, который не занял ни один `{}`, не считается аргументом: он становится исключением записи, и его трассировка стека попадает в лог, как в SLF4J. В `LOG.error("Cause: {}", e)` исключение занимает плейсхолдер, поэтому печатается только `e.toString()`. Исключение, переданное первым, всегда главнее; исключение в конце тогда остаётся обычным аргументом.'}
- ${en:'Primitive arrays are rendered with', ru:'Примитивные массивы выводятся через'} `Arrays.toString`, ${en:'object arrays with', ru:'объектные через'} `Arrays.deepToString`.
- ${en:'Every `toString()` is guarded: if it throws, the value becomes `[FAILED toString()]` and nothing propagates to the caller. A throwable whose stack trace cannot be printed becomes `[FAILED printStackTrace()]`.', ru:'Каждый `toString()` защищён: если он бросает исключение, значение превращается в `[FAILED toString()]`, и наружу ничего не вылетает. Исключение, трассировку которого не удаётся напечатать, превращается в `[FAILED printStackTrace()]`.'}
- ${en:'A lazy message is guarded the same way: if its `Supplier` throws, the entry is still logged, with the message `[FAILED supplier: <exception>]`, for example `[FAILED supplier: java.lang.IllegalStateException: no report]`; the exception and the arguments of the entry are kept. The supplier runs at most once.', ru:'Ленивое сообщение защищено так же: если его `Supplier` бросает исключение, запись всё равно попадает в лог с сообщением `[FAILED supplier: <исключение>]`, например `[FAILED supplier: java.lang.IllegalStateException: no report]`; исключение и аргументы записи сохраняются. Supplier вызывается не больше одного раза.'}
- ${en:'`LOG.lazy(supplier)` makes one argument lazy: `LOG.debug("Stats: {}", LOG.lazy(() -> summarize(stats)))` computes the value only if the level is enabled and only once, however many loggers format the entry. Unlike a lazy message, the template stays a template for strict mode, for the `template` and `args` of `JsonLogger` and for `assertTemplateLogged`, and no `isDebugEnabled()` check is needed. A supplier that throws gives `[FAILED supplier: <exception>]` in place of the value. There are no overloads that take a `Supplier` for the arguments: they would make calls ambiguous, from Kotlin as well.', ru:'`LOG.lazy(supplier)` делает ленивым один аргумент: `LOG.debug("Stats: {}", LOG.lazy(() -> summarize(stats)))` вычисляет значение только при включённом уровне и только один раз, сколько бы логгеров ни форматировали запись. В отличие от ленивого сообщения, шаблон остаётся шаблоном для строгого режима, для `template` и `args` в `JsonLogger` и для `assertTemplateLogged`, а проверка `isDebugEnabled()` не нужна. Supplier, который бросает исключение, даёт `[FAILED supplier: <исключение>]` вместо значения. Перегрузок, принимающих `Supplier` для аргументов, нет: они сделали бы вызовы неоднозначными, в том числе из Kotlin.'}
- ${en:'The message object itself may be anything, not only a', ru:'Сам объект сообщения может быть любым, не только'} `String`; ${en:'it is converted with the same guarded', ru:'он преобразуется тем же защищённым'} `toString()`.
- With `nanolog.dump=auto` (`NANOLOG_DUMP=auto`) an argument whose class has no `toString()` of its own is printed field by field, as in [`LOG.dbg`](#objects-without-tostring): `LOG.info("Added {}", line)` prints `Added Line{sku="A-1", qty=2}` instead of `Added Line@1e643faf`. Collections, maps, arrays and `Optional` are walked, so a list of such objects is printed the same way. Everything with a `toString()` of its own, strings included, prints as before, without quotes, limits or redaction. The setting is off by default because it changes existing output; `JsonLogger` puts the result into `message` and `args` as text. Since 1.4.<!--en-->
- С `nanolog.dump=auto` (`NANOLOG_DUMP=auto`) аргумент, у класса которого нет собственного `toString()`, печатается поле за полем, как в [`LOG.dbg`](#объекты-без-tostring): `LOG.info("Added {}", line)` печатает `Added Line{sku="A-1", qty=2}` вместо `Added Line@1e643faf`. Коллекции, словари, массивы и `Optional` обходятся, поэтому список таких объектов печатается так же. Всё, у чего есть собственный `toString()`, включая строки, печатается как раньше, без кавычек, ограничений и скрытия секретов. По умолчанию настройка выключена, потому что меняет привычный вывод; `JsonLogger` кладёт результат в `message` и `args` как текст. Начиная с 1.4.<!--ru-->

### ${en:'Fields: named arguments', ru:'Поля: именованные аргументы'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'`LOG.kv(key, value)` names an argument, so that a script or an agent can select the entry by it instead of matching the text of the message:', ru:'`LOG.kv(key, value)` даёт аргументу имя, чтобы скрипт или агент выбирали запись по нему, а не по тексту сообщения:'}

${widget:import(path='${examples}/Fields.java', region='fields', wrap='true')}

- ${en:'In a `{}` a field prints its value like any argument, so existing messages keep their text.', ru:'В `{}` поле печатает своё значение, как любой аргумент, поэтому текст существующих сообщений не меняется.'}
- ${en:'A field that no `{}` takes is not an extra argument: `ConsoleLogger` and `Slf4jLogger` append it as `key=value`, quoted like the pairs of the [context](#context-tag-the-lines-of-one-request), and strict mode does not report it.', ru:'Поле, которое не занял ни один `{}`, — не лишний аргумент: `ConsoleLogger` и `Slf4jLogger` дописывают его как `key=value` с кавычками, как пары [контекста](#контекст-метка-для-строк-одного-запроса), а строгий режим о нём не сообщает.'}
- ${en:'`JsonLogger` writes every field into `"fields"`, whether a `{}` took it or not; numbers and booleans stay JSON numbers and booleans. Any logger reads them from `LogEntry.getFields()`, and those the message does not show from `getTrailingFields()`.', ru:'`JsonLogger` пишет в `"fields"` все поля, занял их `{}` или нет; числа и логические значения остаются числами и логическими значениями JSON. Любой логгер читает их из `LogEntry.getFields()`, а те, которых нет в сообщении, — из `getTrailingFields()`.'}
- ${en:'An exception goes last, after the fields, and becomes the exception of the entry as usual. A key given twice keeps its last value; `LOG.kv(null, value)` is a plain argument.', ru:'Исключение ставится последним, после полей, и, как обычно, становится исключением записи. Ключ, заданный дважды, сохраняет последнее значение; `LOG.kv(null, value)` — обычный аргумент.'}
- ${en:'Names inside the braces, `{orderId}`, are not supported: `{word}` is ordinary text in existing messages.', ru:'Имена внутри скобок, `{orderId}`, не поддерживаются: `{слово}` — обычный текст в существующих сообщениях.'}

### ${en:'Checking placeholders in tests', ru:'Проверка плейсхолдеров в тестах'}

Снисходительность к `{}` уместна в продакшене, но в разработке она скрывает ошибки: потерянный аргумент<!--ru-->
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

${en:'Two more mistakes are reported the same way, with the call site:', ru:'Так же, с местом вызова, сообщается ещё о двух ошибках:'}

```
[NANOLOG] com.acme.PaymentService.charge(PaymentService.java:91): argument 1 repeats the message of the exception
[NANOLOG] com.acme.LoginService.login(LoginService.java:40): the template is not a constant: "User 17 logged in from {}"
```

- ${en:'An argument that is the message of the exception logged with it, as in `LOG.error("Charge failed: {}", e.getMessage(), e)`: the stack trace prints the message already. Name what failed instead: `LOG.error("Charge of {} failed", orderId, e)`. Only the string that `getMessage()` returns counts, and an exception without a message is not checked.', ru:'Аргумент, который совпадает с сообщением залогированного вместе с ним исключения, как в `LOG.error("Charge failed: {}", e.getMessage(), e)`: трассировка стека и так печатает это сообщение. Лучше назвать, что не удалось: `LOG.error("Charge of {} failed", orderId, e)`. Считается только строка, которую возвращает `getMessage()`, а исключение без сообщения не проверяется.'}
- ${en:'A call site seen with two templates, as in `LOG.info("User " + id + " logged in from {}", ip)`: a template built from data groups nothing, since the `template` of `JsonLogger` and `assertTemplateLogged` see a new one every time. Put the value into a `{}`: `LOG.info("User {} logged in from {}", id, ip)`. The first template of each call site is remembered. Calls without arguments, such as `LOG.info(report)`, and calls with an explicit class are not checked. A call site is a line, so two calls on one line count as one, and a template chosen by `?:` between two constants is reported too.', ru:'Место вызова с двумя разными шаблонами, как в `LOG.info("User " + id + " logged in from {}", ip)`: шаблон, собранный из данных, ничего не группирует, потому что `template` в `JsonLogger` и `assertTemplateLogged` каждый раз видят новый. Значение нужно передать через `{}`: `LOG.info("User {} logged in from {}", id, ip)`. Для каждого места вызова запоминается первый шаблон. Вызовы без аргументов, например `LOG.info(report)`, и вызовы с явным классом не проверяются. Место вызова — это строка, поэтому два вызова в одной строке считаются одним, а шаблон, выбранный через `?:` из двух констант, тоже попадает в отчёт.'}

${en:'For all three checks:', ru:'Для всех трёх проверок:'}

- ${en:'The entry itself is logged exactly as without the check, and nothing is thrown.', ru:'Сама запись логируется так же, как без проверки, и ничего не бросается.'}
- ${en:'Each finding is printed once; the 1000 most recent printed findings are remembered.', ru:'О каждой находке сообщается один раз; запоминаются 1000 последних напечатанных находок.'}
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

${en:'A line on standard error is easy to miss among thousands; `MemoryLogger` turns it into a failing test. Strict mode hands every finding to the installed logger as well (`ILogger.onDiagnostic`), every occurrence and not only the first one of a template, so a test fails even when an earlier test met the same mismatch:', ru:'Строку в потоке ошибок легко пропустить среди тысяч других; `MemoryLogger` превращает её в упавший тест. Строгий режим передаёт каждую находку и установленному логгеру (`ILogger.onDiagnostic`), каждое повторение, а не только первое для шаблона, поэтому тест падает, даже если то же несовпадение уже встретил предыдущий тест:'}

${widget:import(path='${examples}/CapturingLogs.java', region='diagnostics', wrap='true')}

- ${en:'`getDiagnostics()` returns the findings, `assertNoDiagnostics()` fails with all of them in the message; `clear()` drops them together with the entries. `CompositeLogger` passes them to its delegates.', ru:'`getDiagnostics()` возвращает находки, `assertNoDiagnostics()` падает со всеми ними в сообщении; `clear()` удаляет их вместе с записями. `CompositeLogger` передаёт их своим делегатам.'}
- ${en:'Without strict mode there is nothing to report and `assertNoDiagnostics()` passes, so switch it on for the test run as above.', ru:'Без строгого режима сообщать не о чем, и `assertNoDiagnostics()` проходит, поэтому включите его для тестового запуска, как показано выше.'}
- ${en:'`nanolog.dbg=fail` reports `LOG.dbg` calls the same way, see [Leftover calls](#leftover-calls).', ru:'`nanolog.dbg=fail` так же сообщает о вызовах `LOG.dbg`, см. [Забытые вызовы](#забытые-вызовы).'}

## ${en:'Logging payloads safely', ru:'Безопасное логирование содержимого'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'Prompts and responses of a language model, request bodies and documents are either not logged at all or logged raw, keys pasted into them included. `LOG.content(value)` is the middle ground: the text in development, a summary in production.', ru:'Промпты и ответы языковой модели, тела запросов и документы либо не логируются вовсе, либо логируются как есть, вместе с вставленными в них ключами. `LOG.content(value)` — компромисс: текст при разработке, сводка в продакшене.'}

${widget:import(path='${examples}/Payloads.java', region='content', wrap='true')}

- ${en:'**By default** a payload is its length and a hash of eight hexadecimal digits. Equal payloads have equal hashes within a run, so a repeated prompt or a retry is recognised without its text; the hash is keyed with a random key of the JVM, so the log does not reveal a short payload, such as a pasted key, to someone who tries candidates. Java AI frameworks keep prompt content out of the logs by default for the same reason, and the logs a coding agent reads are sent to its model provider.', ru:'**По умолчанию** содержимое — это его длина и хеш из восьми шестнадцатеричных цифр. У одинакового содержимого в пределах запуска одинаковый хеш, поэтому повторный промпт или повтор запроса узнаётся без текста; хеш вычисляется со случайным ключом JVM, поэтому лог не выдаёт короткое содержимое, например вставленный ключ, тому, кто перебирает варианты. ИИ-фреймворки для Java по умолчанию не пускают содержимое промптов в логи по той же причине, а логи, которые читает кодовый агент, уходят его поставщику модели.'}
- ${en:'**`nanolog.content`** (`NANOLOG_CONTENT`) is `hide`, `truncate:<characters>` or `show`, and `nanolog.content.<package or class>` sets it for one package or class, the most specific rule winning, as with levels: `-Dnanolog.content.com.acme.llm=show` shows the prompts of one package while you debug. Rules for packages are system properties or keys of `nanolog.properties`.', ru:'**`nanolog.content`** (`NANOLOG_CONTENT`) — это `hide`, `truncate:<символы>` или `show`, а `nanolog.content.<пакет или класс>` задаёт его для одного пакета или класса, и действует самое точное правило, как с уровнями: `-Dnanolog.content.com.acme.llm=show` показывает промпты одного пакета на время отладки. Правила для пакетов — это системные свойства или ключи `nanolog.properties`.'}
- ${en:'**Escaped.** In the text that reaches the log, line breaks and control characters are escaped, `\n` and `\u001b`, so a pasted payload cannot forge a log line; a tab stays.', ru:'**Экранирование.** В тексте, который попадает в лог, переводы строк и управляющие символы экранируются, `\n` и `\u001b`, поэтому вставленное содержимое не может подделать строку лога; табуляция остаётся.'}
- ${en:'**Taken once.** The text is taken when the entry is created, with the guarded `toString()` of a `{}` argument, so a payload that changes later or breaks its `toString()` changes nothing. A payload in a `LOG.context(...)` is taken for each entry and follows the rule of the class of that entry.', ru:'**Берётся один раз.** Текст берётся при создании записи, тем же защищённым `toString()`, что у аргумента `{}`, поэтому содержимое, которое потом изменилось или чей `toString()` сломан, ничего не меняет. Содержимое в `LOG.context(...)` берётся для каждой записи и подчиняется правилу её класса.'}
- ${en:'**JSON.** In `"fields"` and `"context"` a hidden payload is `{"content_len":1834,"content_hash":"3f2a9c1b"}`; `message` and `args` hold the text as the console writes it.', ru:'**JSON.** В `"fields"` и `"context"` скрытое содержимое — это `{"content_len":1834,"content_hash":"3f2a9c1b"}`; в `message` и `args` — текст в том виде, в каком его пишет консоль.'}
- ${en:'**Known keys are masked.** A shown or truncated payload has keys of model providers (`sk-...`), AWS access keys (`AKIA...`), bearer tokens and JSON web tokens replaced by `sk-***`, `AKIA***`, `Bearer ***` and `eyJ***`. Other secrets inside it, such as a password in plain words, are written as they are; only payloads are scanned, never the text of messages.', ru:'**Известные ключи маскируются.** В показанном или обрезанном содержимом ключи поставщиков моделей (`sk-...`), ключи доступа AWS (`AKIA...`), bearer-токены и JSON web tokens заменяются на `sk-***`, `AKIA***`, `Bearer ***` и `eyJ***`. Другие секреты внутри, например пароль обычными словами, пишутся как есть; сканируется только содержимое, никогда текст сообщений.'}

## ${en:'Masking secrets', ru:'Маскирование секретов'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'Tokens and passwords reach the log through `{}` arguments, fields and the context, and the logs a coding agent reads are sent to its model provider. `LOG.secret(value)` marks a secret, and fields and context named like secrets are masked without it:', ru:'Токены и пароли попадают в лог через аргументы `{}`, поля и контекст, а логи, которые читает кодовый агент, уходят его поставщику модели. `LOG.secret(value)` помечает секрет, а поля и контекст с именами секретов маскируются и без него:'}

```java
LOG.info("Login of {} with {}", user, LOG.secret(token));
// INFO 16.11.2026 10:15:02 [AuthService] Login of alice with ***

LOG.info("Connected", LOG.kv("url", url), LOG.kv("password", password));
// INFO 16.11.2026 10:15:02 [Database] Connected url=jdbc:postgresql://db/shop password=***
```

- ${en:'`LOG.secret` keeps no value and never formats it, so its `toString()` is not called; every logger writes `***`, in the message, in `args` and `fields` of `JsonLogger`, in the MDC of `Slf4jLogger` and in the assertion messages of `MemoryLogger`.', ru:'`LOG.secret` не хранит значение и никогда его не форматирует, поэтому его `toString()` не вызывается; любой логгер пишет `***` — в сообщении, в `args` и `fields` у `JsonLogger`, в MDC у `Slf4jLogger` и в сообщениях проверок `MemoryLogger`.'}
- ${en:'A field of `LOG.kv(...)` or a value of `LOG.context(...)` is masked when its key, or a word of it, is in `nanolog.redact` (`NANOLOG_REDACT`): by default `password`, `passwd`, `secret`, `token`, `apikey`, `credential` and `authorization`. Words are split at separators and at camel case, so `password` covers `db.password` and `userPassword`, and `token` covers `X-Auth-Token` but not `input_tokens`. The value is masked when the entry is created, so a `{}` that takes the field shows `***` too. A list replaces the default one; an empty value turns masking off.', ru:'Поле `LOG.kv(...)` или значение `LOG.context(...)` маскируется, если его ключ или слово ключа есть в `nanolog.redact` (`NANOLOG_REDACT`): по умолчанию `password`, `passwd`, `secret`, `token`, `apikey`, `credential` и `authorization`. Ключ делится на слова по разделителям и по camelCase, поэтому `password` покрывает `db.password` и `userPassword`, а `token` — `X-Auth-Token`, но не `input_tokens`. Значение маскируется при создании записи, поэтому `{}`, который занял поле, тоже показывает `***`. Список заменяет список по умолчанию; пустое значение отключает маскирование.'}
- ${en:'The text of messages is not scanned: scanning every message is slow and gives a false sense of safety. Payloads of', ru:'Текст сообщений не сканируется: сканировать каждое сообщение медленно, и это даёт ложное чувство безопасности. В содержимом'} [`LOG.content`](#${en:'logging-payloads-safely', ru:'безопасное-логирование-содержимого'}) ${en:'have the known key formats masked when they are shown.', ru:'известные форматы ключей маскируются, когда оно показывается.'}

## ${en:'Logging LLM calls', ru:'Логирование вызовов LLM'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'Spring AI and LangChain4j have switches that log requests and responses, but they print `toString()` dumps and pretty-printed JSON. A listener or an advisor of about twenty lines logs one line per call through Nanolog instead: the model, the tokens, the latency and the finish reason, under the field names of the', ru:'У Spring AI и LangChain4j есть переключатели, которые логируют запросы и ответы, но печатают они дампы `toString()` и форматированный JSON. Слушатель или advisor примерно на двадцать строк логирует вместо этого одну строку на вызов через Nanolog: модель, токены, задержку и причину завершения, под именами полей из'} [OpenTelemetry GenAI semantic conventions](https://github.com/open-telemetry/semantic-conventions-genai) (${en:'the `gen_ai.client.inference` span, status Development in November 2026', ru:'span `gen_ai.client.inference`, статус Development на ноябрь 2026 года'}). ${en:'The prompt and the completion go through', ru:'Промпт и ответ проходят через'} [`LOG.content`](#${en:'logging-payloads-safely', ru:'безопасное-логирование-содержимого'})${en:', and at `DEBUG` so does a short transcript.', ru:', а на уровне `DEBUG` — и короткая стенограмма.'}

${widget:alert(type='note', text="${en:'These snippets are not compiled by the build of Nanolog, which targets Java 8, while both frameworks need Java 17. They are written against LangChain4j 1.x and Spring AI 2.0.', ru:'Сборка Nanolog не компилирует эти фрагменты: она рассчитана на Java 8, а обоим фреймворкам нужна Java 17. Они написаны для LangChain4j 1.x и Spring AI 2.0.'}")}

### LangChain4j

${en:'A `ChatModelListener`, registered with `.listeners(List.of(new NanologChatListener()))` on the builder of the model:', ru:'`ChatModelListener`, который подключается через `.listeners(List.of(new NanologChatListener()))` у билдера модели:'}

```java
public class NanologChatListener implements ChatModelListener {

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put("nanolog.start", System.nanoTime());
        if (LOG.isDebugEnabled()) {
            LOG.debug("chat request {}", LOG.content(transcript(context.chatRequest().messages())));
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        ChatResponseMetadata metadata = context.chatResponse().metadata();
        TokenUsage usage = metadata.tokenUsage();
        long start = (Long) context.attributes().get("nanolog.start");
        LOG.info("chat {} {} ms",
                 LOG.kv("gen_ai.request.model", context.chatRequest().parameters().modelName()),
                 LOG.kv("duration_ms", (System.nanoTime() - start) / 1_000_000),
                 LOG.kv("gen_ai.provider.name", context.modelProvider()),
                 LOG.kv("gen_ai.response.model", metadata.modelName()),
                 LOG.kv("gen_ai.usage.input_tokens", usage == null ? null : usage.inputTokenCount()),
                 LOG.kv("gen_ai.usage.output_tokens", usage == null ? null : usage.outputTokenCount()),
                 LOG.kv("gen_ai.response.finish_reasons", metadata.finishReason()),
                 LOG.kv("gen_ai.output.messages", LOG.content(context.chatResponse().aiMessage().text())));
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        LOG.error(context.error(), "chat {} failed",
                  LOG.kv("gen_ai.request.model", context.chatRequest().parameters().modelName()));
    }

    private static String transcript(List<ChatMessage> messages) {
        StringBuilder out = new StringBuilder();
        for (ChatMessage message : messages) {
            String text = message instanceof SystemMessage s ? s.text()
                        : message instanceof UserMessage u && u.hasSingleText() ? u.singleText()
                        : message instanceof AiMessage a ? a.text()
                        : message instanceof ToolExecutionResultMessage t ? t.text()
                        : String.valueOf(message);
            out.append('[').append(message.type()).append("] ").append(text).append('\n');
        }
        return out.toString();
    }
}
```

```
INFO [NanologChatListener] chat gpt-4o-mini 812 ms gen_ai.provider.name=OPEN_AI gen_ai.response.model=gpt-4o-mini-2024-07-18 gen_ai.usage.input_tokens=1834 gen_ai.usage.output_tokens=212 gen_ai.response.finish_reasons=STOP gen_ai.output.messages="[content 980 chars #9c41d2e7]"
```

### Spring AI

${en:'A `CallAdvisor`, registered with `ChatClient.builder(chatModel).defaultAdvisors(new NanologAdvisor())`. It makes each call a', ru:'`CallAdvisor`, который подключается через `ChatClient.builder(chatModel).defaultAdvisors(new NanologAdvisor())`. Он делает каждый вызов'} [${en:'unit of work', ru:'единицей работы'}](#${en:'wide-events-one-line-per-request-canonical-log-lines', ru:'wide-events-одна-строка-на-запрос-canonical-log-lines'}), ${en:'so the line gets the outcome and the latency, and a thrown exception is attached to it:', ru:'поэтому строка получает исход и задержку, а брошенное исключение прикрепляется к ней:'}

```java
public class NanologAdvisor implements CallAdvisor {

    @Override
    public String getName() {
        return "NanologAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // the last advisor, next to the model
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Prompt prompt = request.prompt();
        if (LOG.isDebugEnabled()) {
            LOG.debug("chat request {}", LOG.content(transcript(prompt.getInstructions())));
        }
        LOG.Unit unit = LOG.unit("chat")
                           .put("gen_ai.request.model", prompt.getOptions() == null ? null : prompt.getOptions().getModel());
        return unit.run(() -> {
            ChatClientResponse response = chain.nextCall(request);
            ChatResponse chat = response.chatResponse();
            if (chat != null) {
                ChatResponseMetadata metadata = chat.getMetadata();
                Generation result = chat.getResult();
                unit.put("gen_ai.response.model", metadata.getModel())
                    .put("gen_ai.response.id", metadata.getId())
                    .put("gen_ai.usage.input_tokens", metadata.getUsage().getPromptTokens())
                    .put("gen_ai.usage.output_tokens", metadata.getUsage().getCompletionTokens())
                    .put("gen_ai.response.finish_reasons", result == null ? null : result.getMetadata().getFinishReason())
                    .put("gen_ai.output.messages", LOG.content(result == null ? null : result.getOutput().getText()));
            }
            return response;
        });
    }

    private static String transcript(List<Message> messages) {
        StringBuilder out = new StringBuilder();
        for (Message message : messages) {
            out.append('[').append(message.getMessageType()).append("] ").append(message.getText()).append('\n');
        }
        return out.toString();
    }
}
```

```
INFO [NanologAdvisor] chat ok 812 ms parent=agent-turn gen_ai.request.model=gpt-4o-mini gen_ai.response.model=gpt-4o-mini-2024-07-18 gen_ai.response.id=chatcmpl-B9x2 gen_ai.usage.input_tokens=1834 gen_ai.usage.output_tokens=212 gen_ai.response.finish_reasons=STOP gen_ai.output.messages="[content 980 chars #9c41d2e7]"
```

- ${en:'**A whole agent turn** becomes one unit with `try (LOG.Unit turn = LOG.unit("agent-turn")) { ... }` around it: the calls of the model and the tools inside name it as `parent`, their warnings and errors count in it, and `LOG.wrap` carries it into the threads of the tools.', ru:'**Целый ход агента** становится одной единицей, если обернуть его в `try (LOG.Unit turn = LOG.unit("agent-turn")) { ... }`: вызовы модели и инструментов внутри называют её в поле `parent`, их предупреждения и ошибки считаются в ней, а `LOG.wrap` переносит её в потоки инструментов.'}
- ${en:'**Prompts while debugging:** `-Dnanolog.content=show`, or a rule for the package of the listener such as `-Dnanolog.content.com.acme.ai=truncate:200`. With', ru:'**Промпты при отладке:** `-Dnanolog.content=show` или правило для пакета слушателя, например `-Dnanolog.content.com.acme.ai=truncate:200`. С'} [`nanolog.buffer=DEBUG`](#${en:'breadcrumbs-debug-only-when-something-fails', ru:'breadcrumbs-debug-только-когда-что-то-сломалось'}) ${en:'the transcripts are written only before a failure.', ru:'стенограммы пишутся только перед сбоем.'}
- ${en:'The field names follow the conventions, the values are what the frameworks report: the provider and the finish reason are enum names such as `OPEN_AI` and `STOP`, not the lower-case values of OpenTelemetry. With `JsonLogger` the fields are typed: `jq` sums `.fields["gen_ai.usage.input_tokens"]`.', ru:'Имена полей следуют соглашениям, а значения — то, что сообщают фреймворки: провайдер и причина завершения — имена перечислений вроде `OPEN_AI` и `STOP`, а не значения OpenTelemetry в нижнем регистре. С `JsonLogger` поля типизированы: `jq` суммирует `.fields["gen_ai.usage.input_tokens"]`.'}

## ${en:'Context: tag the lines of one request', ru:'Контекст: метка для строк одного запроса'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'`LOG.context(key, value)` adds a pair to every entry the current thread logs until the scope is closed, so the lines of one request, job or test are found together, with `grep requestId=r-17` or by an agent reading the log:', ru:'`LOG.context(key, value)` добавляет пару ко всем записям текущего потока, пока область не закрыта, поэтому строки одного запроса, задания или теста можно найти вместе: через `grep requestId=r-17` или агентом, который читает лог:'}

${widget:import(path='${examples}/RequestContext.java', region='context', wrap='true')}

- ${en:'`ConsoleLogger` prints the pairs after the message. A value that is empty or contains a space, a quote or `=` goes in double quotes, `user="John Smith"`, so every pair reads as one; `showContext=false` hides them.', ru:'`ConsoleLogger` печатает пары после сообщения. Значение, которое пусто или содержит пробел, кавычку или `=`, берётся в двойные кавычки, `user="John Smith"`, поэтому каждая пара читается как одна; `showContext=false` их скрывает.'}
- ${en:'`JsonLogger` writes a nested object, `"context":{"requestId":"r-17"}`, which cannot collide with `level`, `message` and the other fields; numbers and booleans keep their JSON type.', ru:'`JsonLogger` пишет вложенный объект, `"context":{"requestId":"r-17"}`, который не может столкнуться с `level`, `message` и другими полями; числа и логические значения сохраняют свой тип JSON.'}
- ${en:'`Slf4jLogger` puts the pairs into the MDC while it forwards an entry and takes them out afterwards, so `%X{requestId}` prints them in Logback and Log4j 2.', ru:'`Slf4jLogger` кладёт пары в MDC, пока передаёт запись, а потом убирает их, поэтому `%X{requestId}` печатает их в Logback и Log4j 2.'}
- ${en:'Every entry keeps its context as `LogEntry.getContext()`, so `MemoryLogger` assertions and custom loggers see it, and the assertion messages of `MemoryLogger` show it.', ru:'Каждая запись хранит свой контекст как `LogEntry.getContext()`, поэтому его видят проверки `MemoryLogger` и собственные логгеры, а сообщения проверок `MemoryLogger` его показывают.'}
- ${en:'Scopes nest: an inner scope adds a key or sets one again, and closing it puts the previous value back. Close each scope in the thread that opened it, as try-with-resources does.', ru:'Области вкладываются: внутренняя добавляет ключ или задаёт его заново, а её закрытие возвращает прежнее значение. Закрывайте область в том потоке, который её открыл, как это делает try-with-resources.'}
- ${en:'The context belongs to the thread. A pool thread does not inherit it, because it would inherit the context of the code that happened to create the thread. `LOG.wrap(task)` takes the context and the open unit of `LOG.unit(...)` when it is called, then runs the task with them in whatever thread and removes them when the task ends, also when it throws.', ru:'Контекст принадлежит потоку. Поток пула его не наследует, потому что унаследовал бы контекст кода, который случайно создал поток. `LOG.wrap(task)` берёт контекст и открытую единицу `LOG.unit(...)` в момент вызова, выполняет задачу с ними в любом потоке и убирает их, когда задача закончилась, в том числе с исключением.'}
- ${en:'As with `ExecutorService.submit`, a lambda that returns a value, `() -> list.add(item)`, is a `Callable` to the compiler; write `() -> { list.add(item); }` where a `Runnable` is needed.', ru:'Как и с `ExecutorService.submit`, лямбда, которая возвращает значение, `() -> list.add(item)`, для компилятора — `Callable`; где нужен `Runnable`, пишите `() -> { list.add(item); }`.'}
- ${en:'Every entry reads one `ThreadLocal`. A thread without a context keeps nothing in it, virtual threads included.', ru:'Каждая запись читает один `ThreadLocal`. Поток без контекста ничего в нём не хранит, в том числе виртуальный.'}

### ${en:'Context providers', ru:'Провайдеры контекста'}

${en:'The trace ID of OpenTelemetry, of New Relic or of a request scope lives in a context of its own, not in `LOG.context`. A context provider adds such keys to every entry, without a dependency of Nanolog on that library:', ru:'Trace ID из OpenTelemetry, New Relic или области запроса живёт в собственном контексте, а не в `LOG.context`. Провайдер контекста добавляет такие ключи к каждой записи, и Nanolog не нужна зависимость от этой библиотеки:'}

```java
LOG.addContextProvider(() -> {
    SpanContext span = Span.current().getSpanContext();
    if (!span.isValid()) {
        return null;
    }
    Map<String, Object> ids = new LinkedHashMap<>();
    ids.put("traceId", span.getTraceId());
    ids.put("spanId", span.getSpanId());
    return ids;
});
```

- ${en:'A provider is asked when an entry is created, in the thread that logs: only for an enabled level and once per entry, so keep it cheap. It may return `null` or an empty map; a `null` key is ignored.', ru:'Провайдер вызывается при создании записи, в потоке, который логирует: только для включённого уровня и один раз на запись, поэтому он должен быть дешёвым. Он может вернуть `null` или пустую карту; ключ `null` пропускается.'}
- ${en:'Its keys come after the pairs of `LOG.context`, which win over them, and a provider added earlier wins over a later one. They reach the loggers like the other pairs: `ConsoleLogger` prints `traceId=4bf92f35...` after the message, `JsonLogger` writes them into `"context"`, `Slf4jLogger` puts them into the MDC for `%X{traceId}`, and keys named like secrets are masked.', ru:'Его ключи идут после пар `LOG.context`, которые важнее их, а провайдер, добавленный раньше, важнее добавленного позже. До логгеров они доходят как остальные пары: `ConsoleLogger` печатает `traceId=4bf92f35...` после сообщения, `JsonLogger` пишет их в `"context"`, `Slf4jLogger` кладёт в MDC для `%X{traceId}`, а ключи, названные как секреты, маскируются.'}
- ${en:'A provider that throws is reported once with a `[NANOLOG]` line, and the entry is logged without its keys. An entry that a provider logs itself gets no keys from the providers.', ru:'О провайдере, который бросает исключение, один раз сообщает строка `[NANOLOG]`, а запись логируется без его ключей. Запись, которую логирует сам провайдер, ключей от провайдеров не получает.'}
- ${en:'In the configuration, `nanolog.context.provider=com.acme.TraceIdProvider` names a class that implements `Supplier<Map<String, ?>>` and has a public no-argument constructor; several are separated by commas and come before the providers of `addContextProvider`. A class that cannot be created is reported and left out.', ru:'В конфигурации `nanolog.context.provider=com.acme.TraceIdProvider` называет класс, который реализует `Supplier<Map<String, ?>>` и имеет публичный конструктор без аргументов; несколько классов перечисляются через запятую и идут перед провайдерами `addContextProvider`. Класс, который не удалось создать, попадает в диагностику и не используется.'}
- ${en:'Providers apply to the whole JVM and stay after `LOG.init()`; `LOG.removeContextProvider(provider)` removes one. A task of `LOG.wrap(task)` asks them in the thread that runs it, where OpenTelemetry has carried its own context.', ru:'Провайдеры действуют на всю JVM и сохраняются после `LOG.init()`; `LOG.removeContextProvider(provider)` убирает провайдер. Задача из `LOG.wrap(task)` спрашивает их в потоке, который её выполняет, куда OpenTelemetry уже перенёс свой контекст.'}

## ${en:'Rate limiting', ru:'Ограничение частоты'}

${en:'A retry loop, a full queue or a deprecated option can print the same line thousands of times, and the line that matters scrolls away. `LOG.once()` and `LOG.atMostEvery(...)` limit one statement:', ru:'Цикл повторов, переполненная очередь или устаревшая настройка могут напечатать одну и ту же строку тысячи раз, и важная строка пропадает из виду. `LOG.once()` и `LOG.atMostEvery(...)` ограничивают одну строку кода:'}

${widget:import(path='${examples}/RateLimiting.java', region='limits', wrap='true')}

- ${en:'A statement is its call site, the file and line of the call. Two calls on one line share one limit, and so do all the callers of a helper method that logs for them, unless the helper is skipped:', ru:'Строка кода — это место вызова, файл и номер строки. Два вызова на одной строке делят одно ограничение, как и все, кто вызывает вспомогательный метод, который пишет в лог за них, если этот метод не пропускается:'} [${en:'Logging helpers', ru:'Обёртки над LOG'}](#${en:'logging-helpers', ru:'обёртки-над-log'}).
- ${en:'The next entry that gets through carries the number of calls skipped since the previous one: `ConsoleLogger` prints `[skipped 241]` after the message, `JsonLogger` writes `"skipped":241`, `Slf4jLogger` appends `[skipped 241]` to the message, and any logger reads `LogEntry.getSkippedCount()`.', ru:'Следующая прошедшая запись несёт число вызовов, пропущенных с предыдущей: `ConsoleLogger` печатает `[skipped 241]` после сообщения, `JsonLogger` пишет `"skipped":241`, `Slf4jLogger` дописывает `[skipped 241]` к сообщению, а любой логгер может прочитать `LogEntry.getSkippedCount()`.'}
- ${en:'The level is checked first, the limit last: a call at a disabled level is not counted, and a skipped call builds no message and formats nothing.', ru:'Сначала проверяется уровень, ограничение — последним: вызов выключенного уровня не считается, а пропущенный вызов не строит сообщение и ничего не форматирует.'}
- ${en:'`once()` and `atMostEvery(...)` offer four shapes at every level: `("... {} ...", args)`, `(() -> message)` and both with an exception first. The state of each call site takes a few bytes and is updated without locks, and the number of call sites is bounded by the code.', ru:'`once()` и `atMostEvery(...)` дают четыре формы на каждом уровне: `("... {} ...", args)`, `(() -> message)` и обе с исключением первым аргументом. Состояние места вызова занимает несколько байт и обновляется без блокировок, а число мест вызова ограничено размером кода.'}
- ${en:'A count left at the end of a burst is printed only when the statement runs again. A period of zero or less limits nothing.', ru:'Счётчик, оставшийся после серии вызовов, печатается, только когда строка выполнится снова. Период ноль или меньше ничего не ограничивает.'}

## ${en:'Tags', ru:'Теги'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'Some entries belong to a concern rather than to a class: audit, security, SQL. `LOG.tag(name)` marks them, wherever they come from:', ru:'Некоторые записи относятся к теме, а не к классу: аудит, безопасность, SQL. `LOG.tag(name)` помечает их, откуда бы они ни шли:'}

```java
LOG.tag("audit").warn("User {} changed role to {}", user, role);
LOG.tag("sql").debug("{} rows in {} ms", rows, millis);
```

```
WARN 18.11.2026 10:30:00 [UserService] [audit] User alice changed role to admin
```

- ${en:'Levels per tag: `nanolog.tags=audit=WARN,sql=DEBUG` (`NANOLOG_TAGS`). A rule for the tag of an entry overrides the global level and the package rules, so the audit entries below `WARN` are dropped wherever they come from, and the SQL entries at `DEBUG` are written even under `nanolog.level=INFO`. A tag without a rule follows the level of its class. `resetLevels()` reads the rules again; `LOG.setLevel` does not change them.', ru:'Уровни по тегам: `nanolog.tags=audit=WARN,sql=DEBUG` (`NANOLOG_TAGS`). Правило для тега записи главнее глобального уровня и правил пакетов: записи аудита ниже `WARN` отбрасываются, откуда бы они ни шли, а записи SQL уровня `DEBUG` пишутся даже при `nanolog.level=INFO`. Тег без правила следует уровню своего класса. `resetLevels()` перечитывает правила; `LOG.setLevel` их не меняет.'}
- ${en:'`ConsoleLogger` writes `[audit]` after the source class, `JsonLogger` `"tags":["audit"]`, and `Slf4jLogger` passes an SLF4J `Marker` named after the tag. Any logger reads `LogEntry.getTags()` and may answer `ILogger.isEnabled(level, sourceClass, tag)`; by default it ignores the tag.', ru:'`ConsoleLogger` пишет `[audit]` после класса-источника, `JsonLogger` — `"tags":["audit"]`, а `Slf4jLogger` передаёт `Marker` SLF4J с именем тега. Любой логгер читает `LogEntry.getTags()` и может отвечать на `ILogger.isEnabled(level, sourceClass, tag)`; по умолчанию тег не учитывается.'}
- ${en:'A tag has the four shapes of `LOG.once()` at every level: a message with arguments, a lazy message, and both with an exception first. `LOG.tag("audit").once()` and `.atMostEvery(...)` limit a tagged statement. An entry has at most one tag; `LOG.tag(null)` logs without one.', ru:'У тега те же четыре формы на каждом уровне, что у `LOG.once()`: сообщение с аргументами, ленивое сообщение и оба с исключением впереди. `LOG.tag("audit").once()` и `.atMostEvery(...)` ограничивают помеченную строку. У записи не больше одного тега; `LOG.tag(null)` логирует без тега.'}
- ${en:'A tag costs nothing over an ordinary call. A rule that enables a level for a tag, `sql=DEBUG` under `INFO`, makes the level possible, so every call at that level walks the stack, as a package rule does.', ru:'Тег ничего не стоит сверх обычного вызова. Правило, которое включает уровень для тега, `sql=DEBUG` при `INFO`, делает этот уровень возможным, и каждый вызов этого уровня обходит стек, как и при правиле пакета.'}

## ${en:'Catching exceptions', ru:'Перехват исключений'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'Two patterns repeat in every code base: the `try { ... } catch (Exception e) { LOG.error(...) }` block that logs and carries on, and the exception that kills a background thread and is printed by the JVM as `Exception in thread "pool-1-thread-3"`, outside the log format, outside `JsonLogger` and out of sight of `MemoryLogger`:', ru:'Два шаблона повторяются в каждом проекте: блок `try { ... } catch (Exception e) { LOG.error(...) }`, который логирует и продолжает работу, и исключение, которое убивает фоновый поток и печатается JVM как `Exception in thread "pool-1-thread-3"`, вне формата лога, вне `JsonLogger` и вне поля зрения `MemoryLogger`:'}

${widget:import(path='${examples}/Catching.java', region='catching', wrap='true')}

- ${en:'**When to swallow.** `LOG.catching` is for work whose failure must not stop the caller: closing a resource, deleting a temporary file, a best-effort notification or metric. Do not use it where the caller must know about the failure, such as saving data, charging, or anything whose result the next step relies on: let the exception propagate, or handle it.', ru:'**Когда глотать.** `LOG.catching` — для работы, сбой которой не должен останавливать вызывающего: закрыть ресурс, удалить временный файл, отправить уведомление или метрику по возможности. Не используйте его там, где вызывающий должен узнать о сбое: сохранение данных, списание денег, всё, на результат чего опирается следующий шаг; там пусть исключение летит дальше или обрабатывается.'}
- ${en:'The exception is logged at `ERROR` with its stack trace, from the class and call site of the `LOG.catching` call, and the levels apply as usual. The actions may throw checked exceptions (`LOG.ThrowingRunnable`, `LOG.ThrowingSupplier`). An `Error` is not caught, and an `InterruptedException` is caught with the interrupt flag of the thread set again. Inside a', ru:'Исключение записывается на уровне `ERROR` с трассировкой стека, с классом и местом вызова `LOG.catching`, и уровни действуют как обычно. Действия могут бросать проверяемые исключения (`LOG.ThrowingRunnable`, `LOG.ThrowingSupplier`). `Error` не перехватывается, а `InterruptedException` перехватывается, и флаг прерывания потока ставится снова. Внутри'} [${en:'unit', ru:'единицы работы'}](#${en:'wide-events-one-line-per-request-canonical-log-lines', ru:'wide-events-одна-строка-на-запрос-canonical-log-lines'}) ${en:'a caught exception makes the unit fail, like any `ERROR`.', ru:'перехваченное исключение делает единицу неудачной, как любой `ERROR`.'}
- ${en:'**Threads.** `LOG.installUncaughtExceptionHandler()` installs a default handler that logs `Uncaught exception in thread worker-1` with the stack trace, then passes the exception to the handler installed before, so test frameworks and existing handlers still see it; without one, the JVM no longer prints it itself. Installing again changes nothing.', ru:'**Потоки.** `LOG.installUncaughtExceptionHandler()` ставит обработчик по умолчанию, который пишет `Uncaught exception in thread worker-1` с трассировкой стека и затем передаёт исключение обработчику, стоявшему раньше, поэтому тестовые фреймворки и существующие обработчики его по-прежнему видят; если прежнего не было, JVM больше не печатает его сама. Повторная установка ничего не меняет.'}
- ${en:'The entry of an uncaught exception has the class and call site of the first frame of its stack trace outside the JDK, the code that failed, not the dispatch code of the JVM. The dying thread first writes its', ru:'У записи о необработанном исключении класс и место вызова первого кадра его стека вне JDK — кода, который упал, а не кода JVM, который его доставил. Умирающий поток сначала пишет свои'} [breadcrumbs](#${en:'breadcrumbs-debug-only-when-something-fails', ru:'breadcrumbs-debug-только-когда-что-то-сломалось'}), ${en:'then the exception, then its units still open, as `interrupted`.', ru:'затем исключение, затем свои ещё открытые единицы работы с исходом `interrupted`.'}

## ${en:'System.out and System.err', ru:'System.out и System.err'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'Libraries and old code print with `System.out.println` and `e.printStackTrace()`. Their lines have no level and no source, miss `JsonLogger` and are not captured by `MemoryLogger`. `LOG.captureSystemStreams()`, or `nanolog.captureSystemStreams=true`, makes each such line an entry:', ru:'Библиотеки и старый код печатают через `System.out.println` и `e.printStackTrace()`. У их строк нет уровня и источника, они проходят мимо `JsonLogger` и не попадают в `MemoryLogger`. `LOG.captureSystemStreams()` или `nanolog.captureSystemStreams=true` превращают каждую такую строку в запись:'}

```
INFO 18.11.2026 10:30:00 [LegacyImporter] Imported 120 rows
ERROR 18.11.2026 10:30:01 [LegacyImporter] java.io.IOException: disk full
```

- ${en:'A line of `System.out` becomes an `INFO` entry, a line of `System.err` an `ERROR` entry; the levels apply, so a line at a disabled level is dropped. The source class and the call site are those of the code that printed; the frames of `PrintStream`, `printf` and `printStackTrace` are skipped.', ru:'Строка из `System.out` становится записью `INFO`, строка из `System.err` — записью `ERROR`; уровни действуют, поэтому строка выключенного уровня отбрасывается. Класс-источник и место вызова — того кода, который печатал; кадры `PrintStream`, `printf` и `printStackTrace` пропускаются.'}
- ${en:'A line ends at `\n` or `\r\n`, or when the stream is flushed; a partial line waits, per thread, so the lines of two threads do not mix. Empty lines are dropped, and each line of a stack trace is an entry of its own. The text is kept whole whatever the charset of the platform.', ru:'Строка заканчивается на `\n` или `\r\n` либо при сбросе потока; незаконченная строка ждёт, отдельно для каждого потока выполнения, поэтому строки двух потоков не смешиваются. Пустые строки отбрасываются, а каждая строка трассировки стека становится отдельной записью. Текст сохраняется целиком при любой кодировке платформы.'}
- ${en:'No loop: Nanolog writes its own output, the console included, to the streams it found when the capture started, and what a logger prints to `System.out` while it writes an entry passes straight through.', ru:'Петли нет: Nanolog пишет свой вывод, в том числе консольный, в потоки, которые застал при начале перехвата, а то, что логгер печатает в `System.out`, пока пишет запись, проходит насквозь.'}
- ${en:'`LOG.restoreSystemStreams()` puts the streams back, unless something replaced them since; capturing or restoring twice changes nothing.', ru:'`LOG.restoreSystemStreams()` возвращает потоки на место, если их с тех пор никто не заменил; повторный перехват или возврат ничего не меняют.'}

## ${en:'java.util.logging and System.Logger into Nanolog', ru:'java.util.logging и System.Logger в Nanolog'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'Many libraries log through `java.util.logging`, and the JDK through `System.Logger`. Their lines come in another format on another stream and miss `JsonLogger` and `MemoryLogger`. `NanologHandler` turns their records into entries of Nanolog:', ru:'Многие библиотеки пишут лог через `java.util.logging`, а JDK — через `System.Logger`. Их строки приходят в другом формате и в другой поток, мимо `JsonLogger` и `MemoryLogger`. `NanologHandler` превращает их записи в записи Nanolog:'}

```java
NanologHandler.install();   // ${en:'once, at startup', ru:'один раз, при старте'}
```

${en:'or in `logging.properties`:', ru:'или в `logging.properties`:'}

```properties
handlers=com.nanolaba.logging.NanologHandler
```

- ${en:'`install()` replaces the handlers of the root logger, its console handler among them, so nothing is printed twice; `uninstall()` puts them back. A record becomes an entry of the code that called JUL, with the call site and the exception; the message is formatted as JUL formats it, `{0}` parameters and resource bundles included.', ru:'`install()` заменяет обработчики корневого логгера, в том числе консольный, поэтому ничего не печатается дважды; `uninstall()` возвращает их. Запись становится записью кода, который вызвал JUL, с местом вызова и исключением; сообщение форматируется так, как его форматирует JUL, с параметрами `{0}` и resource bundle.'}
- ${en:'`SEVERE` becomes `ERROR`, `WARNING` `WARN`, `INFO` `INFO`, `CONFIG` and `FINE` `DEBUG`, `FINER` and `FINEST` `TRACE`; then the levels of Nanolog apply.', ru:'`SEVERE` становится `ERROR`, `WARNING` — `WARN`, `INFO` — `INFO`, `CONFIG` и `FINE` — `DEBUG`, `FINER` и `FINEST` — `TRACE`; затем действуют уровни Nanolog.'}
- ${en:'JUL decides first: a record below the level of its JUL logger, `INFO` by default, never reaches Nanolog. To see the `FINE` records of one library, lower its level in JUL too, `com.zaxxer.hikari.level=FINE`; lowering the level of the root logger instead makes JUL build a record for every disabled call of every library.', ru:'Сначала решает JUL: запись ниже уровня её JUL-логгера (по умолчанию `INFO`) до Nanolog не доходит. Чтобы увидеть записи `FINE` одной библиотеки, понизьте её уровень и в JUL, `com.zaxxer.hikari.level=FINE`; если вместо этого понизить уровень корневого логгера, JUL будет строить запись на каждый отключённый вызов каждой библиотеки.'}
- ${en:'`System.Logger` (Java 9+) reaches the handler as well: the JDK sends it to JUL while the module `java.logging` is present. Nanolog registers no `System.LoggerFinder` of its own, which would take over `System.Logger` in every application that has Nanolog on its classpath.', ru:'`System.Logger` (Java 9+) тоже доходит до обработчика: JDK отправляет его в JUL, пока есть модуль `java.logging`. Собственного `System.LoggerFinder` Nanolog не регистрирует: он перехватывал бы `System.Logger` в каждом приложении, у которого Nanolog есть в classpath.'}
- ${en:'Not together with `JulLogger`: entries would go round from Nanolog to JUL and back. A record that comes back is dropped, with one `[NANOLOG]` line.', ru:'Не вместе с `JulLogger`: записи ходили бы по кругу из Nanolog в JUL и обратно. Вернувшаяся запись отбрасывается, и об этом сообщает одна строка `[NANOLOG]`.'}

## ${en:'Caller detection', ru:'Определение вызывающего класса'}

Когда класс не передан явно, `LOG` проходит по стеку и берёт первый кадр,<!--ru-->
который не принадлежит `LOG`, `LogEntry` или реализации `ILogger`. На Java 9+ это делает<!--ru-->
`StackWalker`, который останавливается на этом кадре; на Java 8 — `Reflection.getCallerClass`, который даёт класс каждого кадра.<!--ru-->
When no class is passed explicitly, `LOG` walks the stack and takes the first frame<!--en-->
that does not belong to `LOG`, `LogEntry` or an `ILogger` implementation. On Java 9+ a<!--en-->
`StackWalker` does it and stops at that frame; on Java 8 `Reflection.getCallerClass` gives the class of each frame.<!--en-->

- ${en:'Nested, anonymous and lambda classes are attributed to their top-level enclosing class.', ru:'Вложенные, анонимные классы и лямбды относятся к своему внешнему классу верхнего уровня.'}
- ${en:'The frame found by the walk is kept as `LogEntry.getCaller()`: the exact class, method, file and line of the call, including nested classes and lambdas. It costs nothing extra, and it is `null` when the class was passed explicitly.', ru:'Найденный кадр сохраняется в `LogEntry.getCaller()`: точный класс, метод, файл и строка вызова, в том числе для вложенных классов и лямбд. Это ничего не стоит сверх обхода стека; при явно переданном классе значение равно `null`.'}
- ${en:'On Java 9+ the stack walker hands over the class of each frame, so the caller is found exactly whatever class loader it lives in. On Java 8 the internal `sun.reflect.Reflection.getCallerClass(int)` does the same, and only the frames up to the caller become `StackTraceElement`s (`JavaLangAccess.getStackTraceElement`); where the two disagree, under `Method.invoke`, the call falls back to the stack trace of a `Throwable`. Where neither is available (another JVM, a native image without the metadata for `StackWalker`), the frames of a `Throwable` carry only class names: they are resolved through the context class loader of the thread first and the class loader of `LOG` second, so the detection works when Nanolog lives in a shared loader of an application server and the caller in a web application. A frame neither loader can see or load is skipped, whatever the failure: a missing class, a linkage error such as `NoClassDefFoundError`, or `MissingReflectionRegistrationError` from GraalVM in a native image.', ru:'На Java 9+ обход стека получает сам класс каждого кадра, поэтому вызывающий класс находится точно, в каком бы загрузчике он ни был. На Java 8 то же делает внутренний `sun.reflect.Reflection.getCallerClass(int)`, и в `StackTraceElement` превращаются только кадры до вызывающего (`JavaLangAccess.getStackTraceElement`); где они расходятся, под `Method.invoke`, вызов переходит к стеку `Throwable`. Там, где нет ни того, ни другого (другая JVM, native image без метаданных для `StackWalker`), кадры `Throwable` содержат только имена классов: они ищутся сначала контекстным загрузчиком потока, затем загрузчиком `LOG`, поэтому определение работает, когда Nanolog лежит в общем загрузчике сервера приложений, а вызывающий код — в веб-приложении. Кадр, класс которого ни один загрузчик не видит или не может загрузить, пропускается, какой бы ни была ошибка: класса нет, ошибка линковки вроде `NoClassDefFoundError` или `MissingReflectionRegistrationError` от GraalVM в native image.'}
- ${en:'The stack is walked only for a level the logger may have enabled: `LOG` first asks `ILogger.mayBeEnabled(level)`, so a level that is off for every class, such as `DEBUG` under `nanolog.level=INFO`, costs no walk. `LOG.isDebugEnabled()` follows the same rule. Once a level is on anywhere, even for one package (`nanolog.level.com.acme.db=TRACE`), every call at that level walks the stack, because only the class tells whether it is enabled. In hot paths pass the class explicitly:', ru:'Стек обходится, только если уровень может быть включён хотя бы для одного класса: `LOG` сначала спрашивает `ILogger.mayBeEnabled(level)`, поэтому уровень, выключенный для всех классов, например `DEBUG` при `nanolog.level=INFO`, обхода не требует. `LOG.isDebugEnabled()` подчиняется тому же правилу. Если уровень включён хоть где-то, даже для одного пакета (`nanolog.level.com.acme.db=TRACE`), каждый вызов этого уровня обходит стек: включён ли он, решает класс. В горячих местах передавайте класс явно:'} `LOG.debug(MyClass.class, ...)`.
- ${en:'Any class implementing `ILogger` is skipped, so a custom logger that logs through `LOG` must pass its source class explicitly.', ru:'Любой класс, реализующий `ILogger`, пропускается, поэтому свой логгер, который сам пишет через `LOG`, должен передавать класс-источник явно.'}
- ${en:'If no frame qualifies, the entry is still logged, without a source class and a call site, as with `LOG.log(level, null, ...)`. That happens when the code runs entirely inside an `ILogger` class, or on Java 8 when the JVM records no stack traces (`-XX:-StackTraceInThrowable`). `Slf4jLogger` sends such entries to the `ROOT` logger.', ru:'Если подходящего кадра нет, запись всё равно попадает в лог, но без класса-источника и места вызова, как при `LOG.log(level, null, ...)`. Так бывает, когда код целиком выполняется внутри класса, реализующего `ILogger`, или на Java 8, когда JVM не записывает стек (`-XX:-StackTraceInThrowable`). `Slf4jLogger` отправляет такие записи в логгер `ROOT`.'}

### ${en:'Logging helpers', ru:'Обёртки над LOG'}

${en:'*Since 1.4.*', ru:'*С версии 1.4.*'}

${en:'A helper that logs for others, such as an `Audit.record(...)` method or a base class of tasks, would be the source of every entry it logs, and its callers would share one rate limit of `LOG.once()`. Name such helpers in `nanolog.caller.skip` (or `NANOLOG_CALLER_SKIP`), class or package names separated by commas:', ru:'Вспомогательный код, который пишет в лог за других, например метод `Audit.record(...)` или базовый класс задач, оказался бы источником каждой своей записи, а все его вызывающие делили бы одно ограничение `LOG.once()`. Такие обёртки перечисляются в `nanolog.caller.skip` (или `NANOLOG_CALLER_SKIP`) — имена классов или пакетов через запятую:'}

```properties
nanolog.caller.skip=com.acme.Audit,com.acme.logging
```

- ${en:'Their frames are skipped like those of `LOG`: the entry gets the source class, the call site and the rate limit of the code that called the helper.', ru:'Их кадры пропускаются так же, как кадры `LOG`: запись получает класс-источник, место вызова и ограничение частоты того кода, который вызвал обёртку.'}
- ${en:'A class covers its nested classes, and a package its classes and subpackages, by whole segments of the name: `com.acme.log` does not cover `com.acme.logging`. A nested class alone is written with `$`, `com.acme.Outer$Helper`.', ru:'Класс охватывает свои вложенные классы, а пакет — свои классы и подпакеты, по целым сегментам имени: `com.acme.log` не охватывает `com.acme.logging`. Отдельный вложенный класс записывается через `$`: `com.acme.Outer$Helper`.'}
- ${en:'A library that wraps `LOG` cannot rely on the settings of the application: it calls `LOG.skipCaller(Helper.class)` once, for example in a static initializer. These classes are added to those named in the setting and stay skipped after `LOG.init()`.', ru:'Библиотека, которая оборачивает `LOG`, не может полагаться на настройки приложения: она один раз вызывает `LOG.skipCaller(Helper.class)`, например в статическом инициализаторе. Такие классы добавляются к настройке и остаются пропущенными после `LOG.init()`.'}
- ${en:'`Slf4jLogger` passes the helper to a location-aware backend as the caller boundary, so `%file:%line` of Logback and `%l` of Log4j 2 point at the code that called the helper as well.', ru:'`Slf4jLogger` передаёт обёртку как границу вызова бэкенду, который определяет место вызова, поэтому `%file:%line` в Logback и `%l` в Log4j 2 тоже указывают на код, вызвавший обёртку.'}
- ${en:'The match is found once per class and cached, so a skipped helper adds no string comparison to later calls. An invalid name, such as `com.acme.*`, prints a `[NANOLOG]` diagnostic and is left out; when every frame is skipped, the entry is logged without a source class, as above.', ru:'Совпадение ищется один раз на класс и запоминается, поэтому пропуск обёртки не добавляет сравнений строк в последующие вызовы. Неверное имя, например `com.acme.*`, печатает диагностику `[NANOLOG]` и не учитывается; если пропущены все кадры, запись логируется без класса-источника, как описано выше.'}

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
- `isDbg()`, `isBreadcrumb()` — ${en:'whether the entry comes from `LOG.dbg`, and whether `nanolog.buffer` held it back.', ru:'пришла ли запись из `LOG.dbg` и придержал ли её `nanolog.buffer`.'}
- `getFields()`, `getTrailingFields()` — ${en:'the arguments of `LOG.kv(...)` by name, and those of them that no `{}` took.', ru:'аргументы `LOG.kv(...)` по именам и те из них, которые не занял ни один `{}`.'}
- `getContext()` — ${en:'the pairs of `LOG.context(...)` open in the thread that created the entry; an empty map without them.', ru:'пары `LOG.context(...)`, открытые в потоке, который создал запись; без них — пустая карта.'}
- `getFormattedMessage()` — ${en:'the final string with `{}` substituted.', ru:'итоговая строка с подставленными `{}`.'}

${en:'`isEnabled(...)` is called before the `LogEntry` is built and before any `Supplier` is resolved, so returning `false` skips the whole cost of the message. It does not skip caller detection: for a call without an explicit class the source class is found first, so the stack has already been walked; see', ru:'`isEnabled(...)` вызывается до создания `LogEntry` и до раскрытия `Supplier`, поэтому возврат `false` избавляет от всей стоимости сообщения. От определения вызывающего класса он не избавляет: для вызова без явного класса класс-источник находится заранее, и стек к этому моменту уже обойдён; см.'} [${en:'Caller detection', ru:'Определение вызывающего класса'}](#${en:'caller-detection', ru:'определение-вызывающего-класса'}).

${en:'To spare the walk as well, override `mayBeEnabled(level)`, as the example above does: `LOG` asks it before caller detection, and `false` means that no class has the level enabled. The default `true` is always safe; `false` for a level that `isEnabled(...)` accepts for some class loses those entries. `ConsoleLogger`, `JsonLogger` and `CompositeLogger` implement it; a subclass that overrides `isEnabled(...)` but not `mayBeEnabled(...)` gets `true`.', ru:'Чтобы сэкономить и обход, переопределите `mayBeEnabled(level)`, как в примере выше: `LOG` спрашивает его до определения вызывающего класса, и `false` означает, что уровень не включён ни для одного класса. Значение по умолчанию `true` безопасно всегда; `false` для уровня, который `isEnabled(...)` пропускает хотя бы для одного класса, теряет эти записи. `ConsoleLogger`, `JsonLogger` и `CompositeLogger` его реализуют; подкласс, который переопределяет `isEnabled(...)`, но не `mayBeEnabled(...)`, получает `true`.'}

${en:'A logger that throws does not break the code that logs. The first failure of each logger class prints one line to `System.err`, for example', ru:'Логгер, который бросает исключение, не ломает код, который пишет в лог. Первый сбой каждого класса логгера печатает одну строку в `System.err`, например'} `[NANOLOG] Logger com.acme.AuditLogger threw java.io.UncheckedIOException: disk full; entries it fails to write are printed to System.err`. ${en:'Every entry the logger fails to write then goes to `System.err` in the `ConsoleLogger` format, so nothing is lost. If `isEnabled(...)` throws, the level counts as enabled.', ru:'Каждая запись, которую логгер не смог записать, уходит в `System.err` в формате `ConsoleLogger`, так что ничего не теряется. Если бросает `isEnabled(...)`, уровень считается включённым.'}

## ${en:'Thread safety and performance', ru:'Потокобезопасность и производительность'}

- ${en:'The static', ru:'Статические методы'} `LOG` ${en:'methods are safe to call from any thread as long as the installed', ru:'можно вызывать из любого потока, пока установленный'} `ILogger` ${en:'is;', ru:'потокобезопасен;'} `ConsoleLogger` ${en:'and', ru:'и'} `Slf4jLogger` ${en:'are.', ru:'таковы.'}
- ${en:'A logger installed with `LOG.init(...)` or `MemoryLogger.install()` is used by every thread from its next logging call, threads that are already running included. Levels may change at any time, through `LOG.setLevel(...)` and the other level methods or the level setters of `ConsoleLogger`. The display setters (`setShowDate`, `setDateFormat` and the others) are meant for startup, not for concurrent reconfiguration.', ru:'Логгер, установленный через `LOG.init(...)` или `MemoryLogger.install()`, используется всеми потоками начиная со следующего вызова логирования, в том числе уже запущенными. Уровни можно менять в любой момент через `LOG.setLevel(...)` и другие методы уровней или сеттеры уровней `ConsoleLogger`. Сеттеры вывода (`setShowDate`, `setDateFormat` и другие) рассчитаны на старт приложения, а не на параллельную перенастройку.'}
- ${en:'A call at a level that is off for every class costs one `mayBeEnabled` check, or one `isEnabled` check with an explicit class. If a level is on for some package or class, a call at that level without an explicit class costs one stack walk and an `isEnabled` check even where the level is off, so a single rule such as `nanolog.level.com.acme.db=TRACE` makes every `trace` call without a class walk the stack. Message formatting and `Supplier` evaluation happen only for enabled levels.', ru:'Вызов уровня, выключенного для всех классов, стоит одну проверку `mayBeEnabled`, а с явным классом — одну проверку `isEnabled`. Если уровень включён для какого-то пакета или класса, вызов без явного класса стоит обхода стека и проверки `isEnabled` даже там, где уровень выключен: одно правило вроде `nanolog.level.com.acme.db=TRACE` заставляет каждый вызов `trace` без класса обходить стек. Форматирование сообщения и `Supplier` вычисляются только для включённых уровней.'}
- ${en:'Measured with JMH on JDK 17 (Ryzen 5 5600X, Windows 10): a call at a level that is off for every class takes about 2 ns without a class and 7 ns with one. The stack walk, the price of a call without a class at a level enabled for some class, takes about 2.5 µs near the top of the stack and 3 µs at 100 frames; Java 8, which has no `StackWalker`, needs about 5 µs and 8 µs. An enabled line through `ConsoleLogger` takes under 1 µs, plus the walk if the class is not given. Other JDKs and the method:', ru:'Замеры JMH на JDK 17 (Ryzen 5 5600X, Windows 10): вызов уровня, выключенного для всех классов, занимает около 2 нс без класса и 7 нс с явным классом. Обход стека — цена вызова без класса для уровня, включённого хотя бы для одного класса, — стоит около 2,5 мкс у вершины стека и 3 мкс на глубине 100 кадров; на Java 8, где нет `StackWalker`, — около 5 и 8 мкс. Включённая строка через `ConsoleLogger` занимает меньше 1 мкс плюс обход, если класс не передан. Другие JDK и методика:'} [src/jmh/RESULTS.md](src/jmh/RESULTS.md).

${widget:if(cond='endsWith(${devVersion}, -SNAPSHOT)')}
## ${en:'Using SNAPSHOT versions', ru:'Использование SNAPSHOT-версий'}

${en:'Development builds are published to the Sonatype snapshot repository. Add it and depend on the `-SNAPSHOT` version:', ru:'Сборки версии в разработке публикуются в репозиторий снапшотов Sonatype. Подключите его и укажите версию с суффиксом `-SNAPSHOT`:'}

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

## ${en:'Related projects', ru:'Похожие проекты'}

Where Nanolog stands among the loggers you may already know. Compared on 2026-11-18 with the versions in the table headers, from their documentation, their sources and runs on Java 8. ✅ = out of the box, ➖ = partial, ❌ = not supported. Each feature of Nanolog links to the section that describes it.<!--en-->
Место Nanolog среди логгеров, которые вы, возможно, уже знаете. Сравнение сделано 2026-11-18 для версий из заголовков таблиц, по их документации, исходникам и запускам на Java 8. ✅ — из коробки, ➖ — частично, ❌ — нет. Каждая возможность, которая есть в Nanolog, ведёт на раздел с её описанием.<!--ru-->

### ${en:'Static facades', ru:'Статические фасады'}

The direct analogues: a static call without a logger field.<!--en-->
Прямые аналоги: статический вызов без поля логгера.<!--ru-->

| ${en:'Feature', ru:'Возможность'} | **Nanolog** | [tinylog][tinylog] 2.8.1 | [jcabi-log][jcabi-log] 0.24.3 | [minlog][minlog] 1.3.1 | [Timber][timber] 5.0.1 |
|---|:-:|:-:|:-:|:-:|:-:|
| [${en:'No logger field', ru:'Без поля логгера'}](#${en:'caller-detection', ru:'определение-вызывающего-класса'}) | ✅ | ✅ | ✅ | ✅ | ✅ |
| [${en:'Source class found automatically', ru:'Класс-источник определяется сам'}](#${en:'caller-detection', ru:'определение-вызывающего-класса'}) | ✅ | ✅ | ❌¹ | ❌ | ✅² |
| [${en:'Call site in the output', ru:'Место вызова в выводе'}](#consolelogger) | ✅ | ✅ | ❌ | ❌ | ➖³ |
| [${en:'Runtime dependencies', ru:'Зависимости во время работы'}](#${en:'installation', ru:'установка'}) | ${en:'one jar', ru:'один jar'} | api + impl⁴ | ${en:'SLF4J + a backend', ru:'SLF4J + бэкенд'} | ${en:'one jar', ru:'один jar'} | ${en:'Android only', ru:'только Android'} |
| ${en:'Java baseline', ru:'Минимальная Java'} | Java 8 | Java 6 | Java 8 | Java 6 | Android API 14 |
| [${en:'Levels without a configuration file', ru:'Уровни без файла конфигурации'}](#${en:'levels-and-options-without-code', ru:'уровни-и-настройки-без-кода'}) | ✅ | ✅ | ➖⁵ | ❌ | ❌ |
| [${en:'Levels changed at run time', ru:'Уровни меняются во время работы'}](#${en:'changing-levels-at-run-time', ru:'изменение-уровней-во-время-работы'}) | ✅ | ❌⁶ | ➖⁵ | ✅ | ➖⁷ |
| [${en:'Placeholders', ru:'Плейсхолдеры'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) | `{}` | `{}`⁸ | `%s` | ${en:'none', ru:'нет'} | `%s` |
| [${en:'A trailing exception keeps its stack trace', ru:'Исключение после аргументов сохраняет трассировку'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) | ✅ | ❌⁹ | ❌¹⁰ | ➖¹¹ | ❌¹² |
| [${en:'A throwing `toString()` does not break the call', ru:'Падающий `toString()` не ломает вызов'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) | ✅ | ❌ | ❌ | ❌¹³ | ❌ |
| [${en:'Lazy messages', ru:'Ленивые сообщения'}](#${en:'api-at-a-glance', ru:'api-одним-взглядом'}) | ✅ | ✅ | ❌ | ❌ | ❌ |
| [${en:'Rate limiting per call site', ru:'Ограничение частоты на месте вызова'}](#${en:'rate-limiting', ru:'ограничение-частоты'}) | ✅ | ❌ | ❌ | ❌ | ❌ |
| [${en:'Shorter stack traces', ru:'Короткие трассировки стека'}](#${en:'shorter-stack-traces', ru:'короткие-трассировки-стека'}) | ✅ | ➖¹⁴ | ❌ | ❌ | ❌ |
| [${en:'JSON lines built in', ru:'JSON lines из коробки'}](#jsonlogger) | ✅ | ✅¹⁵ | ➖⁵ | ❌ | ❌ |
| [${en:'Context of a thread (MDC)', ru:'Контекст потока (MDC)'}](#${en:'context-tag-the-lines-of-one-request', ru:'контекст-метка-для-строк-одного-запроса'}) | ✅ | ✅ | ➖⁵ | ❌ | ❌ |
| [${en:'Key-value fields of one call', ru:'Поля ключ-значение одного вызова'}](#${en:'fields-named-arguments', ru:'поля-именованные-аргументы'}) | ✅ | ❌ | ❌ | ❌ | ❌ |
| [${en:'Capturing entries in tests', ru:'Захват записей в тестах'}](#${en:'memorylogger-and-compositelogger', ru:'memorylogger-и-compositelogger'}) | ✅ | ❌ | ❌ | ❌ | ❌ |
| [${en:'Placeholders checked against the arguments', ru:'Проверка плейсхолдеров по аргументам'}](#${en:'checking-placeholders-in-tests', ru:'проверка-плейсхолдеров-в-тестах'}) | ✅¹⁶ | ❌ | ➖¹⁷ | ❌ | ✅¹⁸ |
| [${en:'Files with rolling', ru:'Файлы с ротацией'}](#filelogger) | ➖¹⁹ | ✅ | ➖⁵ | ❌ | ❌ |
| ${en:'Asynchronous output', ru:'Асинхронный вывод'} | ❌ | ✅ | ➖⁵ | ❌ | ❌ |
| [${en:'Markers or tags', ru:'Маркеры или теги'}](#${en:'tags', ru:'теги'}) | ✅ | ✅ | ❌ | ➖²⁰ | ✅ |
| [${en:'Forwarding to SLF4J or another backend', ru:'Передача в SLF4J или другой бэкенд'}](#slf4jlogger) | ✅ | ➖²¹ | ✅ | ➖²² | ✅ |
| [${en:'A cheap disabled call, no stack walk', ru:'Дешёвый выключенный вызов без обхода стека'}](#${en:'thread-safety-and-performance', ru:'потокобезопасность-и-производительность'}) | ➖²³ | ➖²⁴ | ✅ | ✅ | ❌²⁵ |
| [${en:'Print debugging that names the expression', ru:'Отладочная печать с именем выражения'}](#${en:'print-debugging-logdbg-prints-the-expression-and-its-value', ru:'отладочная-печать-logdbg-печатает-выражение-и-его-значение'}) | ✅ | ❌ | ❌ | ❌ | ❌ |
| [${en:'Output for coding agents', ru:'Вывод для кодовых агентов'}](#${en:'agent-mode-nanolog-detects-coding-agents', ru:'агентский-режим-nanolog-распознаёт-кодовых-агентов'}) | ✅ | ❌ | ❌ | ❌ | ❌ |

¹ jcabi-log: the caller passes `this`, a class or a name as the source.<br><!--en-->
¹ jcabi-log: вызывающий код передаёт источник: `this`, класс или имя.<br><!--ru-->
² Timber: only `DebugTree` takes the tag from the calling class.<br><!--en-->
² Timber: тег из вызывающего класса берёт только `DebugTree`.<br><!--ru-->
³ Timber: a subclass of `DebugTree` can add the method and the line in `createStackElementTag`.<br><!--en-->
³ Timber: подкласс `DebugTree` может добавить метод и строку в `createStackElementTag`.<br><!--ru-->
⁴ tinylog: without `tinylog-impl`, or when ServiceLoader cannot find it (ProGuard, a fat jar), it prints `No logging framework implementation found` and logs nothing.<br><!--en-->
⁴ tinylog: без `tinylog-impl` или когда ServiceLoader его не находит (ProGuard, fat jar), печатается `No logging framework implementation found`, и ничего не логируется.<br><!--ru-->
⁵ jcabi-log writes nothing itself: this depends on the SLF4J backend under it.<br><!--en-->
⁵ jcabi-log сам ничего не пишет: это зависит от бэкенда SLF4J под ним.<br><!--ru-->
⁶ tinylog: the configuration is frozen once it is applied; `Configuration.set` then throws.<br><!--en-->
⁶ tinylog: конфигурация замораживается после применения; `Configuration.set` после этого бросает исключение.<br><!--ru-->
⁷ Timber: trees are planted and removed at any time, but there are no levels; a tree filters in `isLoggable`.<br><!--en-->
⁷ Timber: деревья можно сажать и убирать в любой момент, но уровней нет; дерево фильтрует само в `isLoggable`.<br><!--ru-->
⁸ tinylog: in a call with arguments, text in braces is a `DecimalFormat` or `ChoiceFormat` pattern, see the table below.<br><!--en-->
⁸ tinylog: в вызове с аргументами текст в фигурных скобках считается шаблоном `DecimalFormat` или `ChoiceFormat`, см. таблицу ниже.<br><!--ru-->
⁹ tinylog: the exception comes first, `Logger.error(e, ...)`; a trailing one is dropped.<br><!--en-->
⁹ tinylog: исключение идёт первым, `Logger.error(e, ...)`; исключение в конце теряется.<br><!--ru-->
¹⁰ jcabi-log: `error(this, "failed %s", id, e)` throws `IllegalArgumentException`; a stack trace goes into the text through `%[exception]s`.<br><!--en-->
¹⁰ jcabi-log: `error(this, "failed %s", id, e)` бросает `IllegalArgumentException`; трассировка попадает в текст через `%[exception]s`.<br><!--ru-->
¹¹ minlog: there are no arguments, only `error(message, e)`.<br><!--en-->
¹¹ minlog: аргументов нет, есть только `error(message, e)`.<br><!--ru-->
¹² Timber: the exception comes first; the bundled lint check `ThrowableNotAtBeginning` flags a trailing one.<br><!--en-->
¹² Timber: исключение идёт первым; встроенная lint-проверка `ThrowableNotAtBeginning` отмечает исключение в конце.<br><!--ru-->
¹³ minlog: the caller builds the message, so a throwing `toString()` throws before minlog is called, even at a disabled level.<br><!--en-->
¹³ minlog: сообщение собирает вызывающий код, поэтому падающий `toString()` бросает исключение ещё до вызова minlog, даже на выключенном уровне.<br><!--ru-->
¹⁴ tinylog: frames are dropped by package (`strip`, `keep`), with no limit on the depth.<br><!--en-->
¹⁴ tinylog: кадры отбрасываются по пакетам (`strip`, `keep`), без ограничения глубины.<br><!--ru-->
¹⁵ tinylog: the `json` writer, to a file.<br><!--en-->
¹⁵ tinylog: writer `json`, в файл.<br><!--ru-->
¹⁶ Nanolog: at run time with `nanolog.strict` and in tests, not at build time.<br><!--en-->
¹⁶ Nanolog: во время работы с `nanolog.strict` и в тестах, не при сборке.<br><!--ru-->
¹⁷ jcabi-log: always on; a mismatch throws out of the logging call.<br><!--en-->
¹⁷ jcabi-log: всегда включена; несовпадение бросает исключение из вызова логирования.<br><!--ru-->
¹⁸ Timber: the bundled Android Lint checks, at build time.<br><!--en-->
¹⁸ Timber: встроенные проверки Android Lint, при сборке.<br><!--ru-->
¹⁹ Nanolog: `FileLogger` rolls over by size only, with backups; no rolling by date, no compression, no asynchronous writing.<br><!--en-->
¹⁹ Nanolog: `FileLogger` переносит файл только по размеру, с резервными копиями; переноса по дате, сжатия и асинхронной записи нет.<br><!--ru-->
²⁰ minlog: a category string in each call.<br><!--en-->
²⁰ minlog: строка категории в каждом вызове.<br><!--ru-->
²¹ tinylog 2: to java.util.logging and JBoss Logging, and a writer to Logcat; not to SLF4J (`slf4j-tinylog` goes the other way).<br><!--en-->
²¹ tinylog 2: в java.util.logging и JBoss Logging, плюс writer в Logcat; в SLF4J нет (`slf4j-tinylog` работает в обратную сторону).<br><!--ru-->
²² minlog: a subclass of `Log.Logger`, set with `Log.setLogger`.<br><!--en-->
²² minlog: подкласс `Log.Logger`, заданный через `Log.setLogger`.<br><!--ru-->
²³ Nanolog: a level that is off for every class costs about 2 ns; a level enabled for another package walks the stack, about 3 µs on JDK 17 and 5 µs on Java 8, unless the class is passed.<br><!--en-->
²³ Nanolog: уровень, выключенный для всех классов, стоит около 2 нс; уровень, включённый для другого пакета, обходит стек, около 3 мкс на JDK 17 и 5 мкс на Java 8, если класс не передан.<br><!--ru-->
²⁴ tinylog: with levels per package, a disabled call between the global level and the most verbose package level looks up the caller first.<br><!--en-->
²⁴ tinylog: с уровнями по пакетам выключенный вызов между глобальным уровнем и самым подробным уровнем пакета сначала ищет вызывающий класс.<br><!--ru-->
²⁵ Timber: `DebugTree` takes the tag from a new stack trace before `isLoggable`.<!--en-->
²⁵ Timber: `DebugTree` берёт тег из нового стека ещё до `isLoggable`.<!--ru-->

### ${en:'Mainstream loggers', ru:'Распространённые логгеры'}

The loggers most Java code already uses, and Flogger, which limits the rate per call site.<!--en-->
Логгеры, которыми уже пользуется большая часть Java-кода, и Flogger, который ограничивает частоту на месте вызова.<!--ru-->

| ${en:'Feature', ru:'Возможность'} | **Nanolog** | [SLF4J][slf4j] 2.0.20 + [Logback][logback] 1.6.7²⁶ | [Log4j 2][log4j] 2.26.1 | [JUL][jul] / [System.Logger][system-logger] | [Flogger][flogger] 0.9 | [JBoss Logging][jboss-logging] 3.6.3 |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| [${en:'No logger field', ru:'Без поля логгера'}](#${en:'caller-detection', ru:'определение-вызывающего-класса'}) | ✅ | ❌²⁷ | ➖²⁸ | ➖²⁹ | ➖³⁰ | ❌²⁷ |
| [${en:'Source class found automatically', ru:'Класс-источник определяется сам'}](#${en:'caller-detection', ru:'определение-вызывающего-класса'}) | ✅ | ❌²⁷ | ✅ | ➖³¹ | ✅ | ❌²⁷ |
| [${en:'Call site in the output', ru:'Место вызова в выводе'}](#consolelogger) | ✅ | ✅ | ✅ | ➖³² | ➖³³ | ➖³⁴ |
| [${en:'Runtime dependencies', ru:'Зависимости во время работы'}](#${en:'installation', ru:'установка'}) | ${en:'one jar', ru:'один jar'} | `slf4j-api` + Logback | api + core | JDK | ${en:'flogger + a backend', ru:'flogger + бэкенд'} | ${en:'one jar³⁵', ru:'один jar³⁵'} |
| ${en:'Java baseline', ru:'Минимальная Java'} | Java 8 | Java 11³⁶ | Java 8 | Java 1.4 / 9 | Java 8 | Java 11 |
| [${en:'Levels without a configuration file', ru:'Уровни без файла конфигурации'}](#${en:'levels-and-options-without-code', ru:'уровни-и-настройки-без-кода'}) | ✅ | ❌²⁶ | ➖³⁷ | ❌ | ❌ | ❌ |
| [${en:'Levels changed at run time', ru:'Уровни меняются во время работы'}](#${en:'changing-levels-at-run-time', ru:'изменение-уровней-во-время-работы'}) | ✅ | ✅ | ✅ | ✅ | ✅ | ➖³⁸ |
| [${en:'Placeholders', ru:'Плейсхолдеры'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) | `{}` | `{}` | `{}` | `{0}` | `%s` | `%s`, `{0}` |
| [${en:'A trailing exception keeps its stack trace', ru:'Исключение после аргументов сохраняет трассировку'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) | ✅ | ✅ | ✅ | ❌ | ❌³⁹ | ❌⁴⁰ |
| [${en:'A throwing `toString()` does not break the call', ru:'Падающий `toString()` не ломает вызов'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) | ✅ | ✅ | ✅ | ✅ | ✅ | ➖⁴¹ |
| [${en:'Lazy messages', ru:'Ленивые сообщения'}](#${en:'api-at-a-glance', ru:'api-одним-взглядом'}) | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| [${en:'Rate limiting per call site', ru:'Ограничение частоты на месте вызова'}](#${en:'rate-limiting', ru:'ограничение-частоты'}) | ✅ | ➖⁴² | ➖⁴³ | ❌ | ✅ | ❌ |
| [${en:'Shorter stack traces', ru:'Короткие трассировки стека'}](#${en:'shorter-stack-traces', ru:'короткие-трассировки-стека'}) | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| [${en:'JSON lines built in', ru:'JSON lines из коробки'}](#jsonlogger) | ✅ | ✅ | ➖⁴⁴ | ❌ | ❌ | ➖⁴⁵ |
| [${en:'Context of a thread (MDC)', ru:'Контекст потока (MDC)'}](#${en:'context-tag-the-lines-of-one-request', ru:'контекст-метка-для-строк-одного-запроса'}) | ✅ | ✅ | ✅ | ❌ | ➖⁴⁶ | ➖⁴⁷ |
| [${en:'Key-value fields of one call', ru:'Поля ключ-значение одного вызова'}](#${en:'fields-named-arguments', ru:'поля-именованные-аргументы'}) | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| [${en:'Capturing entries in tests', ru:'Захват записей в тестах'}](#${en:'memorylogger-and-compositelogger', ru:'memorylogger-и-compositelogger'}) | ✅ | ✅ | ➖⁴⁸ | ❌ | ❌ | ❌ |
| [${en:'Placeholders checked against the arguments', ru:'Проверка плейсхолдеров по аргументам'}](#${en:'checking-placeholders-in-tests', ru:'проверка-плейсхолдеров-в-тестах'}) | ✅¹⁶ | ➖⁴⁹ | ➖⁵⁰ | ❌ | ✅ | ➖⁵¹ |
| [${en:'Files with rolling', ru:'Файлы с ротацией'}](#filelogger) | ➖¹⁹ | ✅ | ✅ | ➖⁵² | ➖⁵² | ➖⁴⁵ |
| ${en:'Asynchronous output', ru:'Асинхронный вывод'} | ❌ | ✅ | ✅ | ❌ | ❌ | ➖⁴⁵ |
| [${en:'Markers or tags', ru:'Маркеры или теги'}](#${en:'tags', ru:'теги'}) | ✅ | ✅ | ✅ | ❌ | ➖⁵³ | ❌ |
| [${en:'Forwarding to SLF4J or another backend', ru:'Передача в SLF4J или другой бэкенд'}](#slf4jlogger) | ✅ | ✅ | ✅ | ➖⁵⁴ | ✅ | ✅ |
| [${en:'A cheap disabled call, no stack walk', ru:'Дешёвый выключенный вызов без обхода стека'}](#${en:'thread-safety-and-performance', ru:'потокобезопасность-и-производительность'}) | ➖²³ | ✅ | ✅⁵⁵ | ✅ | ✅⁵⁵ | ✅ |
| [${en:'Print debugging that names the expression', ru:'Отладочная печать с именем выражения'}](#${en:'print-debugging-logdbg-prints-the-expression-and-its-value', ru:'отладочная-печать-logdbg-печатает-выражение-и-его-значение'}) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| [${en:'Output for coding agents', ru:'Вывод для кодовых агентов'}](#${en:'agent-mode-nanolog-detects-coding-agents', ru:'агентский-режим-nanolog-распознаёт-кодовых-агентов'}) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

²⁶ slf4j-simple instead of Logback runs on Java 8 and reads levels from system properties, but fixes them at start and has no call site, rolling, JSON or MDC.<br><!--en-->
²⁶ slf4j-simple вместо Logback работает на Java 8 и читает уровни из системных свойств, но фиксирует их при запуске, и в нём нет места вызова, ротации, JSON и MDC.<br><!--ru-->
²⁷ Lombok 1.18.48 `@Slf4j`, `@Log4j2`, `@Log`, `@Flogger` and `@JBossLog` write the field and the class for you, but the field stays, and each class needs the annotation.<br><!--en-->
²⁷ Lombok 1.18.48 `@Slf4j`, `@Log4j2`, `@Log`, `@Flogger` и `@JBossLog` пишут поле и класс за вас, но поле остаётся, и аннотация нужна в каждом классе.<br><!--ru-->
²⁸ Log4j 2: `LogManager.getLogger().info(...)` works inline, but walks the stack and looks up the logger at every call; the docs use a field.<br><!--en-->
²⁸ Log4j 2: `LogManager.getLogger().info(...)` работает прямо в вызове, но каждый раз обходит стек и ищет логгер; документация использует поле.<br><!--ru-->
²⁹ java.util.logging: `Logger.getGlobal()` works from any class, but it is one logger, without levels per class; `System.Logger` always needs a name.<br><!--en-->
²⁹ java.util.logging: `Logger.getGlobal()` работает из любого класса, но это один логгер без уровней по классам; `System.Logger` всегда требует имя.<br><!--ru-->
³⁰ Flogger: `FluentLogger.forEnclosingClass()` inline walks the stack and creates a logger at every call; the docs prescribe a field.<br><!--en-->
³⁰ Flogger: `FluentLogger.forEnclosingClass()` прямо в вызове каждый раз обходит стек и создаёт логгер; документация предписывает поле.<br><!--ru-->
³¹ java.util.logging: the logger name is passed; each record finds its source class and method.<br><!--en-->
³¹ java.util.logging: имя логгера передаётся явно; класс и метод источника каждая запись находит сама.<br><!--ru-->
³² java.util.logging: the class and the method, no line.<br><!--en-->
³² java.util.logging: класс и метод, без строки.<br><!--ru-->
³³ Flogger: the system backend prints the class and the method, not the line.<br><!--en-->
³³ Flogger: системный бэкенд печатает класс и метод, но не строку.<br><!--ru-->
³⁴ JBoss Logging: depends on the backend; the output through java.util.logging gives the class and the method.<br><!--en-->
³⁴ JBoss Logging: зависит от бэкенда; вывод через java.util.logging даёт класс и метод.<br><!--ru-->
³⁵ JBoss Logging: without a backend it writes through java.util.logging.<br><!--en-->
³⁵ JBoss Logging: без бэкенда пишет через java.util.logging.<br><!--ru-->
³⁶ Logback 1.6 needs Java 11; `slf4j-api` runs on Java 8, and Logback 1.3, the last line for Java 8, is no longer supported.<br><!--en-->
³⁶ Logback 1.6 требует Java 11; `slf4j-api` работает на Java 8, а Logback 1.3, последняя линия для Java 8, больше не поддерживается.<br><!--ru-->
³⁷ Log4j 2: `log4j2.level` (`LOG4J_LEVEL`) sets only the root level of the default configuration.<br><!--en-->
³⁷ Log4j 2: `log4j2.level` (`LOG4J_LEVEL`) задаёт только корневой уровень конфигурации по умолчанию.<br><!--ru-->
³⁸ JBoss Logging: through the API of the backend.<br><!--en-->
³⁸ JBoss Logging: через API бэкенда.<br><!--ru-->
³⁹ Flogger: `.withCause(e)`; a trailing exception is reported as an unused argument.<br><!--en-->
³⁹ Flogger: `.withCause(e)`; исключение в конце выводится как неиспользованный аргумент.<br><!--ru-->
⁴⁰ JBoss Logging: the exception comes first, `errorf(e, format, args)`.<br><!--en-->
⁴⁰ JBoss Logging: исключение идёт первым, `errorf(e, format, args)`.<br><!--ru-->
⁴¹ JBoss Logging: through java.util.logging the entry is dropped silently; with Logback the exception reaches the caller.<br><!--en-->
⁴¹ JBoss Logging: при выводе через java.util.logging запись молча теряется; с Logback исключение доходит до вызывающего кода.<br><!--ru-->
⁴² Logback: `DuplicateMessageFilter`, global, by the text of the message.<br><!--en-->
⁴² Logback: `DuplicateMessageFilter`, глобальный, по тексту сообщения.<br><!--ru-->
⁴³ Log4j 2: `BurstFilter`, global or per appender, by level.<br><!--en-->
⁴³ Log4j 2: `BurstFilter`, глобальный или на аппендер, по уровню.<br><!--ru-->
⁴⁴ Log4j 2: JSON Template Layout, a separate jar of the project.<br><!--en-->
⁴⁴ Log4j 2: JSON Template Layout, отдельный jar проекта.<br><!--ru-->
⁴⁵ JBoss Logging: with the jboss-logmanager backend; through java.util.logging there is no JSON and no asynchronous output, and files rotate by size only.<br><!--en-->
⁴⁵ JBoss Logging: с бэкендом jboss-logmanager; через java.util.logging нет JSON и асинхронного вывода, а файлы ротируются только по размеру.<br><!--ru-->
⁴⁶ Flogger: needs `flogger-grpc-context`; without it the context does nothing.<br><!--en-->
⁴⁶ Flogger: нужен `flogger-grpc-context`; без него контекст ничего не делает.<br><!--ru-->
⁴⁷ JBoss Logging: the MDC API exists, but the output through java.util.logging never prints it.<br><!--en-->
⁴⁷ JBoss Logging: API MDC есть, но вывод через java.util.logging его не печатает.<br><!--ru-->
⁴⁸ Log4j 2: `ListAppender` in the separate `log4j-core-test`.<br><!--en-->
⁴⁸ Log4j 2: `ListAppender` в отдельном `log4j-core-test`.<br><!--ru-->
⁴⁹ SLF4J: third-party checks at build time: findbugs-slf4j, Error Prone Support, an IntelliJ inspection.<br><!--en-->
⁴⁹ SLF4J: сторонние проверки при сборке: findbugs-slf4j, Error Prone Support, инспекция IntelliJ.<br><!--ru-->
⁵⁰ Log4j 2: a warning of the status logger, hidden by default, and an IntelliJ inspection.<br><!--en-->
⁵⁰ Log4j 2: предупреждение status logger, скрытое по умолчанию, и инспекция IntelliJ.<br><!--ru-->
⁵¹ JBoss Logging: for typed `@Message` loggers only, by `jboss-logging-processor` at build time.<br><!--en-->
⁵¹ JBoss Logging: только для типизированных логгеров `@Message`, через `jboss-logging-processor` при сборке.<br><!--ru-->
⁵² java.util.logging, and Flogger through it: `FileHandler` rotates by size and count, never by time.<br><!--en-->
⁵² java.util.logging и Flogger через него: `FileHandler` ротирует по размеру и числу файлов, но не по времени.<br><!--ru-->
⁵³ Flogger: tags in a scoped context, no markers.<br><!--en-->
⁵³ Flogger: теги в scoped-контексте, маркеров нет.<br><!--ru-->
⁵⁴ java.util.logging: the adapters come from SLF4J and Log4j (`jul-to-slf4j`, `log4j-jul`).<br><!--en-->
⁵⁴ java.util.logging: адаптеры есть у SLF4J и Log4j (`jul-to-slf4j`, `log4j-jul`).<br><!--ru-->
⁵⁵ Log4j 2 and Flogger: with a logger field; the calls without one of notes 28 and 30 walk the stack every time.<!--en-->
⁵⁵ Log4j 2 и Flogger: с полем логгера; вызовы без поля из сносок 28 и 30 каждый раз обходят стек.<!--ru-->

### ${en:'Next to tinylog 2.8.1', ru:'Рядом с tinylog 2.8.1'}

tinylog is the closest analogue. The same calls, run on Java 8 with the default configuration of both:<!--en-->
tinylog — ближайший аналог. Одни и те же вызовы, запущенные на Java 8 с конфигурацией по умолчанию у обоих:<!--ru-->

| ${en:'Call', ru:'Вызов'} | tinylog 2.8.1 | Nanolog |
|---|---|---|
| `error("failed {}", id, e)` | ${en:'the message only, the stack trace is dropped', ru:'только сообщение, трассировка стека теряется'} | ${en:'the message and the stack trace', ru:'сообщение и трассировка стека'} |
| `info("{\"id\": {}}", 42)` | `"id": {}42` | `{"id": 42}` |
| `info("{foo} and {}", "bar")` | ${en:'a warning, then `bar and {}`', ru:'предупреждение, затем `bar and {}`'} | `{foo} and bar` |
| ${en:'an argument whose `toString()` throws', ru:'аргумент, чей `toString()` бросает исключение'} | ${en:'the exception reaches the caller', ru:'исключение доходит до вызывающего кода'} | `[FAILED toString()]` |
| ${en:'changing the configuration after the first log call', ru:'изменение конфигурации после первого вызова лога'} | `UnsupportedOperationException` | ${en:'`LOG.init` and `LOG.setLevel` at any time', ru:'`LOG.init` и `LOG.setLevel` в любой момент'} |
| ${en:'packaging', ru:'поставка'} | ${en:'api and impl jars, found by ServiceLoader', ru:'jar с api и jar с impl, которые находит ServiceLoader'} | ${en:'one jar', ru:'один jar'} |
| ${en:'Java baseline of the next major version', ru:'Минимальная Java следующей мажорной версии'} | ${en:'tinylog 3, not released yet, drops Java 8', ru:'tinylog 3, ещё не выпущенный, отказывается от Java 8'} | Java 8 |

When ServiceLoader does not find `tinylog-impl`, nothing is logged: [#126](https://github.com/tinylog-org/tinylog/issues/126), [#135](https://github.com/tinylog-org/tinylog/issues/135), [#140](https://github.com/tinylog-org/tinylog/issues/140). Java 8 in tinylog 3: [#398](https://github.com/tinylog-org/tinylog/issues/398).<!--en-->
Когда ServiceLoader не находит `tinylog-impl`, ничего не логируется: [#126](https://github.com/tinylog-org/tinylog/issues/126), [#135](https://github.com/tinylog-org/tinylog/issues/135), [#140](https://github.com/tinylog-org/tinylog/issues/140). Java 8 в tinylog 3: [#398](https://github.com/tinylog-org/tinylog/issues/398).<!--ru-->

### ${en:'When another logger fits better', ru:'Когда лучше подойдёт другой логгер'}

- **Log files in production with rolling by date.** Rolling by time, compression and retention by age: Logback, Log4j 2, tinylog. `FileLogger` of Nanolog rolls over by size only.<!--en-->
- **Лог-файлы в продакшене с ротацией по дате.** Ротация по времени, сжатие и хранение по возрасту: Logback, Log4j 2, tinylog. `FileLogger` в Nanolog переносит файл только по размеру.<!--ru-->
- **Asynchronous output and high throughput.** The asynchronous appenders and loggers of Logback and Log4j 2, the writing thread of tinylog.<!--en-->
- **Асинхронный вывод и высокая нагрузка.** Асинхронные аппендеры и логгеры Logback и Log4j 2, пишущий поток tinylog.<!--ru-->
- **An ecosystem.** Appenders for syslog, databases, message queues and log services, markers and filters, the configuration of Spring Boot: SLF4J with Logback or Log4j 2. Nanolog can write through them with [`Slf4jLogger`](#slf4jlogger).<!--en-->
- **Экосистема.** Аппендеры для syslog, баз данных, очередей сообщений и сервисов логов, маркеры и фильтры, конфигурация Spring Boot: SLF4J с Logback или Log4j 2. Nanolog может писать через них с помощью [`Slf4jLogger`](#slf4jlogger).<!--ru-->
- **Android.** Timber writes to Logcat and checks its format strings with Android Lint. Nanolog is not tested on Android.<!--en-->
- **Android.** Timber пишет в Logcat и проверяет строки формата через Android Lint. Nanolog на Android не тестируется.<!--ru-->

[tinylog]: https://tinylog.org "tinylog"
[jcabi-log]: https://log.jcabi.com "jcabi-log"
[minlog]: https://github.com/EsotericSoftware/minlog "minlog"
[timber]: https://github.com/JakeWharton/timber "Timber"
[slf4j]: https://www.slf4j.org "SLF4J"
[logback]: https://logback.qos.ch "Logback"
[log4j]: https://logging.apache.org/log4j/2.x/ "Log4j 2"
[jul]: https://docs.oracle.com/javase/8/docs/technotes/guides/logging/overview.html "java.util.logging"
[system-logger]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.Logger.html "System.Logger"
[flogger]: https://google.github.io/flogger/ "Flogger"
[jboss-logging]: https://github.com/jboss-logging/jboss-logging "JBoss Logging"

## Changelog

${widget:if(cond='endsWith(${devVersion}, -SNAPSHOT)')}
### ${pom.version}

- ${en:'Run summary with `forkCount` above 1: a Surefire fork that runs several test classes prints one summary after the last of them, through the new `SummarySessionListener` (JUnit Platform 1.8+). Before, it printed after the first class and missed the rest of the fork.', ru:'Сводка запуска при `forkCount` больше 1: форк Surefire, который выполняет несколько тестовых классов, печатает одну сводку после последнего из них, через новый `SummarySessionListener` (JUnit Platform 1.8+). Раньше она печаталась после первого класса и пропускала остальные классы форка.'}
- ${en:'The run log keeps every entry after `LOG.init(logger)`: a logger the application installs, Spring with `Slf4jLogger` for one, or `MemoryLogger.install()` gets the run log added, and `LOG.getLogger()` returns a `CompositeLogger` of both. A decorator that passes each entry on to the logger `LOG.getLogger()` returned writes each entry to the file once. Before, the run log stopped at that call, while the run summary still called it the full log.', ru:'Журнал запуска получает все записи и после `LOG.init(logger)`: к логгеру, который устанавливает приложение, например Spring со `Slf4jLogger`, или `MemoryLogger.install()`, добавляется журнал запуска, а `LOG.getLogger()` возвращает `CompositeLogger` из обоих. Декоратор, который передаёт каждую запись дальше логгеру из `LOG.getLogger()`, пишет каждую запись в файл один раз. Раньше журнал запуска останавливался на этом вызове, хотя сводка запуска по-прежнему называла его полным логом.'}
- ${en:'"Logger replaced" no longer attributes a reflective `LOG.init` to `jdk.internal.reflect`: a call through reflection belongs to the code that made it, and the fallback caller detection passes over the frames of reflection as the stack walker does. A container named in `nanolog.caller.skip`, such as `org.springframework` for `MethodInvokingBean`, installs the logger for the application, and its calls are never reported.', ru:'"Logger replaced" больше не приписывает вызов `LOG.init` через рефлексию пакету `jdk.internal.reflect`: вызов через рефлексию принадлежит коду, который его сделал, а запасное определение вызывающего класса пропускает кадры рефлексии, как это делает обход стека. Контейнер, указанный в `nanolog.caller.skip`, например `org.springframework` для `MethodInvokingBean`, устанавливает логгер за приложение, и о его вызовах не сообщается.'}
- ${en:'Run summary: warnings and errors are grouped by level and call site, so a call site that builds its messages from data is one kind, shown with its first message and `(4 different messages)`, up to `(10+ different messages)`, instead of filling the list with one kind per value. A call with an explicit class, which has no call site, is still told apart by its message.', ru:'Сводка запуска: предупреждения и ошибки группируются по уровню и месту вызова, поэтому место вызова, которое собирает сообщения из данных, — один вид с первым сообщением и пометкой `(4 different messages)`, вплоть до `(10+ different messages)`, а не отдельный вид на каждое значение, вытесняющий остальные. Вызов с явным классом, у которого нет места вызова, по-прежнему различается по сообщению.'}
- ${en:'`nanolog.version`, the Maven property that holds the version of the dependency, no longer prints `Unknown setting` when it is set on the command line, where Surefire passes it to the JVMs of the tests as a system property. `NANOLOG_VERSION` is not reported either.', ru:'`nanolog.version`, свойство Maven с версией зависимости, больше не печатает `Unknown setting`, когда его задают в командной строке, откуда Surefire передаёт его в JVM тестов как системное свойство. О `NANOLOG_VERSION` тоже не сообщается.'}
- ${en:'Run log in tests run by Maven or Gradle: a JVM in which Surefire, Failsafe or Gradle runs tests names its own file, `runs/<time>-<pid>.log`, at the start and in the run summary, so with `forkCount` above 1 or `reuseForks=false` each fork points to the file with its own entries for the whole run. Before, every fork pointed to `latest.log`, which held only the fork that started last. Elsewhere a run names `latest.log` while it is the file of the run, and its own file once a later run has taken it. The summary is printed in one piece, so the summaries of two forks that end together no longer mix line by line.', ru:'Журнал запуска в тестах, которые запускает Maven или Gradle: JVM, в которой тесты запускает Surefire, Failsafe или Gradle, называет свой файл `runs/<время>-<pid>.log` и в начале, и в сводке запуска, поэтому при `forkCount` больше 1 или `reuseForks=false` каждый форк весь запуск указывает на файл со своими записями. Раньше все форки указывали на `latest.log`, где были записи только последнего запущенного форка. В остальных случаях запуск называет `latest.log`, пока это файл запуска, и свой файл, когда его занял более поздний запуск. Сводка печатается одним куском, поэтому сводки двух форков, закончивших одновременно, больше не перемешиваются построчно.'}
- ${en:'Run summary: an entry that a test captures with `MemoryLogger.capture()`, which keeps it from the logger, is no longer counted, so the summary describes the run log it names. Before, a warning that a test provoked and asserted on was listed as a kind and counted, while neither the console nor the run log had it. Entries that `NanologExtension` holds back are still counted, since the run log gets them when the test ends.', ru:'Сводка запуска: запись, которую тест перехватывает через `MemoryLogger.capture()`, не пропускающий её к логгеру, больше не считается, поэтому сводка описывает тот журнал запуска, который называет. Раньше предупреждение, которое тест вызвал и проверил, попадало в сводку отдельным видом и в счётчики, хотя его не было ни в консоли, ни в журнале запуска. Записи, которые придерживает `NanologExtension`, по-прежнему считаются: журнал запуска получает их, когда тест заканчивается.'}
- ${en:'`NanologExtension` also holds back the log of the class outside its tests: the `@BeforeAll` and `@AfterAll` methods and the creation of the test instances, where `SpringExtension` starts the application context. It is printed, framed by `--- log of class PaymentServiceTest ---`, when a test of the class or the class itself fails, and otherwise goes only to the run log. Before, a passing Spring test class still printed the start-up log of its context.', ru:'`NanologExtension` придерживает и лог класса вне его тестов: методы `@BeforeAll` и `@AfterAll` и создание экземпляров теста, где `SpringExtension` поднимает контекст приложения. Он печатается в рамке `--- log of class PaymentServiceTest ---`, если упал тест класса или сам класс, а иначе попадает только в журнал запуска. Раньше прошедший тестовый класс Spring всё равно печатал лог запуска своего контекста.'}
- ${en:'Run summary: every kind of warning and error goes to `runs/<time>-<pid>.summary.log` next to the run log, with `latest.summary.log`, one line each, ordered by level and call site so that `diff` compares two runs. When more than 10 kinds were logged, the line that counts the rest names the file: `... and 2 more kinds of WARN and ERROR; the full list is in ...`. Before, the kinds past the first ten were counted and written nowhere.', ru:'Сводка запуска: все виды предупреждений и ошибок записываются в `runs/<время>-<pid>.summary.log` рядом с журналом запуска, с `latest.summary.log`, по строке на вид, в порядке уровня и места вызова, чтобы `diff` сравнивал два запуска. Когда видов больше 10, строка, которая считает остальные, называет файл: `... and 2 more kinds of WARN and ERROR; the full list is in ...`. Раньше виды после первых десяти подсчитывались, но нигде не записывались.'}
- ${en:'Run summary: a message of several lines shows its first line that is not blank, followed by `...`, as a message cut at 200 characters. Before, every line break was printed as `\u000a`, six characters each, and the details filled the line of the kind.', ru:'Сводка запуска: у сообщения из нескольких строк показывается первая непустая строка и `...`, как у сообщения, обрезанного на 200 символах. Раньше каждый перевод строки печатался как `\u000a`, по шесть символов, и подробности заполняли строку вида.'}
- ${en:'`Slf4jLogger`: the line of `LOG.timed(...)` and of `LOG.unit(name, body)` has the call site of that call in the `%file:%line` of Logback and the `%l` of Log4j 2. Before, the backend named a frame of Nanolog, `Timers.run` or `LOG.unit`. A timer or a unit closed by a try-with-resources block still shows the line where the block ends, since SLF4J has no way to pass a call site that is no longer on the stack; `ConsoleLogger`, `JsonLogger` and the run log show the `LOG.time` or `LOG.unit` call.', ru:'`Slf4jLogger`: у строки `LOG.timed(...)` и `LOG.unit(name, body)` в `%file:%line` Logback и в `%l` Log4j 2 стоит место этого вызова. Раньше бэкенд называл кадр самого Nanolog, `Timers.run` или `LOG.unit`. Таймер или единица, которые закрывает блок try-with-resources, по-прежнему показывают строку, где блок заканчивается: через SLF4J нельзя передать место вызова, которого уже нет на стеке; `ConsoleLogger`, `JsonLogger` и журнал запуска показывают вызов `LOG.time` или `LOG.unit`.'}
- ${en:'The run log no longer changes the levels of a logger the application installs: `LOG.isXxxEnabled` and the lazy arguments of a call answer for that logger, `Slf4jLogger` for one, also in agent mode, and the run log gets the entries it takes. Before, the run log, which takes every level, turned every level on after `LOG.init(logger)`, so code that checked a level behaved differently when a coding agent ran it. The short console of agent mode still leaves the rest of its levels to the run log.', ru:'Журнал запуска больше не меняет уровни логгера, который устанавливает приложение: `LOG.isXxxEnabled` и ленивые аргументы вызова отвечают за этот логгер, например `Slf4jLogger`, и в агентском режиме, а журнал запуска получает записи, которые этот логгер берёт. Раньше журнал запуска, который берёт все уровни, после `LOG.init(logger)` включал все уровни, и код, проверяющий уровень, вёл себя иначе, когда его запускал кодовый агент. Короткая консоль агентского режима по-прежнему оставляет журналу запуска остальные свои уровни.'}
- ${en:'`LOG.unit(level, name)`, with `LOG.unit(level, name, body)`, writes the line of a failed or interrupted unit at another level than `ERROR`, such as `WARN` for a job that is retried, where every `ERROR` would raise an alert. `ok` stays `INFO`; the outcome, the counts and the `error.*` fields do not change.', ru:'`LOG.unit(level, name)` и `LOG.unit(level, name, body)` пишут строку неудачной или прерванной единицы на другом уровне, чем `ERROR`, например `WARN` для задания, которое повторяется, где каждый `ERROR` поднимал бы тревогу. `ok` остаётся `INFO`; исход, счётчики и поля `error.*` не меняются.'}
${widget:endIf}

### 1.4

- ${en:'Agent mode: when the environment names a coding agent (`AI_AGENT`, `AGENT`, `CLAUDECODE`, `GEMINI_CLI`, `CODEX_SANDBOX`, `CODEX_CI`, `CURSOR_AGENT`, `VSCODE_AGENT`, `CLINE_ACTIVE`), the console of the default logger shows `WARN` and above, one line per entry, without dates or colours and with short stack traces, `LOG.dbg` prints whatever the levels, the run log is on, and one `[NANOLOG]` line says so and names the file. `nanolog.agent=auto|on|off` decides; an explicitly set level or display setting wins.', ru:'Агентский режим: когда окружение называет кодового агента (`AI_AGENT`, `AGENT`, `CLAUDECODE`, `GEMINI_CLI`, `CODEX_SANDBOX`, `CODEX_CI`, `CURSOR_AGENT`, `VSCODE_AGENT`, `CLINE_ACTIVE`), консоль логгера по умолчанию показывает `WARN` и выше, по строке на запись, без дат и цветов и с короткими трассировками стека, `LOG.dbg` печатает при любых уровнях, журнал запуска включён, а одна строка `[NANOLOG]` сообщает об этом и называет файл. Решает `nanolog.agent=auto|on|off`; явно заданные уровень или настройка вывода главнее.'}
- ${en:'Documentation: what agent mode saves, measured in tokens. Four runs of a small project in `src/tokens`, with the script that repeats them; a failing test run printed 1,022 tokens instead of 13,577 (o200k_base). See', ru:'Документация: что экономит агентский режим, в токенах. Четыре запуска небольшого проекта в `src/tokens` и скрипт, который их повторяет; упавший тестовый запуск напечатал 1 022 токена вместо 13 577 (o200k_base). См.'} [${en:'Fewer tokens', ru:'Меньше токенов'}](#${en:'fewer-tokens-logs-for-ai-coding-agents', ru:'меньше-токенов-логи-для-ии-агентов'}).
- ${en:'Run summary, `nanolog.summary=on|off|errors`, on by default in agent mode: the run ends with the counts by level, each kind of warning and error with its call site and count, the trace of the first error, the `LOG.dbg` calls that ran and the path of the run log. The summary is printed last so that it survives when an agent keeps only the tail of the output. In test runs `SummaryListener`, which the JUnit Platform loads by itself, prints it and the list of `LOG.dbg` calls when the tests have run, because Surefire drops what a shutdown hook of its forked JVM prints.', ru:'Сводка запуска, `nanolog.summary=on|off|errors`, по умолчанию включена в агентском режиме: запуск заканчивается числом записей по уровням, каждым видом предупреждений и ошибок с местом вызова и числом повторений, трассировкой первой ошибки, вызовами `LOG.dbg`, которые выполнились, и путём к журналу запуска; она печатается последней, чтобы сохраниться, когда агент оставляет только хвост вывода. В тестовых запусках её, как и список вызовов `LOG.dbg`, печатает после выполнения тестов `SummaryListener`, который платформа JUnit загружает сама, потому что Surefire отбрасывает то, что печатает shutdown hook его отдельной JVM.'}
- ${en:'Run log for agents: with `nanolog.run=on` every entry of the run also goes to `target/nanolog/latest.log` (Gradle: `build/nanolog`, otherwise `.nanolog`), warnings and errors to `latest.errors.log` one line each, and the absolute path is printed when `LOG` initialises. Each run writes `runs/<time>-<pid>.log`, `latest.log` is a hard link to it, the last 10 runs are kept. `nanolog.run.dir`, `nanolog.run.format=json`, `nanolog.run.keep`, `nanolog.run.maxSize`.', ru:'Журнал запуска для агентов: с `nanolog.run=on` каждая запись запуска попадает ещё и в `target/nanolog/latest.log` (Gradle: `build/nanolog`, иначе `.nanolog`), предупреждения и ошибки — в `latest.errors.log`, по строке на запись, а абсолютный путь печатается при инициализации `LOG`. Каждый запуск пишет `runs/<время>-<pid>.log`, `latest.log` — жёсткая ссылка на него, хранятся последние 10 запусков. `nanolog.run.dir`, `nanolog.run.format=json`, `nanolog.run.keep`, `nanolog.run.maxSize`.'}
- ${en:'`LOG.dbg(expression)` prints the expression as written in the source, its value and type, and the call site, and returns the value; `LOG.dbg(a, b, ...)` prints several, `LOG.dbg()` that the line was reached. Without the source only the values are printed. `ConsoleLogger` marks the line `DBG`, `JsonLogger` adds `"dbg":true`, `LogEntry.isDbg()` tells the entry apart; `nanolog.sources` names the source directories.', ru:'`LOG.dbg(expression)` печатает выражение так, как оно записано в исходнике, его значение, тип и место вызова и возвращает значение; `LOG.dbg(a, b, ...)` печатает несколько, `LOG.dbg()` — что строка достигнута. Без исходников печатаются только значения. `ConsoleLogger` помечает строку `DBG`, `JsonLogger` добавляет `"dbg":true`, `LogEntry.isDbg()` отличает запись; `nanolog.sources` задаёт каталоги исходников.'}
- ${en:'Objects whose class has no `toString()` of its own are printed field by field instead of `Invoice@2f92e0f4`: always in `LOG.dbg`, and in `{}` arguments with `nanolog.dump=auto`. Classes of the JDK are never reflected into, so nothing needs `--add-opens` on JDK 17+; cycles, depth, items and string length are limited, and every cut says what it left out; fields named like secrets print `***`; records show their components, proxies only their class name.', ru:'Объекты, у класса которых нет собственного `toString()`, печатаются поле за полем, а не как `Invoice@2f92e0f4`: всегда в `LOG.dbg`, а в аргументах `{}` — с `nanolog.dump=auto`. В классы JDK рефлексия не заглядывает, поэтому на JDK 17+ не нужен `--add-opens`; циклы, глубина, число элементов и длина строк ограничены, и каждое ограничение сообщает, что отброшено; поля с именами секретов печатаются как `***`; записи (record) показывают свои компоненты, прокси — только имя класса.'}
- ${en:'Leftover `LOG.dbg` calls: when the JVM exits, one `[NANOLOG]` line lists the call sites that printed; `nanolog.dbg=off` silences the calls, `nanolog.dbg=fail` reports each one as a diagnostic that fails `MemoryLogger.assertNoDiagnostics()`; `NanologChecks.assertNoDbgCalls("src/main/java")` fails a test while the sources call `LOG.dbg`, with the file, line and code of each call. `MemoryLogger.assertNoDiagnostics()` now says `Nanolog reported` instead of `strict mode reported`.', ru:'Забытые вызовы `LOG.dbg`: при завершении JVM одна строка `[NANOLOG]` перечисляет места вызова, которые печатали; `nanolog.dbg=off` заглушает вызовы, `nanolog.dbg=fail` превращает каждый вызов в диагностику, на которой падает `MemoryLogger.assertNoDiagnostics()`; `NanologChecks.assertNoDbgCalls("src/main/java")` роняет тест, пока в исходниках есть `LOG.dbg`, и называет файл, строку и код каждого вызова. `MemoryLogger.assertNoDiagnostics()` теперь пишет `Nanolog reported` вместо `strict mode reported`.'}
- ${en:'Wide events: `LOG.unit("checkout")` opens a unit of work that writes one canonical log line per request or job when it closes, `checkout ok 182 ms orderId=A-17 warn=1`: the outcome (`ok`, `error`, `interrupted`), the duration, the fields of `put(...)`, the counts of warnings and errors logged inside it and, for a failed unit, `error.type`, `error.message` and an `error.key` that stays the same between runs. Units nest (`parent=`), cross executors with `LOG.wrap` and are written as `interrupted` when the run ends while they are open; `nanolog.unit.sample` / `nanolog.unit.slowMs` sample the fast successful ones; `run(...)` and `LOG.unit(name, body)` record a thrown exception.', ru:'Wide events: `LOG.unit("checkout")` открывает единицу работы, которая при закрытии пишет одну каноническую строку лога на запрос или задание, `checkout ok 182 ms orderId=A-17 warn=1`: исход (`ok`, `error`, `interrupted`), длительность, поля из `put(...)`, число предупреждений и ошибок внутри неё, а для неудачной единицы — `error.type`, `error.message` и `error.key`, который не меняется между запусками. Единицы вкладываются (`parent=`), переходят в пулы потоков через `LOG.wrap`, пишутся как `interrupted`, если запуск закончился при открытой единице, а `nanolog.unit.sample` / `nanolog.unit.slowMs` прореживают быстрые успешные; `run(...)` и `LOG.unit(name, body)` записывают брошенное исключение.'}
- ${en:'Settings that Nanolog does not know are reported when `LOG` initialises instead of being ignored: a mistyped `nanolog.*` system property, `NANOLOG_*` environment variable or key of `nanolog.properties` prints `[NANOLOG] Unknown setting "nanolog.levle" (system property); did you mean "nanolog.level"?`, a key of the file without the `nanolog.` prefix is reported as ignored, and `NANOLOG_LEVEL_COM_ACME` points to `NANOLOG_LEVELS`. Keys of a custom logger under a namespace of its own are left alone.', ru:'Настройки, которых Nanolog не знает, при инициализации `LOG` не игнорируются, а вызывают сообщение: системное свойство `nanolog.*`, переменная окружения `NANOLOG_*` или ключ `nanolog.properties` с опечаткой печатают `[NANOLOG] Unknown setting "nanolog.levle" (system property); did you mean "nanolog.level"?`, о ключе файла без префикса `nanolog.` сообщается как о проигнорированном, а `NANOLOG_LEVEL_COM_ACME` отсылает к `NANOLOG_LEVELS`. Ключи собственного логгера в своём пространстве имён не трогаются.'}
- ${en:'`NanologExtension`, a JUnit 5 extension: the log of a test is held back and printed only when the test fails, framed by `--- log of PaymentServiceTest.chargeIsRetried ---`; a passing or aborted test writes it only to the run log. Parallel tests keep their logs apart, and `nanolog.junit.output=failed|all|none` decides what is printed.', ru:'`NanologExtension`, расширение JUnit 5: лог теста придерживается и печатается, только если тест упал, в рамке `--- log of PaymentServiceTest.chargeIsRetried ---`; прошедший или прерванный тест пишет его только в журнал запуска. Параллельные тесты не смешивают логи, а `nanolog.junit.output=failed|all|none` решает, что печатать.'}
- ${en:'`nanolog.console.multiline` (`setMultiline`) for `ConsoleLogger`: `indent` starts every line of an entry after the first with a tab, so a message cannot forge an entry; `escape` writes line breaks as `\r` and `\n`, so an entry and its stack trace are one physical line; both write other control characters, terminal escape sequences among them, as `\u001b`. `keep`, the default, changes nothing; agent mode uses `escape`.', ru:'`nanolog.console.multiline` (`setMultiline`) для `ConsoleLogger`: `indent` начинает каждую строку записи после первой с табуляции, поэтому сообщение не может подделать запись; `escape` пишет переводы строк как `\r` и `\n`, поэтому запись вместе с трассировкой занимает одну физическую строку; оба режима пишут остальные управляющие символы, в том числе escape-последовательности терминала, как `\u001b`. `keep`, значение по умолчанию, ничего не меняет; агентский режим использует `escape`.'}
- ${en:'Coloured level words on a terminal, `nanolog.console.color=auto|always|never` (`setColor`): red `ERROR`, yellow `WARN`, green `INFO`, dim `DEBUG` and `TRACE`, magenta `DBG`. `auto`, the default, colours only when standard output is a terminal, honours `NO_COLOR`, `FORCE_COLOR` and `TERM=dumb`, and stays off on Windows and in agent mode; pipes, files, the run log and `JsonLogger` never get escape codes.', ru:'Цветные имена уровней в терминале, `nanolog.console.color=auto|always|never` (`setColor`): красный `ERROR`, жёлтый `WARN`, зелёный `INFO`, приглушённые `DEBUG` и `TRACE`, пурпурный `DBG`. `auto`, значение по умолчанию, раскрашивает, только когда стандартный вывод — терминал, учитывает `NO_COLOR`, `FORCE_COLOR` и `TERM=dumb` и выключен в Windows и в агентском режиме; в конвейеры, файлы, журнал запуска и `JsonLogger` escape-последовательности не попадают никогда.'}
- ${en:'`java.util.logging` both ways: `JulLogger` (`nanolog.logger=com.nanolaba.logging.JulLogger`) writes the entries to JUL, to the logger of the source class with the call site, and asks JUL for the levels; `NanologHandler.install()` or `handlers=com.nanolaba.logging.NanologHandler` turns the records of JUL, and so of `System.Logger`, into entries of the code that called it. A loop between the two is reported and cut.', ru:'`java.util.logging` в обе стороны: `JulLogger` (`nanolog.logger=com.nanolaba.logging.JulLogger`) пишет записи в JUL, в логгер класса-источника с местом вызова, и спрашивает у JUL уровни; `NanologHandler.install()` или `handlers=com.nanolaba.logging.NanologHandler` превращает записи JUL, а значит и `System.Logger`, в записи кода, который его вызвал. Петля между ними обнаруживается и разрывается.'}
- ${en:'GraalVM native image and Android: the jar carries the metadata of a native image (`META-INF/native-image`: the constructors of the loggers, the stack walker, `nanolog.properties`) and rules for R8 and ProGuard (`META-INF/proguard/nanolog.pro`), so neither needs setup; CI builds and runs a native image on GraalVM 21 and 25.', ru:'GraalVM native image и Android: jar несёт метаданные для native image (`META-INF/native-image`: конструкторы логгеров, обход стека, `nanolog.properties`) и правила для R8 и ProGuard (`META-INF/proguard/nanolog.pro`), так что ни то, ни другое не требует настройки; CI собирает и запускает native image на GraalVM 21 и 25.'}
- ${en:'`FileLogger`: `nanolog.logger=com.nanolaba.logging.FileLogger` with `nanolog.file=logs/app.log` appends the entries to a file, in the text format of `ConsoleLogger` or the JSON lines of `JsonLogger`. Each entry is written through at once, or with `nanolog.file.flush=buffer` kept until `LOG.flush()`; the file rolls over at `nanolog.file.maxSize`, with `nanolog.file.backups`. `LOG.flush()` and `ILogger.flush()` are new.', ru:'`FileLogger`: `nanolog.logger=com.nanolaba.logging.FileLogger` с `nanolog.file=logs/app.log` дописывает записи в файл в текстовом формате `ConsoleLogger` или строками JSON `JsonLogger`: пишет каждую запись сразу или с `nanolog.file.flush=buffer` держит до `LOG.flush()`, переносит файл по `nanolog.file.maxSize` с `nanolog.file.backups`. Добавлены `LOG.flush()` и `ILogger.flush()`.'}
- ${en:'A real module descriptor: on Java 9+ the jar is the module `com.nanolaba.nanolog`, from `META-INF/versions/9/module-info.class` of a multi-release jar, so `jlink` accepts it; the module needs only `java.base`.', ru:'Настоящий дескриптор модуля: на Java 9+ jar — это модуль `com.nanolaba.nanolog` из `META-INF/versions/9/module-info.class` многорелизного jar, поэтому `jlink` его принимает; модулю нужен только `java.base`.'}
- ${en:'`LOG.captureSystemStreams()` and `nanolog.captureSystemStreams=true` turn the lines printed to `System.out` and `System.err` into `INFO` and `ERROR` entries with the source and call site of the code that printed, `e.printStackTrace()` included, without a loop through the console.', ru:'`LOG.captureSystemStreams()` и `nanolog.captureSystemStreams=true` превращают строки, напечатанные в `System.out` и `System.err`, в записи `INFO` и `ERROR` с источником и местом вызова того кода, который печатал, в том числе `e.printStackTrace()`, без петли через консоль.'}
- ${en:'Calls from Kotlin are checked: CI compiles the calls of `LOG` in every form from Kotlin, so an overload that is ambiguous for Kotlin, as in tinylog, fails the build.', ru:'Вызовы из Kotlin проверяются: CI компилирует из Kotlin вызовы `LOG` всех форм, поэтому перегрузка, неоднозначная для Kotlin, как в tinylog, роняет сборку.'}
- ${en:'`Slf4jLogger` is tested against SLF4J 1.7 as well as 2.x: CI runs the tests once more with slf4j-api and slf4j-simple 1.7.36.', ru:'`Slf4jLogger` проверяется не только на SLF4J 2.x, но и на 1.7: CI ещё раз прогоняет тесты со slf4j-api и slf4j-simple 1.7.36.'}
- ${en:'A library that replaces the logger of the application is reported: when code of another organization (the first two segments of the package) replaces a logger installed with `LOG.init(logger)`, a `[NANOLOG]` line names both call sites. `LOG.lock()` and `nanolog.logger.lock=true` keep the installed logger and turn later `LOG.init` calls into no-ops with such a line; `MemoryLogger.install()` still works under the lock.', ru:'О библиотеке, которая заменяет логгер приложения, сообщается: когда код другой организации (первые два сегмента пакета) заменяет логгер, установленный через `LOG.init(logger)`, строка `[NANOLOG]` называет оба места вызова. `LOG.lock()` и `nanolog.logger.lock=true` сохраняют установленный логгер и превращают последующие вызовы `LOG.init` в пустые с такой же строкой; `MemoryLogger.install()` работает и при блокировке.'}
- ${en:'Documentation: a `nanolog.properties` in `src/test/resources` replaces the one in `src/main/resources` in tests, as a whole file, not merged.', ru:'Документация: `nanolog.properties` из `src/test/resources` заменяет в тестах файл из `src/main/resources` целиком, а не дополняет его.'}
- ${en:'Several `nanolog.properties` on the classpath, at least one of them in a jar, are reported with one `[NANOLOG]` line that lists them and names the one that was read: a library that ships its own file no longer wins over the file of the application without a word. Two directories, such as the main and the test resources, stay quiet.', ru:'Несколько `nanolog.properties` в classpath, хотя бы один из которых лежит в jar, отмечаются одной строкой `[NANOLOG]`, которая перечисляет их и называет прочитанный: библиотека, принёсшая свой файл, больше не перебивает файл приложения молча. Два каталога, например основные и тестовые ресурсы, сообщений не вызывают.'}
- ${en:'Tags: `LOG.tag("audit").warn(...)` marks the entries of a concern rather than of a class, and `nanolog.tags=audit=WARN,sql=DEBUG` sets levels per tag, which take precedence over the levels of the class. `ConsoleLogger` writes `[audit]`, `JsonLogger` `"tags":["audit"]`, `Slf4jLogger` passes an SLF4J `Marker`; `LogEntry.getTags()` and `ILogger.isEnabled(level, sourceClass, tag)` are new.', ru:'Теги: `LOG.tag("audit").warn(...)` помечает записи, относящиеся к теме, а не к классу, а `nanolog.tags=audit=WARN,sql=DEBUG` задаёт уровни по тегам, которые главнее уровней класса. `ConsoleLogger` пишет `[audit]`, `JsonLogger` — `"tags":["audit"]`, `Slf4jLogger` передаёт `Marker` SLF4J; добавлены `LogEntry.getTags()` и `ILogger.isEnabled(level, sourceClass, tag)`.'}
- ${en:'Lazy arguments: `LOG.debug("Stats: {}", LOG.lazy(() -> summarize(stats)))` computes one argument only if the level is enabled, once, and keeps the template for strict mode, `JsonLogger` and `assertTemplateLogged`.', ru:'Ленивые аргументы: `LOG.debug("Stats: {}", LOG.lazy(() -> summarize(stats)))` вычисляет один аргумент только при включённом уровне, один раз, и сохраняет шаблон для строгого режима, `JsonLogger` и `assertTemplateLogged`.'}
- ${en:'`nanolog.console.sourceLength` (`setSourceLength`) abbreviates a fully qualified source name like `%logger{n}` of Logback: `com.acme.billing.payments.PaymentService` becomes `c.a.b.p.PaymentService` at 20 characters.', ru:'`nanolog.console.sourceLength` (`setSourceLength`) сокращает полное имя класса-источника, как `%logger{n}` в Logback: `com.acme.billing.payments.PaymentService` при 20 символах становится `c.a.b.p.PaymentService`.'}
- ${en:'`nanolog.console.timeZone` (`setTimeZone`) sets the time zone of the `ConsoleLogger` timestamps, such as `UTC` or `Europe/Berlin`; an unknown ID is reported instead of being taken for GMT. `nanolog.console.dateFormat=iso` stands for the ISO 8601 pattern with milliseconds and the offset of the zone.', ru:'`nanolog.console.timeZone` (`setTimeZone`) задаёт часовой пояс меток времени `ConsoleLogger`, например `UTC` или `Europe/Berlin`; о неизвестном идентификаторе сообщается, а не подставляется GMT. `nanolog.console.dateFormat=iso` означает шаблон ISO 8601 с миллисекундами и смещением пояса.'}
- ${en:'Context providers: `LOG.addContextProvider(() -> ids)` and `nanolog.context.provider=com.acme.TraceIdProvider` add the keys of a context of their own, such as the trace ID of OpenTelemetry, to every entry, after the pairs of `LOG.context`, which win. A provider is asked only for an enabled entry; one that throws is reported once and the entry is logged without its keys.', ru:'Провайдеры контекста: `LOG.addContextProvider(() -> ids)` и `nanolog.context.provider=com.acme.TraceIdProvider` добавляют к каждой записи ключи из собственного контекста, например trace ID из OpenTelemetry, после пар `LOG.context`, которые важнее. Провайдер вызывается только для включённой записи; о провайдере, который бросает исключение, сообщается один раз, а запись логируется без его ключей.'}
- ${en:'Logging helpers: `nanolog.caller.skip=com.acme.Audit,com.acme.logging` and `LOG.skipCaller(Helper.class)` skip the frames of classes and packages that log for others, so an entry logged through `Audit.record(...)` gets the source class, call site and rate limit of the code that called the helper, and `Slf4jLogger` passes the helper to Logback and Log4j 2 as the caller boundary.', ru:'Обёртки над `LOG`: `nanolog.caller.skip=com.acme.Audit,com.acme.logging` и `LOG.skipCaller(Helper.class)` пропускают кадры классов и пакетов, которые пишут в лог за других, поэтому запись, сделанная через `Audit.record(...)`, получает класс-источник, место вызова и ограничение частоты того кода, который вызвал обёртку, а `Slf4jLogger` передаёт обёртку в Logback и Log4j 2 как границу вызова.'}
- ${en:'Strict mode reports two more mistakes, with the call site: an argument that repeats the message of the exception logged with it, `LOG.error("Failed: {}", e.getMessage(), e)`, and a call site seen with two different templates, that is, a template built from data, such as `LOG.info("User " + id + " from {}", ip)`. Like the placeholder check, both reach `ILogger.onDiagnostic` and fail `MemoryLogger.assertNoDiagnostics()`; calls without arguments are not checked.', ru:'Строгий режим сообщает ещё о двух ошибках, с местом вызова: об аргументе, который повторяет сообщение залогированного вместе с ним исключения, `LOG.error("Failed: {}", e.getMessage(), e)`, и о месте вызова с двумя разными шаблонами, то есть о шаблоне, собранном из данных, например `LOG.info("User " + id + " from {}", ip)`. Как и проверка плейсхолдеров, обе доходят до `ILogger.onDiagnostic` и роняют `MemoryLogger.assertNoDiagnostics()`; вызовы без аргументов не проверяются.'}
- ${en:'README: *Related projects*, a comparison with tinylog, jcabi-log, minlog, Timber, SLF4J with Logback, Log4j 2, java.util.logging, Flogger and JBoss Logging, with the calls that behave differently in tinylog.', ru:'README: *Похожие проекты*, сравнение с tinylog, jcabi-log, minlog, Timber, SLF4J с Logback, Log4j 2, java.util.logging, Flogger и JBoss Logging, вместе с вызовами, которые в tinylog ведут себя иначе.'}
- ${en:'Faster caller detection on Java 8: `Reflection.getCallerClass` gives the class of each frame and only the frames up to the caller are built, so a call without a class at a level enabled for some class costs about 5 µs instead of 20 near the top of the stack and 19 µs instead of 270 at 500 frames; the stack trace of a `Throwable` stays the fallback.', ru:'Определение вызывающего класса на Java 8 быстрее: `Reflection.getCallerClass` даёт класс каждого кадра, и строятся только кадры до вызывающего, поэтому вызов без класса для уровня, включённого хотя бы для одного класса, стоит около 5 мкс вместо 20 у вершины стека и 19 мкс вместо 270 на глубине 500 кадров; стек `Throwable` остаётся запасным путём.'}
- ${en:'Secrets: `LOG.secret(token)` is written as `***` by every logger and keeps no value; fields and context values whose key names a secret, `password`, `token`, `apikey` and the others of `nanolog.redact`, are masked when the entry is created; a shown `LOG.content` masks keys of model providers, AWS keys, bearer tokens and JSON web tokens.', ru:'Секреты: `LOG.secret(token)` любой логгер пишет как `***`, и значение не хранится; поля и значения контекста, чей ключ называет секрет, `password`, `token`, `apikey` и остальные из `nanolog.redact`, маскируются при создании записи; показанное `LOG.content` маскирует ключи поставщиков моделей, ключи AWS, bearer-токены и JSON web tokens.'}
- ${en:'`LOG.time("Load users")` and `LOG.timed("Count users", () -> ...)` time a block: `Load users took 123 ms` with the fields `timer` and `duration_ms`; a level that is off reads no clock, and the lambda form reports and rethrows an exception.', ru:'`LOG.time("Load users")` и `LOG.timed("Count users", () -> ...)` замеряют блок: `Load users took 123 ms` с полями `timer` и `duration_ms`; выключенный уровень не читает часы, а лямбда-форма сообщает об исключении и пробрасывает его.'}
- ${en:'`nanolog.console.repeats=collapse` (`setRepeats`), the default in agent mode: consecutive repeats of an entry become one line with a count, `(repeated 41 more times in 20.3 s)`, written when another entry comes, the logger is replaced or the run ends, and every ten seconds while the repeats go on; the run log, `JsonLogger` and `MemoryLogger` keep every entry.', ru:'`nanolog.console.repeats=collapse` (`setRepeats`), по умолчанию в агентском режиме: подряд идущие повторы записи становятся одной строкой со счётчиком, `(repeated 41 more times in 20.3 s)`, которая пишется, когда приходит другая запись, логгер заменяется или запуск заканчивается, и каждые десять секунд, пока повторы продолжаются; журнал запуска, `JsonLogger` и `MemoryLogger` сохраняют каждую запись.'}
- ${en:'`nanolog.diagnostics=once|all|off`: each distinct `[NANOLOG]` diagnostic is printed once per JVM by default, configuration mistakes and the findings of strict mode at each call site included, so a `ConsoleLogger` created again does not repeat them; `all` prints every one, `off` none. A diagnostic about an invalid value says where it was set: a system property, an environment variable or the file. The run summary and the other notices are not affected.', ru:'`nanolog.diagnostics=once|all|off`: по умолчанию каждая отдельная диагностика `[NANOLOG]` печатается один раз на JVM, включая ошибки конфигурации и находки строгого режима в каждом месте вызова, поэтому заново созданный `ConsoleLogger` их не повторяет; `all` печатает все, `off` — ни одной. Диагностика о неверном значении говорит, где оно задано: системное свойство, переменная окружения или файл. Сводку запуска и другие сообщения это не затрагивает.'}
- ${en:'`nanolog.console.showSourceLine` (`setShowSourceLine`): the source line of the call under each entry, `    > LOG.warn("Charge of {} failed", amount);`, and under the first three frames of a stack trace outside the JDK. The line is found as `LOG.dbg` finds its source and left out when the source is missing or stale; `JsonLogger` adds `"source"` to `"caller"`. A part of a `ConsoleLogger` line that follows a line break no longer starts with a space.', ru:'`nanolog.console.showSourceLine` (`setShowSourceLine`): строка исходника с вызовом под каждой записью, `    > LOG.warn("Charge of {} failed", amount);`, и под первыми тремя кадрами трассировки вне JDK; исходник ищется так же, как его ищет `LOG.dbg`, и строки нет, если исходник отсутствует или устарел; `JsonLogger` добавляет `"source"` в `"caller"`. Часть строки `ConsoleLogger` после перевода строки больше не начинается с пробела.'}
- ${en:'`LOG.catching(() -> cleanup())` logs an exception at `ERROR` with its stack trace and carries on, `LOG.catching(() -> parse(s), -1)` returns a default instead; checked exceptions are allowed, an `Error` is not caught, an interruption is kept. `LOG.installUncaughtExceptionHandler()` logs the exceptions that kill threads, from the first frame outside the JDK, then passes them to the previous handler; the dying thread writes its breadcrumbs and its open units too.', ru:'`LOG.catching(() -> cleanup())` записывает исключение в `ERROR` со стеком и продолжает работу, а `LOG.catching(() -> parse(s), -1)` вместо этого возвращает значение по умолчанию; проверяемые исключения разрешены, `Error` не перехватывается, прерывание сохраняется. `LOG.installUncaughtExceptionHandler()` записывает исключения, которые убивают потоки, с первого кадра вне JDK и передаёт их прежнему обработчику; умирающий поток пишет и свои breadcrumbs, и открытые единицы работы.'}
- ${en:'README: *Logging LLM calls*, a LangChain4j listener and a Spring AI advisor that log one line per model call with the OpenTelemetry `gen_ai.*` field names, the prompt and the completion as `LOG.content`, and a transcript at `DEBUG`.', ru:'README: *Логирование вызовов LLM* — слушатель для LangChain4j и advisor для Spring AI, которые логируют одну строку на вызов модели с именами полей OpenTelemetry `gen_ai.*`, промптом и ответом через `LOG.content` и стенограммой на уровне `DEBUG`.'}
- ${en:'Payloads: `LOG.content(prompt)` writes a prompt, a response or a request body as `[content 1834 chars #3f2a9c1b]`, its length and a hash keyed per JVM, unless `nanolog.content=truncate:<characters>` or `show` says otherwise, for all classes or, through `nanolog.content.<package or class>`, for some. The text is taken when the entry is created and always escaped against forged lines; in JSON fields a hidden payload is `{"content_len":1834,"content_hash":"3f2a9c1b"}`.', ru:'Содержимое: `LOG.content(prompt)` пишет промпт, ответ или тело запроса как `[content 1834 chars #3f2a9c1b]`, длину и хеш с ключом JVM, если `nanolog.content=truncate:<символы>` или `show` не говорит иного, для всех классов или, через `nanolog.content.<пакет или класс>`, для некоторых. Текст берётся при создании записи и всегда экранируется против подделки строк; в полях JSON скрытое содержимое — это `{"content_len":1834,"content_hash":"3f2a9c1b"}`.'}
- ${en:'Breadcrumbs: `nanolog.buffer=DEBUG` holds `DEBUG` and `TRACE` entries back, the newest 200 per thread (`nanolog.buffer.size`), and writes them before the next `ERROR` under `--- 37 earlier DEBUG entries ---`; inside `LOG.unit` the unit drops them when it succeeds and writes them after its line when it fails. Held-back levels count as enabled, entries are snapshots with the time of their call, and `JsonLogger` marks them `"breadcrumb":true`.', ru:'Breadcrumbs: `nanolog.buffer=DEBUG` придерживает записи `DEBUG` и `TRACE`, последние 200 на поток (`nanolog.buffer.size`), и пишет их перед следующим `ERROR` под заголовком `--- 37 earlier DEBUG entries ---`; внутри `LOG.unit` единица отбрасывает их при успехе и пишет после своей строки при неудаче. Придерживаемые уровни считаются включёнными, записи — снимки со временем вызова, а `JsonLogger` помечает их `"breadcrumb":true`.'}
- ${en:'Fields: `LOG.kv("orderId", id)` names an argument. In a `{}` it prints its value; without one `ConsoleLogger` and `Slf4jLogger` append `orderId=A-17` to the message and strict mode does not count it. `JsonLogger` writes every field into a `"fields"` object whose numbers and booleans keep their type, so `jq` selects entries by them, and `LogEntry.getFields()` gives them to any logger.', ru:'Поля: `LOG.kv("orderId", id)` даёт аргументу имя. В `{}` печатается его значение; без `{}` `ConsoleLogger` и `Slf4jLogger` дописывают `orderId=A-17` к сообщению, а строгий режим его не считает. `JsonLogger` пишет все поля в объект `"fields"`, в котором числа и логические значения сохраняют тип, поэтому `jq` отбирает записи по ним, а `LogEntry.getFields()` отдаёт их любому логгеру.'}
- ${en:'Context: `try (LOG.Context c = LOG.context("requestId", id)) { ... }` adds `requestId=r-17` to every entry the thread logs inside the block, nested scopes put the previous value back, and `LOG.wrap(task)` carries the context into a task that another thread runs. `ConsoleLogger` prints the pairs after the message (`nanolog.console.showContext`), `JsonLogger` writes a `"context"` object whose numbers and booleans keep their type, `Slf4jLogger` puts the pairs into the MDC for `%X{requestId}`, and `LogEntry.getContext()` gives them to any logger.', ru:'Контекст: `try (LOG.Context c = LOG.context("requestId", id)) { ... }` добавляет `requestId=r-17` ко всем записям потока внутри блока, вложенные области возвращают прежнее значение, а `LOG.wrap(task)` переносит контекст в задачу, которую выполняет другой поток. `ConsoleLogger` печатает пары после сообщения (`nanolog.console.showContext`), `JsonLogger` пишет объект `"context"`, в котором числа и логические значения сохраняют тип, `Slf4jLogger` кладёт пары в MDC для `%X{requestId}`, а `LogEntry.getContext()` отдаёт их любому логгеру.'}
- ${en:'`MemoryLogger.capture()` and `captureForwarding()` capture the entries of the current thread only, every level, while the logger of `LOG` stays installed for the other threads, so tests that run in parallel in one JVM can assert on their own log. The findings of strict mode on that thread reach the capture as well.', ru:'`MemoryLogger.capture()` и `captureForwarding()` перехватывают записи только текущего потока, на всех уровнях, а логгер `LOG` остаётся установленным для других потоков, поэтому тесты, идущие параллельно в одной JVM, могут проверять свой лог. Находки строгого режима в этом потоке тоже попадают в захват.'}

### 1.3

- ${en:'Levels change while the application runs, thread-safely: `LOG.setLevel(level)`, `LOG.setLevel(name, level)`, `LOG.clearLevel(name)`, `LOG.resetLevels()`, and `LOG.withLevel(name, level)` for a `try` block. The level is a `LogEntryLevel` or a string such as `"OFF"`, as in the settings. `ConsoleLogger`, `JsonLogger` and `CompositeLogger` implement the new `ILevelControl`, and the level setters of `ConsoleLogger` may now be called while other threads log.', ru:'Уровни меняются во время работы приложения, потокобезопасно: `LOG.setLevel(level)`, `LOG.setLevel(name, level)`, `LOG.clearLevel(name)`, `LOG.resetLevels()` и `LOG.withLevel(name, level)` для блока `try`. Уровень задаётся как `LogEntryLevel` или строкой вроде `"OFF"`, как в настройках. `ConsoleLogger`, `JsonLogger` и `CompositeLogger` реализуют новый `ILevelControl`, а сеттеры уровней `ConsoleLogger` теперь можно вызывать, пока другие потоки пишут в лог.'}
- ${en:'`LOG.once()` and `LOG.atMostEvery(period, unit)` limit a statement to its first call or to one entry per period, keyed by the call site. The next entry that gets through carries the number of skipped calls, `LogEntry.getSkippedCount()`, which `ConsoleLogger`, `JsonLogger` and `Slf4jLogger` print.', ru:'`LOG.once()` и `LOG.atMostEvery(period, unit)` ограничивают строку кода первым вызовом или одной записью за период, по месту вызова. Следующая прошедшая запись несёт число пропущенных вызовов, `LogEntry.getSkippedCount()`, и `ConsoleLogger`, `JsonLogger` и `Slf4jLogger` его печатают.'}
- ${en:'Shorter stack traces in `ConsoleLogger` and `JsonLogger`: `nanolog.exception.exclude` collapses runs of frames from packages such as `org.junit` or `jdk.internal.reflect` into `... N frames excluded`, `nanolog.exception.maxFrames` caps the frames of each throwable. Without them the output is that of `printStackTrace`, as before.', ru:'Короткие трассировки стека в `ConsoleLogger` и `JsonLogger`: `nanolog.exception.exclude` сворачивает подряд идущие кадры пакетов вроде `org.junit` или `jdk.internal.reflect` в `... N frames excluded`, `nanolog.exception.maxFrames` ограничивает число кадров каждого исключения. Без них вывод, как и раньше, совпадает с `printStackTrace`.'}
- `MemoryLogger`: ${en:'`assertNoneAtOrAbove(level)`, `assertLogged(level, text)` and `assertTemplateLogged(level, template)` fail with an `AssertionError` that lists every captured entry, stack traces and call sites included; `getEntries(Class)` selects the entries of one source class.', ru:'`assertNoneAtOrAbove(level)`, `assertLogged(level, text)` и `assertTemplateLogged(level, template)` падают с `AssertionError`, в котором перечислены все перехваченные записи вместе с трассировками и местами вызова; `getEntries(Class)` отбирает записи одного класса-источника.'}
- ${en:'Strict mode reaches tests: every finding also goes to the installed logger through the new `ILogger.onDiagnostic`, every occurrence rather than the first one of a template, and `MemoryLogger.assertNoDiagnostics()` fails on them. `CompositeLogger` forwards them.', ru:'Строгий режим доходит до тестов: каждая находка передаётся и установленному логгеру через новый `ILogger.onDiagnostic`, каждое повторение, а не только первое для шаблона, и `MemoryLogger.assertNoDiagnostics()` падает на них. `CompositeLogger` передаёт их дальше.'}
- `ConsoleLogger`, `JsonLogger`: ${en:'`nanolog.console.stream` (or `setStream`) chooses the stream of each level: `out` sends everything to standard output, so `java -jar app.jar | jq` keeps the `ERROR` lines, `err` sends everything to standard error, `err@WARN` warnings and errors. The default `err@ERROR` is the former behaviour.', ru:'`nanolog.console.stream` (или `setStream`) выбирает поток для каждого уровня: `out` отправляет всё в стандартный вывод, и `java -jar app.jar | jq` сохраняет строки `ERROR`, `err` отправляет всё в поток ошибок, `err@WARN` — предупреждения и ошибки. Значение по умолчанию `err@ERROR` повторяет прежнее поведение.'}
- `ConsoleLogger`: ${en:'`setShowThread(true)` or `nanolog.console.showThread=true` prints the name of the thread after the date, `INFO 16.11.2026 10:15:02 [main] [PaymentService] Charged 42`. Off by default, so the format of existing output does not change.', ru:'`setShowThread(true)` или `nanolog.console.showThread=true` печатает имя потока после даты: `INFO 16.11.2026 10:15:02 [main] [PaymentService] Charged 42`. По умолчанию выключено, поэтому формат прежнего вывода не меняется.'}
- ${en:'Calls without an explicit class no longer walk the stack when their level is off for every class: `LOG` asks the new `ILogger.mayBeEnabled(level)` first. `ConsoleLogger`, `JsonLogger` and `CompositeLogger` answer it from their switches, package rules and delegates; other loggers keep the default `true` and work as before.', ru:'Вызовы без явного класса больше не обходят стек, если их уровень выключен для всех классов: `LOG` сначала спрашивает новый `ILogger.mayBeEnabled(level)`. `ConsoleLogger`, `JsonLogger` и `CompositeLogger` отвечают по своим переключателям, правилам пакетов и делегатам; остальные логгеры получают `true` по умолчанию и работают как раньше.'}
- ${en:'On Java 9+ caller detection uses `StackWalker`: it stops at the calling frame and loads no class by name, so a call without an explicit class costs about 3 µs instead of 9 to 26 µs on JDK 17 and hardly grows with the depth of the stack. A caller in a class loader that neither the context loader nor the loader of Nanolog can see is now found exactly instead of being skipped. Java 8 keeps the `Throwable` walk.', ru:'На Java 9+ вызывающий класс определяется через `StackWalker`: обход останавливается на кадре вызова и не загружает классы по имени, поэтому вызов без явного класса стоит около 3 мкс вместо 9–26 мкс на JDK 17 и почти не дорожает с глубиной стека. Вызывающий класс в загрузчике, который не видят ни контекстный загрузчик, ни загрузчик Nanolog, теперь находится точно, а не пропускается. На Java 8 остаётся обход через `Throwable`.'}
- ${en:'A logger that throws no longer breaks the logging call: the failure is reported once per logger class with a `[NANOLOG]` line, and the entry is printed to `System.err` instead. Inside `CompositeLogger` a throwing delegate no longer stops the others.', ru:'Логгер, который бросает исключение, больше не роняет вызов логирования: сбой один раз на класс логгера отмечается строкой `[NANOLOG]`, а запись печатается в `System.err`. Внутри `CompositeLogger` упавший делегат больше не останавливает остальных.'}
- ${en:'A lazy message whose `Supplier` throws no longer escapes from the logging call: the entry is logged with the message `[FAILED supplier: <exception>]`.', ru:'Ленивое сообщение, у которого `Supplier` бросает исключение, больше не роняет вызов логирования: запись попадает в лог с сообщением `[FAILED supplier: <исключение>]`.'}
- ${en:'When no stack frame qualifies as the caller (`-XX:-StackTraceInThrowable`, or code running entirely inside an `ILogger`), the entry is logged without a source class instead of throwing `IllegalStateException`.', ru:'Если в стеке нет подходящего вызывающего кадра (`-XX:-StackTraceInThrowable` или код целиком внутри `ILogger`), запись логируется без класса-источника, а не падает с `IllegalStateException`.'}
- ${en:'Caller detection no longer lets an error from a class loader escape: a frame whose class fails to load with `NoClassDefFoundError`, `MissingReflectionRegistrationError` from GraalVM or any other throwable is resolved through the other loader or skipped.', ru:'Определение вызывающего класса больше не выпускает наружу ошибки загрузчика классов: кадр, класс которого не загружается из-за `NoClassDefFoundError`, `MissingReflectionRegistrationError` от GraalVM или любой другой ошибки, ищется вторым загрузчиком или пропускается.'}
- ${en:'A logger installed with `LOG.init(...)` or `MemoryLogger.install()` is now guaranteed to be seen by threads that are already logging; before, a test could miss entries logged from an executor thread.', ru:'Логгер, установленный через `LOG.init(...)` или `MemoryLogger.install()`, теперь гарантированно видят и уже работающие потоки; раньше тест мог не получить записи из потока executor.'}
- `ConsoleLogger`: ${en:'an anonymous or local source class passed explicitly is printed as `[Foo$1]` instead of `[]` or `[null]`; `LogEntry.toString()` names it too.', ru:'анонимный или локальный класс-источник, переданный явно, печатается как `[Foo$1]`, а не `[]` или `[null]`; `LogEntry.toString()` тоже называет его.'}
- ${en:'A `[NANOLOG]` diagnostic is always one line. Control characters in the values it repeats, such as a level passed to `LOG.setLevel` from a request, an exception message or a template, are printed as `\u000a` escapes, so such a value cannot forge a line of the log.', ru:'Диагностика `[NANOLOG]` всегда занимает одну строку. Управляющие символы в значениях, которые она повторяет, например в уровне из запроса, переданном в `LOG.setLevel`, в сообщении исключения или в шаблоне, печатаются как `\u000a`, поэтому такое значение не может подделать строку лога.'}
- ${en:'Documentation: the `Slf4jLogger` section no longer says that MDC works with every backend; slf4j-simple and slf4j-nop discard MDC values.', ru:'Документация: раздел о `Slf4jLogger` больше не утверждает, что MDC работает с любым бэкендом; slf4j-simple и slf4j-nop отбрасывают значения MDC.'}
- ${en:'Documentation: `ILogger.isEnabled` no longer promises that returning `false` skips the whole cost of a call; caller detection has already walked the stack by then.', ru:'Документация: `ILogger.isEnabled` больше не обещает, что возврат `false` избавляет от всей стоимости вызова; определение вызывающего класса к этому моменту уже обошло стек.'}
- ${en:'JMH benchmarks in `src/jmh`, built with `mvn -P benchmark test-compile exec:exec`, with results for JDK 8 to 25 in [`src/jmh/RESULTS.md`](src/jmh/RESULTS.md); the README quotes the measured costs.', ru:'JMH-бенчмарки в `src/jmh`, запуск `mvn -P benchmark test-compile exec:exec`, с результатами для JDK 8–25 в [`src/jmh/RESULTS.md`](src/jmh/RESULTS.md); README приводит измеренные цифры.'}

### 1.2

- ${en:'A trailing `Throwable` that no `{}` consumes becomes the exception of the entry, as in SLF4J: `LOG.error("Failed", e)` and `LOG.error("Failed {}", id, e)` no longer drop the stack trace.', ru:'`Throwable` в конце списка аргументов, который не занял ни один `{}`, становится исключением записи, как в SLF4J: `LOG.error("Failed", e)` и `LOG.error("Failed {}", id, e)` больше не теряют трассировку стека.'}
- ${en:'`LogEntry.getCaller()` returns the frame of the logging call, and `ConsoleLogger.setShowLocation(true)` prints it as `[PaymentService.charge(PaymentService.java:87)]`.', ru:'`LogEntry.getCaller()` возвращает кадр вызова логирования, а `ConsoleLogger.setShowLocation(true)` печатает его как `[PaymentService.charge(PaymentService.java:87)]`.'}
- ${en:'`ConsoleLogger` reads its levels and options from properties and environment variables: `nanolog.level`, `nanolog.level.<package or class>`, `nanolog.levels` and `nanolog.console.*`. Every property, `nanolog.logger` and `nanolog.config` included, can be set as an environment variable (`NANOLOG_LEVEL`), which beats `nanolog.properties`.', ru:'`ConsoleLogger` читает уровни и настройки из свойств и переменных окружения: `nanolog.level`, `nanolog.level.<пакет или класс>`, `nanolog.levels` и `nanolog.console.*`. Любое свойство, включая `nanolog.logger` и `nanolog.config`, можно задать переменной окружения (`NANOLOG_LEVEL`), и она главнее `nanolog.properties`.'}
- ${en:'`JsonLogger` writes one JSON object per line. `LogEntry.getTimestamp()` and `LogEntry.getThreadName()` tell when and where an entry was created.', ru:'`JsonLogger` пишет один JSON-объект в строке. `LogEntry.getTimestamp()` и `LogEntry.getThreadName()` сообщают, когда и где создана запись.'}
- ${en:'`MemoryLogger` captures entries in tests (`try (MemoryLogger log = MemoryLogger.install()) { ... }`), `CompositeLogger` sends entries to several loggers.', ru:'`MemoryLogger` перехватывает записи в тестах (`try (MemoryLogger log = MemoryLogger.install()) { ... }`), `CompositeLogger` отправляет записи в несколько логгеров.'}
- ${en:'`nanolog.strict=true` reports calls whose `{}` placeholders and arguments do not match, once per template, with the call site.', ru:'`nanolog.strict=true` сообщает о вызовах, у которых плейсхолдеры `{}` не совпадают с аргументами, по разу на шаблон и с местом вызова.'}
- `Slf4jLogger`: ${en:'location-aware backends (Logback, Log4j 2) report the code that called `LOG` in `%file:%line` and `%l`, instead of `Slf4jLogger.log`.', ru:'бэкенды, определяющие место вызова (Logback, Log4j 2), показывают в `%file:%line` и `%l` код, вызвавший `LOG`, а не `Slf4jLogger.log`.'}
- `ConsoleLogger`: ${en:'a throwable whose `toString()` throws no longer escapes from the logging call; it is printed as `[FAILED printStackTrace()]`.', ru:'исключение, у которого бросает `toString()`, больше не вылетает из вызова логирования, а печатается как `[FAILED printStackTrace()]`.'}
- ${en:'The whole public API has Javadoc, and the build fails on a missing comment.', ru:'У всего публичного API есть Javadoc, и сборка падает, если комментария не хватает.'}
- ${en:'[`llms.txt`](llms.txt) and the agent skill [`skills/nanolog/SKILL.md`](skills/nanolog/SKILL.md) give coding agents the rules of the library; both are generated with the README.', ru:'[`llms.txt`](llms.txt) и навык агента [`skills/nanolog/SKILL.md`](skills/nanolog/SKILL.md) дают кодовым агентам правила работы с библиотекой; оба генерируются вместе с README.'}

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

- **${en:'Questions and feedback', ru:'Вопросы и отзывы'}:** [Discussions](${repo}/discussions) · **${en:'bugs', ru:'ошибки'}:** [Issues](${repo}/issues/new/choose) · ${en:'English or Russian; what to include is in', ru:'на русском или английском; что приложить, описано в'} [SUPPORT.md](${en:'SUPPORT.md', ru:'SUPPORT.ru.md'}).
- ${en:'Building, testing and changing the code or the docs: see', ru:'Сборка, тесты и изменения кода или документации: см.'} [CONTRIBUTING.md](${en:'CONTRIBUTING.md', ru:'CONTRIBUTING.ru.md'}).
- ${en:'Vulnerabilities are reported privately: see', ru:'Об уязвимостях сообщайте приватно: см.'} [SECURITY.md](${en:'SECURITY.md', ru:'SECURITY.ru.md'}).

## ${en:'License', ru:'Лицензия'}

[Apache License 2.0](LICENSE).

---
*${en:'Generated with', ru:'Сгенерировано с помощью'} [nanolaba/readme-generator](https://github.com/nanolaba/readme-generator)*
