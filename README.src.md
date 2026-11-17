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

# ${en:'Nanolog: Java logging without logger fields', ru:'Nanolog: логирование в Java без полей логгера'}

${name} — библиотека логирования для Java 8+ без зависимостей: все вызовы идут<!--ru-->
через статические методы класса `LOG`, класс-источник определяется автоматически,<!--ru-->
поэтому поле логгера в каждом классе больше не нужно. Она печатает в консоль, пишет<!--ru-->
JSON-строки, перехватывает записи в тестах и работает сама по себе или поверх<!--ru-->
**SLF4J**, а значит и поверх Logback, Log4j 2, JBoss Logging и других.<!--ru-->
${name} is a zero-dependency logging library for Java 8+: every call goes through<!--en-->
the static methods of `LOG`, the source class is detected automatically, so no class<!--en-->
needs a logger field. It prints to the console, writes JSON lines, captures entries<!--en-->
in tests, and works on its own or on top of **SLF4J**, and therefore on top of<!--en-->
Logback, Log4j 2, JBoss Logging and others.<!--en-->

${en:'Nanolog for Java (`com.nanolaba:nanolog`) is not related to [NanoLog](https://github.com/PlatformLab/NanoLog), the nanosecond-scale logger for C++.', ru:'Nanolog для Java (`com.nanolaba:nanolog`) не связан с [NanoLog](https://github.com/PlatformLab/NanoLog), наносекундным логгером для C++.'}

## ${en:'Why Nanolog', ru:'Зачем нужен Nanolog'}

- **${en:'Print debugging that names its values.', ru:'Отладочная печать, которая называет значения.'}** ${en:'`LOG.dbg(order.total())` prints `order.total() = 129.90 (BigDecimal)` with the file and line of the call and returns the value, like `dbg!` in Rust and icecream in Python; objects without `toString()` are shown field by field instead of `Order@2f92e0f4`; see', ru:'`LOG.dbg(order.total())` печатает `order.total() = 129.90 (BigDecimal)` с файлом и строкой вызова и возвращает значение, как `dbg!` в Rust и icecream в Python; объекты без `toString()` показываются поле за полем, а не как `Order@2f92e0f4`; см.'} [${en:'Print debugging', ru:'Отладочная печать'}](#${en:'print-debugging-logdbg-prints-the-expression-and-its-value', ru:'отладочная-печать-logdbg-печатает-выражение-и-его-значение'}).
- **${en:'No logger fields.', ru:'Нет полей логгера.'}** `LOG.info("...")` ${en:'works from any class; the caller is found by walking the stack', ru:'работает из любого класса, вызывающий определяется по стеку'}.
- **${en:'Zero dependencies.', ru:'Ноль зависимостей.'}** ${en:'No transitive libraries;', ru:'Никаких транзитивных библиотек;'} `slf4j-api` ${en:'is needed only if you use', ru:'нужен, только если вы используете'} `Slf4jLogger`.
- **${en:'SLF4J-style placeholders.', ru:'Подстановка в стиле SLF4J.'}** `{}` ${en:'with escaping, array rendering and sane handling of missing or extra arguments', ru:'с экранированием, выводом массивов и разумной обработкой лишних и недостающих аргументов'}.
- **${en:'JSON lines and tests built in.', ru:'JSON и тесты из коробки.'}** ${en:'`JsonLogger` writes one JSON object per line for `jq` and log collectors; `MemoryLogger` captures entries in a test and asserts on them, with the whole log in the failure message; see', ru:'`JsonLogger` пишет один JSON-объект в строке для `jq` и сборщиков логов; `MemoryLogger` перехватывает записи в тесте и проверяет их, показывая весь лог в сообщении об ошибке; см.'} [JsonLogger](#jsonlogger) ${en:'and', ru:'и'} [MemoryLogger ${en:'and', ru:'и'} CompositeLogger](#${en:'memorylogger-and-compositelogger', ru:'memorylogger-и-compositelogger'}).
- **${en:'Levels change at run time.', ru:'Уровни меняются на лету.'}** ${en:'`LOG.setLevel("com.acme.payments", DEBUG)` from an admin endpoint or a test takes effect in every thread, and `LOG.withLevel(...)` puts the old level back when the block ends; see', ru:'`LOG.setLevel("com.acme.payments", DEBUG)` из админского эндпоинта или теста действует во всех потоках, а `LOG.withLevel(...)` возвращает прежний уровень в конце блока; см.'} [${en:'Changing levels at run time', ru:'Изменение уровней во время работы'}](#${en:'changing-levels-at-run-time', ru:'изменение-уровней-во-время-работы'}).
- **${en:'Logging never throws.', ru:'Логирование никогда не падает.'}** ${en:'A broken', ru:'Сломанный'} `toString()` ${en:'becomes', ru:'превращается в'} `[FAILED toString()]` ${en:'instead of an exception in your code', ru:'вместо исключения в вашем коде'}.
- **${en:'Lazy messages.', ru:'Ленивые сообщения.'}** `LOG.debug(() -> ...)` ${en:'is evaluated only when the level is enabled', ru:'вычисляется только при включённом уровне'}.
- **${en:'Pluggable.', ru:'Расширяемый.'}** ${en:'A custom backend is one lambda or one class implementing', ru:'Свой бэкенд — это одна лямбда или один класс, реализующий'} `ILogger`.
- **${en:'Fits the agent loop.', ru:'Удобен агентам.'}** ${en:'One line per log call, output from the first run, an API that fits in one context window; see', ru:'Одна строка на вызов, вывод с первого запуска, API целиком помещается в контекстное окно; см.'} [${en:'Agent-driven development', ru:'Агентная разработка'}](#${en:'agent-driven-development', ru:'агентная-разработка'}).

${en:'The whole library:', ru:'Вся библиотека целиком:'}

${widget:fileTree(path='src/main/java/com/nanolaba/logging', depth='1')}

- `LOG` — ${en:'the static entry point with all the', ru:'статическая точка входа со всеми'} `trace` / `debug` / `info` / `warn` / `error` ${en:'overloads', ru:'перегрузками'} ${en:'and', ru:'и'} `dbg`.
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
| `LOG.setLevel(level)`, `LOG.setLevel("com.acme", level)` | ${en:'change the global level or the level of a package while the application runs, see', ru:'смена глобального уровня или уровня пакета во время работы, см.'} [${en:'Changing levels at run time', ru:'Изменение уровней во время работы'}](#${en:'changing-levels-at-run-time', ru:'изменение-уровней-во-время-работы'}) |
| `LOG.withLevel("com.acme", level)` | ${en:'the same for a `try` block, restored at its end', ru:'то же на время блока `try`, с восстановлением в конце'} |
| `LOG.dbg(expression)` | ${en:'print the expression, its value and the call site, and return the value, see', ru:'печать выражения, его значения и места вызова с возвратом значения, см.'} [${en:'Print debugging', ru:'Отладочная печать'}](#${en:'print-debugging-logdbg-prints-the-expression-and-its-value', ru:'отладочная-печать-logdbg-печатает-выражение-и-его-значение'}) |
| `LOG.once().warn(...)` | ${en:'log a statement only the first time it runs, see', ru:'запись только при первом выполнении строки, см.'} [${en:'Rate limiting', ru:'Ограничение частоты'}](#${en:'rate-limiting', ru:'ограничение-частоты'}) |
| `LOG.atMostEvery(10, TimeUnit.SECONDS).warn(...)` | ${en:'at most once per period, with the number of skipped calls on the next entry', ru:'не чаще раза за период, с числом пропущенных вызовов в следующей записи'} |
| `LOG.log(level, sourceClass, throwable, supplier, args)` | ${en:'the universal entry point every overload delegates to', ru:'универсальная точка входа, к которой сводятся все перегрузки'} |

${en:'Every class and method is documented in the', ru:'Каждый класс и метод описан в'} [Javadoc](${javadoc}/${stableVersion}), ${en:'which IDEs also show from the sources jar.', ru:'который IDE показывают и из jar с исходниками.'}

## ${en:'Print debugging: `LOG.dbg` prints the expression and its value', ru:'Отладочная печать: `LOG.dbg` печатает выражение и его значение'}

`LOG.dbg(...)` is the Java counterpart of Rust's `dbg!` and Python's icecream: it prints the expression as written in the source, its value and type, and the file and line of the call, and returns the value, so it wraps any expression in place. Since 1.4.<!--en-->
`LOG.dbg(...)` — аналог `dbg!` из Rust и icecream из Python для Java: печатает выражение так, как оно записано в исходнике, его значение и тип, файл и строку вызова и возвращает значение, поэтому им можно обернуть любое выражение прямо на месте. Начиная с 1.4.<!--ru-->

${widget:import(path='${examples}/PrintDebugging.java', region='dbg', wrap='true')}

```
DBG 17.11.2026 10:15:02 [PrintDebugging.checkout(PrintDebugging.java:21)] order.total() = 129.90 (BigDecimal)
DBG 17.11.2026 10:15:02 [PrintDebugging.checkout(PrintDebugging.java:22)] user = "alice", cart.size() = 3 (Integer)
DBG 17.11.2026 10:15:02 [PrintDebugging.checkout(PrintDebugging.java:23)] reached
```

- The text comes from the source file of the call site. The source directory is derived from where the class was loaded: `target/classes` leads to `src/main/java`, `target/test-classes` to `src/test/java`, Gradle's `build/classes/java/main` to `src/main/java`, and the same for Kotlin; for other layouts the usual directories of the working directory are tried. `nanolog.sources=dir1,dir2` names them explicitly. Each call site is read once, arguments spread over several lines included.<!--en-->
- Текст берётся из исходного файла места вызова. Каталог исходников выводится из того, откуда загружен класс: `target/classes` ведёт к `src/main/java`, `target/test-classes` — к `src/test/java`, `build/classes/java/main` из Gradle — к `src/main/java`, и так же для Kotlin; для других раскладок пробуются обычные каталоги рабочей директории. `nanolog.sources=dir1,dir2` задаёт их явно. Каждое место вызова читается один раз, включая аргументы, разнесённые на несколько строк.<!--ru-->
- Without the text only the values are printed, never an error: when no source directory is found (a production jar), the source file is newer than its class, or the line holds two `dbg` calls, since Java reports the line of a call but not its column.<!--en-->
- Без текста печатаются только значения, и ошибки не бывает: когда каталог исходников не найден (jar в продакшене), исходник новее своего класса или на строке два вызова `dbg`, ведь Java сообщает строку вызова, но не колонку.<!--ru-->
- Strings and chars are quoted and escaped, so a value never breaks the line; collections, maps and arrays are printed item by item, objects without a `toString()` of their own field by field (see below); other values are followed by their type, unless their text starts with it, as `Invoice{id=17, ...}` does.<!--en-->
- Строки и символы печатаются в кавычках и с экранированием, поэтому значение никогда не разрывает строку; коллекции, словари и массивы печатаются поэлементно, объекты без собственного `toString()` — поле за полем (см. ниже); за остальными значениями следует их тип, если их текст не начинается с него, как у `Invoice{id=17, ...}`.<!--ru-->
- It is a `DEBUG` entry: `nanolog.level` and package rules apply, and a level that is off everywhere costs no stack walk. `ConsoleLogger` marks it `DBG` and prints the call site even without `showLocation`, `JsonLogger` adds `"dbg":true`, any logger tells it apart with `LogEntry.isDbg()`. `LOG.once().dbg(...)` and `LOG.atMostEvery(...).dbg(...)` limit it like any other statement.<!--en-->
- Это запись уровня `DEBUG`: действуют `nanolog.level` и правила пакетов, а уровень, выключенный везде, не стоит обхода стека. `ConsoleLogger` помечает её `DBG` и печатает место вызова даже без `showLocation`, `JsonLogger` добавляет `"dbg":true`, любой логгер отличает её по `LogEntry.isDbg()`. `LOG.once().dbg(...)` и `LOG.atMostEvery(...).dbg(...)` ограничивают её, как любую другую строку.<!--ru-->
- `dbg` is meant to be temporary, for you and for a coding agent alike: a debug print that names its values replaces a series of `System.out.println` calls, and its distinct name is easy to find and remove before a commit (`grep -rn "LOG.dbg("`).<!--en-->
- `dbg` задуман как временный, и для человека, и для кодового агента: отладочная печать, называющая свои значения, заменяет серию вызовов `System.out.println`, а её особое имя легко найти и убрать перед коммитом (`grep -rn "LOG.dbg("`).<!--ru-->

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
- **A log call cannot fail the run.** Logging never throws: a broken `toString()`, a lazy message whose lambda throws, a `null` or a wrong number of `{}` arguments ends up as a readable message, not as an exception. A log statement is never the reason a test turns red. In test runs, `-Dnanolog.strict=true` still reports such mismatches with the call site, so they get fixed instead of staying silent.<!--en-->
- **Вызов лога не может уронить запуск.** Логирование никогда не бросает исключений: сломанный `toString()`, ленивое сообщение, лямбда которого бросает исключение, `null` или неверное число аргументов для `{}` превращаются в читаемое сообщение, а не в исключение. Строка лога никогда не становится причиной красного теста. При этом в тестовых запусках `-Dnanolog.strict=true` сообщает о таких несовпадениях вместе с местом вызова, чтобы их исправили, а не оставили незамеченными.<!--ru-->
- **Repeated lines do not crowd out the rest.** A retry loop or a full queue can print the same line thousands of times and push the line that matters out of the context window. `LOG.once()` and `LOG.atMostEvery(10, TimeUnit.SECONDS)` keep such a statement to one line, and the next one that gets through says `[skipped 241]`; see [Rate limiting](#rate-limiting).<!--en-->
- **Повторяющиеся строки не вытесняют остальное.** Цикл повторов или переполненная очередь может напечатать одну и ту же строку тысячи раз и вытолкнуть нужную строку из контекстного окна. `LOG.once()` и `LOG.atMostEvery(10, TimeUnit.SECONDS)` сводят такую строку к одной, а следующая пропущенная запись сообщает `[skipped 241]`; см. [Ограничение частоты](#ограничение-частоты).<!--ru-->
- **The whole library fits in a context window.** One package of small classes, one entry point, this README and the [Javadoc](${javadoc}/${stableVersion}). Every public method has Javadoc, so the IDE and the sources jar give the same answers as this file. An agent reads everything it needs in one pass and does not have to map a framework first.<!--en-->
- **Вся библиотека помещается в контекстное окно.** Один пакет небольших классов, одна точка входа, этот README и [Javadoc](${javadoc}/${stableVersion}). У каждого публичного метода есть Javadoc, поэтому IDE и jar с исходниками отвечают так же, как этот файл. Агент читает всё нужное за один проход, и ему не приходится сначала строить карту фреймворка.<!--ru-->
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
| `nanolog.sources` | ${en:'source directories that `LOG.dbg` reads the text of its arguments from, comma-separated; empty turns the reading off', ru:'каталоги исходников, из которых `LOG.dbg` читает текст аргументов, через запятую; пустое значение отключает чтение'} | ${en:'derived from the class location', ru:'выводятся из расположения класса'} |
| `nanolog.dump` | ${en:'`auto` prints `{}` arguments whose class has no `toString()` of its own field by field, see', ru:'`auto` печатает аргументы `{}`, у класса которых нет собственного `toString()`, поле за полем, см.'} [${en:'Message formatting', ru:'Форматирование сообщений'}](#${en:'message-formatting', ru:'форматирование-сообщений'}) | `off` |
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
- `showSource` / `showSourceFullName` — ${en:'the simple or the fully qualified source class name. An anonymous or local class passed explicitly has no such name and is shown by its binary name, `Foo$1` or `com.acme.Foo$1`', ru:'короткое или полное имя класса-источника. У анонимного или локального класса, переданного явно, такого имени нет, и он показывается по двоичному имени: `Foo$1` или `com.acme.Foo$1`'}.
- `showLocation` — ${en:'the call site after the class name, in the form stack traces use, so IDEs and terminals turn it into a link: `[PaymentService.charge(PaymentService.java:87)]`. Off by default. A call with an explicit source class has no call site and keeps the plain `[PaymentService]`.', ru:'место вызова после имени класса в том же виде, что и в трассировках стека, поэтому IDE и терминалы превращают его в ссылку: `[PaymentService.charge(PaymentService.java:87)]`. По умолчанию выключено. У вызова с явным классом-источником места вызова нет, и остаётся обычный `[PaymentService]`.'}
- `showThread` — ${en:'the name of the thread that logged, in square brackets after the date: `[main]`, `[pool-1-thread-3]`. Off by default; with a thread pool it tells the interleaved lines of different tasks apart. `JsonLogger` always writes the thread.', ru:'имя потока, который записал строку, в квадратных скобках после даты: `[main]`, `[pool-1-thread-3]`. По умолчанию выключено; при пуле потоков помогает различать перемешанные строки разных задач. `JsonLogger` пишет поток всегда.'}
- `stream` — ${en:'which levels go to `System.err`: `err@ERROR` (the default) sends only errors there, `err@WARN` warnings and errors, `err` everything, `out` nothing. One stream keeps errors in a pipe and keeps the order of the lines when standard output and standard error are captured apart.', ru:'какие уровни идут в `System.err`: `err@ERROR` (по умолчанию) отправляет туда только ошибки, `err@WARN` — предупреждения и ошибки, `err` — всё, `out` — ничего. Один поток сохраняет ошибки в конвейере и порядок строк, когда стандартный вывод и поток ошибок перехватываются раздельно.'}
- `exceptionExclude` / `exceptionMaxFrames` — ${en:'shorter stack traces, see', ru:'короткие трассировки стека, см.'} [${en:'Shorter stack traces', ru:'Короткие трассировки стека'}](#${en:'shorter-stack-traces', ru:'короткие-трассировки-стека'}).
- `traceEnabled` … `errorEnabled` — ${en:'per-level switches; everything is enabled by default, so switch `TRACE` and `DEBUG` off in production, in code or with `nanolog.level=INFO`. Disabled levels are rejected before the `LogEntry` is built, so lazy messages are never evaluated. `setLevel(level)` sets all five at once, and the switches may change while other threads log; see [Changing levels at run time](#changing-levels-at-run-time).', ru:'переключатели по уровням; по умолчанию включено всё, поэтому в продакшене отключите `TRACE` и `DEBUG` в коде или через `nanolog.level=INFO`. Отключённые уровни отсекаются до создания `LogEntry`, поэтому ленивые сообщения не вычисляются. `setLevel(level)` задаёт все пять сразу, а менять переключатели можно и пока другие потоки пишут в лог; см. [Изменение уровней во время работы](#изменение-уровней-во-время-работы).'}

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
| `nanolog.console.showThread` | `NANOLOG_CONSOLE_SHOWTHREAD` | ${en:'the same', ru:'то же'} |
| `nanolog.console.stream` | `NANOLOG_CONSOLE_STREAM` | `out`, `err` ${en:'or', ru:'или'} `err@LEVEL` (${en:'default', ru:'по умолчанию'} `err@ERROR`), ${en:'like the setter', ru:'как одноимённый сеттер'} |
| `nanolog.console.dateFormat` | `NANOLOG_CONSOLE_DATEFORMAT` | ${en:'a `SimpleDateFormat` pattern', ru:'шаблон `SimpleDateFormat`'} |
| `nanolog.exception.exclude` | `NANOLOG_EXCEPTION_EXCLUDE` | ${en:'package or class prefixes whose stack frames are collapsed, comma-separated', ru:'префиксы пакетов или классов, чьи кадры стека сворачиваются, через запятую'} |
| `nanolog.exception.maxFrames` | `NANOLOG_EXCEPTION_MAXFRAMES` | ${en:'the most stack frames per throwable;', ru:'наибольшее число кадров стека на исключение;'} `0` ${en:'(default) for no limit', ru:'(по умолчанию) — без ограничения'} |

```
java -Dnanolog.level=WARN -Dnanolog.level.com.acme.payments=DEBUG -jar app.jar
NANOLOG_LEVEL=WARN NANOLOG_LEVELS=com.acme.payments=DEBUG java -jar app.jar
```

- ${en:'A package or class rule beats the global level, however that level was set, setters included. Turning `DEBUG` on for one package therefore needs no code change.', ru:'Правило для пакета или класса главнее глобального уровня, как бы тот ни был задан, в том числе сеттерами. Поэтому включение `DEBUG` для одного пакета не требует правки кода.'}
- ${en:'The most specific rule wins: the class itself, then its enclosing classes, then its packages from the innermost outwards. A rule matches whole name segments only, so `com.acme.pay` does not cover `com.acme.payments`.', ru:'Действует самое точное правило: сам класс, затем внешние классы, затем пакеты от ближайшего к дальнему. Правило совпадает только с целыми частями имени, поэтому `com.acme.pay` не распространяется на `com.acme.payments`.'}
- ${en:'`nanolog.level.<name>` beats an item for the same name in `nanolog.levels`.', ru:'`nanolog.level.<имя>` главнее элемента с тем же именем в `nanolog.levels`.'}
- ${en:'Setters called after the constructor override the values it read, so `new ConsoleLogger().setDebugEnabled(false)` in code wins over `nanolog.level`; package and class rules still apply on top.', ru:'Сеттеры, вызванные после конструктора, главнее прочитанных им значений, поэтому `new ConsoleLogger().setDebugEnabled(false)` в коде главнее `nanolog.level`; правила для пакетов и классов при этом продолжают действовать.'}
- ${en:'An invalid value prints a `[NANOLOG]` diagnostic to `System.err` and is ignored; logging goes on. A diagnostic is always one line: control characters in the value it repeats are printed as escapes such as `\u000a`.', ru:'Неверное значение печатает в `System.err` диагностику с префиксом `[NANOLOG]` и игнорируется; логирование продолжается. Диагностика всегда занимает одну строку: управляющие символы в значении, которое она повторяет, печатаются как экранированные последовательности вроде `\u000a`.'}
- ${en:'`nanolog.properties` is read when `LOG` initialises, so a `ConsoleLogger` created before that sees only `-D` and the environment.', ru:'`nanolog.properties` читается при инициализации `LOG`, поэтому `ConsoleLogger`, созданный раньше, видит только `-D` и переменные окружения.'}

### ${en:'Changing levels at run time', ru:'Изменение уровней во время работы'}

${en:'The levels of `ConsoleLogger` and `JsonLogger` can change while the application runs, for a verbose mode, an admin endpoint or a test:', ru:'Уровни `ConsoleLogger` и `JsonLogger` можно менять во время работы приложения: для подробного режима, из админского эндпоинта или в тесте:'}

${widget:import(path='${examples}/RuntimeLevels.java', region='runtime', wrap='true')}

- ${en:'A change takes effect on the next logging call in every thread, threads that are already running included.', ru:'Изменение действует со следующего вызова логирования во всех потоках, в том числе уже запущенных.'}
- ${en:'A level set for a package or class works exactly like `nanolog.level.<name>`: it beats the global level, and the most specific rule wins. `LOG.setLevel(level)` changes only the global level and leaves such rules alone.', ru:'Уровень пакета или класса работает так же, как `nanolog.level.<имя>`: он главнее глобального уровня, и действует самое точное правило. `LOG.setLevel(level)` меняет только глобальный уровень и такие правила не трогает.'}
- ${en:'`LOG.withLevel(...)` restores the rule the name had when the `try` block ends, also when it ends with an exception. The change is JVM-wide, not limited to the current thread. Nested overrides of the same name restore correctly when they close in reverse order, as try-with-resources does.', ru:'`LOG.withLevel(...)` возвращает прежнее правило в конце блока `try`, даже если блок завершился исключением. Изменение действует на всю JVM, а не только на текущий поток. Вложенные переопределения одного имени восстанавливаются правильно, если закрываются в обратном порядке, как в try-with-resources.'}
- ${en:'`LOG.resetLevels()` reads `nanolog.level`, `nanolog.levels` and `nanolog.level.<name>` again: from `-D` and the environment as they are now, from `nanolog.properties` as it was loaded at startup. It undoes every change made at run time, the level setters of `ConsoleLogger` included; the display options stay as they are.', ru:'`LOG.resetLevels()` заново читает `nanolog.level`, `nanolog.levels` и `nanolog.level.<имя>`: из `-D` и переменных окружения в их текущем виде, из `nanolog.properties` — в том виде, в каком файл был загружен при старте. Это отменяет все изменения, сделанные во время работы, включая сеттеры уровней `ConsoleLogger`; настройки вывода остаются как есть.'}
- ${en:'`CompositeLogger` passes each change to those of its delegates that support it. The other loggers have no levels to change: `Slf4jLogger` takes them from the SLF4J backend, and `MemoryLogger` captures everything. When such a logger is installed directly, the first call prints one `[NANOLOG]` line and nothing changes.', ru:'`CompositeLogger` передаёт каждое изменение тем делегатам, которые его поддерживают. У остальных логгеров нет уровней, которые можно поменять: `Slf4jLogger` берёт их из бэкенда SLF4J, а `MemoryLogger` перехватывает всё. Если такой логгер установлен напрямую, первый вызов печатает одну строку `[NANOLOG]`, и ничего не меняется.'}
- ${en:'A level may also be given as a string, as in the settings: `TRACE` to `OFF`, ignoring case. `LOG.setLevel("com.acme.noisy", "OFF")` switches a package off, and a value from a request or a command line needs no parsing of your own.', ru:'Уровень можно передать и строкой, как в настройках: от `TRACE` до `OFF`, без учёта регистра. `LOG.setLevel("com.acme.noisy", "OFF")` выключает пакет целиком, а значение из запроса или командной строки не нужно разбирать самому.'}
- ${en:'A `null` or blank name and a `null` or unknown level print a `[NANOLOG]` diagnostic and change nothing.', ru:'Пустое имя, `null` вместо имени или уровня и неизвестный уровень печатают диагностику `[NANOLOG]` и ничего не меняют.'}
- ${en:'The same methods exist on `ConsoleLogger` and `CompositeLogger`. A custom logger supports `LOG.setLevel(...)` by implementing `ILevelControl`.', ru:'Те же методы есть у самих `ConsoleLogger` и `CompositeLogger`. Свой логгер поддерживает `LOG.setLevel(...)`, если реализует `ILevelControl`.'}

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
{"ts":"2026-11-11T02:30:31.412Z","level":"WARN","logger":"com.acme.PaymentService","thread":"main","message":"Charge of 42 failed, retrying","template":"Charge of {} failed, retrying","args":["42"],"caller":{"class":"com.acme.PaymentService","method":"charge","file":"PaymentService.java","line":87}}
```

- `ts` — ${en:'ISO-8601 in UTC with milliseconds, sortable as text;', ru:'ISO-8601 в UTC с миллисекундами, сортируется как текст;'} `level`, `logger` (${en:'the source class', ru:'класс-источник'}), `thread`, `message` (${en:'the formatted message', ru:'отформатированное сообщение'}).
- `template` ${en:'and', ru:'и'} `args` — ${en:'the raw message and the arguments as strings, for parameterized messages only; equal events can be grouped whatever their arguments.', ru:'исходное сообщение и аргументы строками, только для параметризованных сообщений; одинаковые события можно группировать независимо от аргументов.'}
- `caller` — ${en:'the call site, when the source class was detected;', ru:'место вызова, если класс-источник определялся автоматически;'} `exception` — `class`, `message` ${en:'and', ru:'и'} `stackTrace`, ${en:'when there is a throwable. Fields without a value are left out.', ru:'если есть исключение. Поля без значения не выводятся.'}
- ${en:'Line breaks and control characters are escaped, so one entry is always exactly one line, stack trace included.', ru:'Переводы строк и управляющие символы экранируются, поэтому одна запись — всегда ровно одна строка, вместе с трассировкой стека.'}
- `JsonLogger` ${en:'extends `ConsoleLogger`: the same levels, the same level properties and the same streams (`ERROR` to `System.err` by default). The display options do not apply.', ru:'наследует `ConsoleLogger`: те же уровни, те же свойства уровней и те же потоки (по умолчанию `ERROR` в `System.err`). Настройки отображения на него не действуют.'}
- ${en:'In a pipe only standard output reaches the next program, so `java -jar app.jar | jq` would lose every `ERROR` entry. `-Dnanolog.console.stream=out` writes them to standard output with the rest, in the order they were logged.', ru:'В конвейер попадает только стандартный вывод, поэтому `java -jar app.jar | jq` потерял бы все записи `ERROR`. `-Dnanolog.console.stream=out` пишет их в стандартный вывод вместе с остальными, в том порядке, в каком они попали в лог.'}

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
- ${en:'The message reaches SLF4J already formatted, so the backend does not see the `{}` arguments as structured data. SLF4J markers are not supported. MDC values set through `org.slf4j.MDC` are printed by backends that support MDC, Logback and Log4j 2, through their own patterns such as `%X{requestId}`; slf4j-simple and slf4j-nop silently discard them, and the JUL binding (slf4j-jdk14) stores them without printing.', ru:'Сообщение приходит в SLF4J уже отформатированным, поэтому бэкенд не видит `{}`-аргументы как структурные данные. Маркеры SLF4J не поддерживаются. Значения MDC, заданные через `org.slf4j.MDC`, печатают бэкенды с поддержкой MDC, Logback и Log4j 2, через свои шаблоны вроде `%X{requestId}`; slf4j-simple и slf4j-nop молча их отбрасывают, а привязка к JUL (slf4j-jdk14) хранит их, но не печатает.'}
- ${en:'An entry without a message is passed with an empty message, not the word `null`.', ru:'Запись без сообщения передаётся с пустым сообщением, а не со словом `null`.'}
- ${en:'An entry without a source class goes to the SLF4J `ROOT` logger.', ru:'Запись без класса-источника уходит в `ROOT`-логгер SLF4J.'}
- ${en:'Loggers are cached per source class in a `ConcurrentHashMap` and never evicted, so with dynamic class reloading the cache retains `Class` references.', ru:'Логгеры кешируются по классу-источнику в `ConcurrentHashMap` и не вытесняются, поэтому при динамической перезагрузке классов кеш удерживает ссылки на `Class`.'}

### MemoryLogger ${en:'and', ru:'и'} CompositeLogger

`MemoryLogger` ${en:'keeps entries in memory for tests. Every level is enabled, so entries are captured whatever the production configuration says, and nothing is printed.', ru:'хранит записи в памяти для тестов. Включены все уровни, поэтому записи перехватываются при любой боевой конфигурации, и ничего не печатается.'}

${widget:import(path='${examples}/CapturingLogs.java', region='capture', wrap='true')}

- `install()` ${en:'replaces the logger of `LOG` and remembers the previous one; `close()` puts it back, also when the test fails. `installForwarding()` does the same but still passes every entry to the previous logger, so the usual output stays visible.', ru:'заменяет логгер `LOG` и запоминает прежний; `close()` возвращает его, даже если тест упал. `installForwarding()` делает то же самое, но продолжает передавать каждую запись прежнему логгеру, и обычный вывод остаётся на месте.'}
- `getEntries()`, `getEntries(level)`, `getEntries(SomeClass.class)`, `getMessages()`, `contains(level, text)`, `clear()` — ${en:'snapshots and short assertions; the logger is thread-safe.', ru:'снимки и короткие проверки; логгер потокобезопасен.'}
- `assertNoneAtOrAbove(WARN)`, `assertLogged(level, text)`, `assertTemplateLogged(level, "Retry {} of {}")` — ${en:'assertions that throw `AssertionError` without JUnit or AssertJ. The message lists every captured entry with its call site and stack trace, so the failing test shows what was logged without a second run. `assertNoneAtOrAbove(WARN)` checks that the code logged no warning and no error; `assertTemplateLogged` matches the message before its arguments were substituted.', ru:'проверки, которые бросают `AssertionError` без JUnit и AssertJ. Сообщение перечисляет все перехваченные записи с местом вызова и трассировкой стека, поэтому упавший тест сразу показывает, что было в логе, без повторного запуска. `assertNoneAtOrAbove(WARN)` проверяет, что код не записал ни одного предупреждения и ни одной ошибки; `assertTemplateLogged` сравнивает сообщение до подстановки аргументов.'}
- `new MemoryLogger(capacity)` ${en:'keeps only the newest entries.', ru:'хранит только самые новые записи.'}
- ${en:'`LOG.init` is JVM-wide, so tests that install a logger must not run in parallel with each other.', ru:'`LOG.init` действует на всю JVM, поэтому тесты, устанавливающие логгер, нельзя запускать параллельно друг с другом.'}

`CompositeLogger` ${en:'sends every entry to several loggers. Each of them receives only the levels it has enabled, and a lazy message is built only if at least one of them wants it. A logger that throws is reported once with a `[NANOLOG]` line and skipped; the others still receive the entry:', ru:'отправляет каждую запись в несколько логгеров. Каждый получает только включённые у него уровни, а ленивое сообщение вычисляется, только если оно нужно хотя бы одному из них. Логгер, который бросает исключение, один раз отмечается строкой `[NANOLOG]` и пропускается, остальные запись всё равно получают:'}

${widget:import(path='${examples}/CapturingLogs.java', region='composite', wrap='true')}

## ${en:'Message formatting', ru:'Форматирование сообщений'}

${widget:import(path='${examples}/Formatting.java', region='formatting', wrap='true')}

- ${en:'`{}` is replaced by the next argument; `\{}` is a literal `{}` and consumes no argument; `\\{}` is a literal backslash followed by a substitution.', ru:'`{}` заменяется очередным аргументом; `\{}` — литеральные `{}`, аргумент не расходуется; `\\{}` — литеральный обратный слеш и подстановка.'}
- ${en:'A `Throwable` in the last position that no `{}` consumes is not an argument: it becomes the exception of the entry, and its stack trace is logged, as in SLF4J. In `LOG.error("Cause: {}", e)` the placeholder takes the exception, so only `e.toString()` is printed. An exception passed first always wins; a trailing one then stays an ordinary argument.', ru:'`Throwable` на последнем месте, который не занял ни один `{}`, не считается аргументом: он становится исключением записи, и его трассировка стека попадает в лог, как в SLF4J. В `LOG.error("Cause: {}", e)` исключение занимает плейсхолдер, поэтому печатается только `e.toString()`. Исключение, переданное первым, всегда главнее; исключение в конце тогда остаётся обычным аргументом.'}
- ${en:'Primitive arrays are rendered with', ru:'Примитивные массивы выводятся через'} `Arrays.toString`, ${en:'object arrays with', ru:'объектные через'} `Arrays.deepToString`.
- ${en:'Every `toString()` is guarded: if it throws, the value becomes `[FAILED toString()]` and nothing propagates to the caller. A throwable whose stack trace cannot be printed becomes `[FAILED printStackTrace()]`.', ru:'Каждый `toString()` защищён: если он бросает исключение, значение превращается в `[FAILED toString()]`, и наружу ничего не вылетает. Исключение, трассировку которого не удаётся напечатать, превращается в `[FAILED printStackTrace()]`.'}
- ${en:'A lazy message is guarded the same way: if its `Supplier` throws, the entry is still logged, with the message `[FAILED supplier: <exception>]`, for example `[FAILED supplier: java.lang.IllegalStateException: no report]`; the exception and the arguments of the entry are kept. The supplier runs at most once.', ru:'Ленивое сообщение защищено так же: если его `Supplier` бросает исключение, запись всё равно попадает в лог с сообщением `[FAILED supplier: <исключение>]`, например `[FAILED supplier: java.lang.IllegalStateException: no report]`; исключение и аргументы записи сохраняются. Supplier вызывается не больше одного раза.'}
- ${en:'The message object itself may be anything, not only a', ru:'Сам объект сообщения может быть любым, не только'} `String`; ${en:'it is converted with the same guarded', ru:'он преобразуется тем же защищённым'} `toString()`.
- With `nanolog.dump=auto` (`NANOLOG_DUMP=auto`) an argument whose class has no `toString()` of its own is printed field by field, as in [`LOG.dbg`](#objects-without-tostring): `LOG.info("Added {}", line)` prints `Added Line{sku="A-1", qty=2}` instead of `Added Line@1e643faf`. Collections, maps, arrays and `Optional` are walked, so a list of such objects is printed the same way. Everything with a `toString()` of its own, strings included, prints as before, without quotes, limits or redaction. The setting is off by default because it changes existing output; `JsonLogger` puts the result into `message` and `args` as text. Since 1.4.<!--en-->
- С `nanolog.dump=auto` (`NANOLOG_DUMP=auto`) аргумент, у класса которого нет собственного `toString()`, печатается поле за полем, как в [`LOG.dbg`](#объекты-без-tostring): `LOG.info("Added {}", line)` печатает `Added Line{sku="A-1", qty=2}` вместо `Added Line@1e643faf`. Коллекции, словари, массивы и `Optional` обходятся, поэтому список таких объектов печатается так же. Всё, у чего есть собственный `toString()`, включая строки, печатается как раньше, без кавычек, ограничений и скрытия секретов. По умолчанию настройка выключена, потому что меняет привычный вывод; `JsonLogger` кладёт результат в `message` и `args` как текст. Начиная с 1.4.<!--ru-->

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

${en:'A line on standard error is easy to miss among thousands; `MemoryLogger` turns it into a failing test. Strict mode hands every finding to the installed logger as well (`ILogger.onDiagnostic`), every occurrence and not only the first one of a template, so a test fails even when an earlier test met the same mismatch:', ru:'Строку в потоке ошибок легко пропустить среди тысяч других; `MemoryLogger` превращает её в упавший тест. Strict-режим передаёт каждую находку и установленному логгеру (`ILogger.onDiagnostic`), каждое повторение, а не только первое для шаблона, поэтому тест падает, даже если то же несовпадение уже встретил предыдущий тест:'}

${widget:import(path='${examples}/CapturingLogs.java', region='diagnostics', wrap='true')}

- ${en:'`getDiagnostics()` returns the findings, `assertNoDiagnostics()` fails with all of them in the message; `clear()` drops them together with the entries. `CompositeLogger` passes them to its delegates.', ru:'`getDiagnostics()` возвращает находки, `assertNoDiagnostics()` падает со всеми ними в сообщении; `clear()` удаляет их вместе с записями. `CompositeLogger` передаёт их своим делегатам.'}
- ${en:'Without strict mode there is nothing to report and `assertNoDiagnostics()` passes, so switch it on for the test run as above.', ru:'Без strict-режима сообщать не о чем, и `assertNoDiagnostics()` проходит, поэтому включите его для тестового запуска, как показано выше.'}

## ${en:'Rate limiting', ru:'Ограничение частоты'}

${en:'A retry loop, a full queue or a deprecated option can print the same line thousands of times, and the line that matters scrolls away. `LOG.once()` and `LOG.atMostEvery(...)` limit one statement:', ru:'Цикл повторов, переполненная очередь или устаревшая настройка могут напечатать одну и ту же строку тысячи раз, и важная строка уходит из виду. `LOG.once()` и `LOG.atMostEvery(...)` ограничивают одну строку кода:'}

${widget:import(path='${examples}/RateLimiting.java', region='limits', wrap='true')}

- ${en:'A statement is its call site, the file and line of the call. Two calls on one line share one limit, and so do all the callers of a helper method that logs for them.', ru:'Строка кода — это место вызова, файл и номер строки. Два вызова на одной строке делят одно ограничение, как и все вызывающие вспомогательного метода, который пишет в лог за них.'}
- ${en:'The next entry that gets through carries the number of calls skipped since the previous one: `ConsoleLogger` prints `[skipped 241]` after the message, `JsonLogger` writes `"skipped":241`, `Slf4jLogger` appends `[skipped 241]` to the message, and any logger reads `LogEntry.getSkippedCount()`.', ru:'Следующая прошедшая запись несёт число вызовов, пропущенных с предыдущей: `ConsoleLogger` печатает `[skipped 241]` после сообщения, `JsonLogger` пишет `"skipped":241`, `Slf4jLogger` дописывает `[skipped 241]` к сообщению, а любой логгер может прочитать `LogEntry.getSkippedCount()`.'}
- ${en:'The level is checked first, the limit last: a call at a disabled level is not counted, and a skipped call builds no message and formats nothing.', ru:'Сначала проверяется уровень, ограничение — последним: вызов выключенного уровня не считается, а пропущенный вызов не строит сообщение и ничего не форматирует.'}
- ${en:'`once()` and `atMostEvery(...)` offer four shapes at every level: `("... {} ...", args)`, `(() -> message)` and both with an exception first. The state of each call site takes a few bytes, without locks, and the number of call sites is bounded by the code.', ru:'`once()` и `atMostEvery(...)` дают четыре формы на каждом уровне: `("... {} ...", args)`, `(() -> message)` и обе с исключением первым аргументом. Состояние места вызова занимает несколько байт и обновляется без блокировок, а число мест вызова ограничено размером кода.'}
- ${en:'A count left at the end of a burst is printed only when the statement runs again. A period of zero or less limits nothing.', ru:'Счётчик, оставшийся после серии вызовов, печатается, только когда строка выполнится снова. Период ноль или меньше ничего не ограничивает.'}

## ${en:'Caller detection', ru:'Определение вызывающего класса'}

Когда класс не передан явно, `LOG` проходит по стеку и берёт первый кадр,<!--ru-->
который не принадлежит `LOG`, `LogEntry` или реализации `ILogger`. На Java 9+ это делает<!--ru-->
`StackWalker`, который останавливается на этом кадре; на Java 8 `LOG` читает стек `Throwable`.<!--ru-->
When no class is passed explicitly, `LOG` walks the stack and takes the first frame<!--en-->
that does not belong to `LOG`, `LogEntry` or an `ILogger` implementation. On Java 9+ a<!--en-->
`StackWalker` does it and stops at that frame; on Java 8 `LOG` reads the stack trace of a `Throwable`.<!--en-->

- ${en:'Nested, anonymous and lambda classes are attributed to their top-level enclosing class.', ru:'Вложенные, анонимные классы и лямбды относятся к своему внешнему классу верхнего уровня.'}
- ${en:'The frame found by the walk is kept as `LogEntry.getCaller()`: the exact class, method, file and line of the call, including nested classes and lambdas. It costs nothing extra, and it is `null` when the class was passed explicitly.', ru:'Найденный кадр сохраняется в `LogEntry.getCaller()`: точный класс, метод, файл и строка вызова, в том числе для вложенных классов и лямбд. Это ничего не стоит сверх обхода стека; при явно переданном классе значение равно `null`.'}
- ${en:'On Java 9+ the stack walker hands over the class of each frame, so the caller is found exactly whatever class loader it lives in. On Java 8, and wherever `StackWalker` is not available (a native image without the metadata for it), the frames of a `Throwable` carry only class names: they are resolved through the context class loader of the thread first and the class loader of `LOG` second, so the detection works when Nanolog lives in a shared loader of an application server and the caller in a web application. A frame neither loader can see or load is skipped, whatever the failure: a missing class, a linkage error such as `NoClassDefFoundError`, or `MissingReflectionRegistrationError` from GraalVM in a native image.', ru:'На Java 9+ обход стека получает сам класс каждого кадра, поэтому вызывающий класс находится точно, в каком бы загрузчике он ни был. На Java 8 и там, где `StackWalker` недоступен (native image без метаданных для него), кадры `Throwable` содержат только имена классов: они ищутся сначала контекстным загрузчиком потока, затем загрузчиком `LOG`, поэтому определение работает, когда Nanolog лежит в общем загрузчике сервера приложений, а вызывающий код — в веб-приложении. Кадр, класс которого ни один загрузчик не видит или не может загрузить, пропускается, какой бы ни была ошибка: класса нет, ошибка линковки вроде `NoClassDefFoundError` или `MissingReflectionRegistrationError` от GraalVM в native image.'}
- ${en:'The stack is walked only for a level the logger may have enabled: `LOG` first asks `ILogger.mayBeEnabled(level)`, so a level that is off for every class, such as `DEBUG` under `nanolog.level=INFO`, costs no walk. `LOG.isDebugEnabled()` follows the same rule. Once a level is on anywhere, even for one package (`nanolog.level.com.acme.db=TRACE`), every call at that level walks the stack, because only the class tells whether it is enabled. In hot paths pass the class explicitly:', ru:'Стек обходится, только если уровень может быть включён хотя бы для одного класса: `LOG` сначала спрашивает `ILogger.mayBeEnabled(level)`, поэтому уровень, выключенный для всех классов, например `DEBUG` при `nanolog.level=INFO`, обхода не стоит. `LOG.isDebugEnabled()` подчиняется тому же правилу. Если уровень включён хоть где-то, даже для одного пакета (`nanolog.level.com.acme.db=TRACE`), каждый вызов этого уровня обходит стек: включён ли он, решает класс. В горячих местах передавайте класс явно:'} `LOG.debug(MyClass.class, ...)`.
- ${en:'Any class implementing `ILogger` is skipped, so a custom logger that logs through `LOG` must pass its source class explicitly.', ru:'Любой класс, реализующий `ILogger`, пропускается, поэтому свой логгер, который сам пишет через `LOG`, должен передавать класс-источник явно.'}
- ${en:'If no frame qualifies, the entry is still logged, without a source class and a call site, as with `LOG.log(level, null, ...)`. That happens when the code runs entirely inside an `ILogger` class, or on Java 8 when the JVM records no stack traces (`-XX:-StackTraceInThrowable`). `Slf4jLogger` sends such entries to the `ROOT` logger.', ru:'Если подходящего кадра нет, запись всё равно попадает в лог, но без класса-источника и места вызова, как при `LOG.log(level, null, ...)`. Так бывает, когда код целиком выполняется внутри класса, реализующего `ILogger`, или на Java 8, когда JVM не записывает стек (`-XX:-StackTraceInThrowable`). `Slf4jLogger` отправляет такие записи в логгер `ROOT`.'}

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

${en:'`isEnabled(...)` is called before the `LogEntry` is built and before any `Supplier` is resolved, so returning `false` skips the whole cost of the message. It does not skip caller detection: for a call without an explicit class the source class is found first, so the stack has already been walked; see', ru:'`isEnabled(...)` вызывается до создания `LogEntry` и до раскрытия `Supplier`, поэтому возврат `false` избавляет от всей стоимости сообщения. От определения вызывающего класса он не избавляет: для вызова без явного класса класс-источник находится заранее, и стек к этому моменту уже обойдён; см.'} [${en:'Caller detection', ru:'Определение вызывающего класса'}](#${en:'caller-detection', ru:'определение-вызывающего-класса'}).

${en:'To spare the walk as well, override `mayBeEnabled(level)`, as the example above does: `LOG` asks it before caller detection, and `false` means that no class has the level enabled. The default `true` is always safe; `false` for a level that `isEnabled(...)` accepts for some class loses those entries. `ConsoleLogger`, `JsonLogger` and `CompositeLogger` implement it; a subclass that overrides `isEnabled(...)` but not `mayBeEnabled(...)` gets `true`.', ru:'Чтобы сэкономить и обход, переопределите `mayBeEnabled(level)`, как в примере выше: `LOG` спрашивает его до определения вызывающего класса, и `false` означает, что уровень не включён ни для одного класса. Значение по умолчанию `true` безопасно всегда; `false` для уровня, который `isEnabled(...)` пропускает хотя бы для одного класса, теряет эти записи. `ConsoleLogger`, `JsonLogger` и `CompositeLogger` его реализуют; подкласс, который переопределяет `isEnabled(...)`, но не `mayBeEnabled(...)`, получает `true`.'}

${en:'A logger that throws does not break the code that logs. The first failure of each logger class prints one line to `System.err`, for example', ru:'Логгер, который бросает исключение, не ломает код, который пишет в лог. Первый сбой каждого класса логгера печатает одну строку в `System.err`, например'} `[NANOLOG] Logger com.acme.AuditLogger threw java.io.UncheckedIOException: disk full; entries it fails to write are printed to System.err`. ${en:'Every entry the logger fails to write then goes to `System.err` in the `ConsoleLogger` format, so nothing is lost. If `isEnabled(...)` throws, the level counts as enabled.', ru:'Каждая запись, которую логгер не смог записать, уходит в `System.err` в формате `ConsoleLogger`, так что ничего не теряется. Если бросает `isEnabled(...)`, уровень считается включённым.'}

## ${en:'Thread safety and performance', ru:'Потокобезопасность и производительность'}

- ${en:'The static', ru:'Статические методы'} `LOG` ${en:'methods are safe to call from any thread as long as the installed', ru:'можно вызывать из любого потока, пока установленный'} `ILogger` ${en:'is;', ru:'потокобезопасен;'} `ConsoleLogger` ${en:'and', ru:'и'} `Slf4jLogger` ${en:'are.', ru:'таковы.'}
- ${en:'A logger installed with `LOG.init(...)` or `MemoryLogger.install()` is used by every thread from its next logging call, threads that are already running included. Levels may change at any time, through `LOG.setLevel(...)` and the other level methods or the level setters of `ConsoleLogger`. The display setters (`setShowDate`, `setDateFormat` and the others) are meant for startup, not for concurrent reconfiguration.', ru:'Логгер, установленный через `LOG.init(...)` или `MemoryLogger.install()`, используется всеми потоками начиная со следующего вызова логирования, в том числе уже запущенными. Уровни можно менять в любой момент через `LOG.setLevel(...)` и другие методы уровней или сеттеры уровней `ConsoleLogger`. Сеттеры вывода (`setShowDate`, `setDateFormat` и другие) рассчитаны на старт приложения, а не на параллельную перенастройку.'}
- ${en:'A call at a level that is off for every class costs one `mayBeEnabled` check, or one `isEnabled` check with an explicit class. A level that is on for some package or class costs a call without an explicit class one stack walk and an `isEnabled` check even where it is off, so a single rule such as `nanolog.level.com.acme.db=TRACE` makes every `trace` call without a class walk the stack. Message formatting and `Supplier` evaluation happen only for enabled levels.', ru:'Вызов уровня, выключенного для всех классов, стоит одну проверку `mayBeEnabled`, а с явным классом — одну проверку `isEnabled`. Если уровень включён для какого-то пакета или класса, вызов без явного класса стоит обхода стека и проверки `isEnabled` даже там, где уровень выключен: одно правило вроде `nanolog.level.com.acme.db=TRACE` заставляет каждый вызов `trace` без класса обходить стек. Форматирование сообщения и `Supplier` вычисляются только для включённых уровней.'}
- ${en:'Measured with JMH on JDK 17 (Ryzen 5 5600X, Windows 10): a call at a level that is off for every class takes about 2 ns without a class and 7 ns with one. The stack walk, the price of a call without a class at a level enabled for some class, takes about 2.5 µs near the top of the stack and 3 µs at 100 frames; Java 8, which has no `StackWalker`, needs about 20 µs and 60 µs. An enabled line through `ConsoleLogger` takes under 1 µs, plus the walk if the class is not given. Other JDKs and the method:', ru:'Замеры JMH на JDK 17 (Ryzen 5 5600X, Windows 10): вызов уровня, выключенного для всех классов, занимает около 2 нс без класса и 7 нс с явным классом. Обход стека — цена вызова без класса для уровня, включённого хотя бы для одного класса, — стоит около 2,5 мкс у вершины стека и 3 мкс на глубине 100 кадров; на Java 8, где нет `StackWalker`, — около 20 и 60 мкс. Включённая строка через `ConsoleLogger` занимает меньше 1 мкс плюс обход, если класс не передан. Другие JDK и методика:'} [src/jmh/RESULTS.md](src/jmh/RESULTS.md).

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

- ${en:'`LOG.dbg(expression)` prints the expression as written in the source, its value and type, and the call site, and returns the value; `LOG.dbg(a, b, ...)` prints several, `LOG.dbg()` that the line was reached. Without the source only the values are printed. `ConsoleLogger` marks the line `DBG`, `JsonLogger` adds `"dbg":true`, `LogEntry.isDbg()` tells the entry apart; `nanolog.sources` names the source directories.', ru:'`LOG.dbg(expression)` печатает выражение так, как оно записано в исходнике, его значение и тип и место вызова и возвращает значение; `LOG.dbg(a, b, ...)` печатает несколько, `LOG.dbg()` — что строка достигнута. Без исходников печатаются только значения. `ConsoleLogger` помечает строку `DBG`, `JsonLogger` добавляет `"dbg":true`, `LogEntry.isDbg()` отличает запись; `nanolog.sources` задаёт каталоги исходников.'}
- ${en:'Objects whose class has no `toString()` of its own are printed field by field instead of `Invoice@2f92e0f4`: always in `LOG.dbg`, and in `{}` arguments with `nanolog.dump=auto`. Classes of the JDK are never reflected into, so nothing needs `--add-opens` on JDK 17+; cycles, depth, items and string length are limited, and every cut says what it left out; fields named like secrets print `***`; records show their components, proxies only their class name.', ru:'Объекты, у класса которых нет собственного `toString()`, печатаются поле за полем, а не как `Invoice@2f92e0f4`: всегда в `LOG.dbg`, а в аргументах `{}` — с `nanolog.dump=auto`. В классы JDK рефлексия не заглядывает, поэтому на JDK 17+ не нужен `--add-opens`; циклы, глубина, число элементов и длина строк ограничены, и каждое ограничение сообщает, что отброшено; поля с именами секретов печатаются как `***`; записи показывают свои компоненты, прокси — только имя класса.'}
${widget:endIf}

### 1.3

- ${en:'Levels change while the application runs, thread-safely: `LOG.setLevel(level)`, `LOG.setLevel(name, level)`, `LOG.clearLevel(name)`, `LOG.resetLevels()`, and `LOG.withLevel(name, level)` for a `try` block. The level is a `LogEntryLevel` or a string such as `"OFF"`, as in the settings. `ConsoleLogger`, `JsonLogger` and `CompositeLogger` implement the new `ILevelControl`, and the level setters of `ConsoleLogger` may now be called while other threads log.', ru:'Уровни меняются во время работы приложения, потокобезопасно: `LOG.setLevel(level)`, `LOG.setLevel(name, level)`, `LOG.clearLevel(name)`, `LOG.resetLevels()` и `LOG.withLevel(name, level)` для блока `try`. Уровень задаётся как `LogEntryLevel` или строкой вроде `"OFF"`, как в настройках. `ConsoleLogger`, `JsonLogger` и `CompositeLogger` реализуют новый `ILevelControl`, а сеттеры уровней `ConsoleLogger` теперь можно вызывать, пока другие потоки пишут в лог.'}
- ${en:'`LOG.once()` and `LOG.atMostEvery(period, unit)` limit a statement to its first call or to one entry per period, keyed by the call site. The next entry that gets through carries the number of skipped calls, `LogEntry.getSkippedCount()`, which `ConsoleLogger`, `JsonLogger` and `Slf4jLogger` print.', ru:'`LOG.once()` и `LOG.atMostEvery(period, unit)` ограничивают строку кода первым вызовом или одной записью за период, по месту вызова. Следующая прошедшая запись несёт число пропущенных вызовов, `LogEntry.getSkippedCount()`, и `ConsoleLogger`, `JsonLogger` и `Slf4jLogger` его печатают.'}
- ${en:'Shorter stack traces in `ConsoleLogger` and `JsonLogger`: `nanolog.exception.exclude` collapses runs of frames from packages such as `org.junit` or `jdk.internal.reflect` into `... N frames excluded`, `nanolog.exception.maxFrames` caps the frames of each throwable. Without them the output is that of `printStackTrace`, as before.', ru:'Короткие трассировки стека в `ConsoleLogger` и `JsonLogger`: `nanolog.exception.exclude` сворачивает подряд идущие кадры пакетов вроде `org.junit` или `jdk.internal.reflect` в `... N frames excluded`, `nanolog.exception.maxFrames` ограничивает число кадров каждого исключения. Без них вывод, как и раньше, совпадает с `printStackTrace`.'}
- `MemoryLogger`: ${en:'`assertNoneAtOrAbove(level)`, `assertLogged(level, text)` and `assertTemplateLogged(level, template)` fail with an `AssertionError` that lists every captured entry, stack traces and call sites included; `getEntries(Class)` selects the entries of one source class.', ru:'`assertNoneAtOrAbove(level)`, `assertLogged(level, text)` и `assertTemplateLogged(level, template)` падают с `AssertionError`, в котором перечислены все перехваченные записи вместе с трассировками и местами вызова; `getEntries(Class)` отбирает записи одного класса-источника.'}
- ${en:'Strict mode reaches tests: every finding also goes to the installed logger through the new `ILogger.onDiagnostic`, every occurrence rather than the first one of a template, and `MemoryLogger.assertNoDiagnostics()` fails on them. `CompositeLogger` forwards them.', ru:'Strict-режим доходит до тестов: каждая находка передаётся и установленному логгеру через новый `ILogger.onDiagnostic`, каждое повторение, а не только первое для шаблона, и `MemoryLogger.assertNoDiagnostics()` падает на них. `CompositeLogger` передаёт их дальше.'}
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

${widget:alert(type='warning', text="${en:'`README.md` and `README.ru.md` are generated from `README.src.md` by the readme-generator Maven plugin. Edit `README.src.md` and run `mvn compile`; CI fails when the generated files are stale.', ru:'`README.md` и `README.ru.md` генерируются из `README.src.md` Maven-плагином readme-generator. Правьте `README.src.md` и запускайте `mvn compile`; CI падает, если сгенерированные файлы устарели.'}")}

- ${en:'Build and test:', ru:'Сборка и тесты:'} `mvn verify -Dgpg.skip=true`. ${en:'The', ru:'Фаза'} `test` ${en:'phase runs JUnit and then pitest with a 100% mutation threshold, so every change to production code needs tests that kill the corresponding mutants.', ru:'запускает JUnit, а затем pitest с порогом 100% убитых мутантов, поэтому любое изменение кода библиотеки нужно сопровождать тестами, убивающими соответствующие мутанты.'}
- ${en:'The library targets Java', ru:'Библиотека собирается под Java'} ${pom.properties.maven.compiler.release}; ${en:'do not use newer APIs.', ru:'не используйте более новые API.'}
- ${en:'The code samples in this file are imported from', ru:'Примеры кода в этом файле импортируются из'} `${examples}` ${en:'and compiled with the tests, so they cannot go stale.', ru:'и компилируются вместе с тестами, поэтому не устаревают.'}
- ${en:'Questions, bug reports and feature requests:', ru:'Вопросы, ошибки и пожелания:'} [${repo}/issues](${repo}/issues).

## ${en:'License', ru:'Лицензия'}

[Apache License 2.0](LICENSE).

---
*${en:'Generated with', ru:'Сгенерировано с помощью'} [nanolaba/readme-generator](https://github.com/nanolaba/readme-generator)*
