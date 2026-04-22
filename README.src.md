<!--@nrg.languages=en,ru-->
<!--@nrg.defaultLanguage=en-->

<!--@name=**Nanolog**-->
<!--@stableVersion=1.0-->
<!--@devVersion=1.1-SNAPSHOT-->

${widget:languages}

# Nanolaba logger (Nanolog)

${name} — это простой, но мощный логгер для Java-приложений, поддерживающий интеграцию с<!--ru-->
**SLF4J** и *не требующий создания статических экземпляров логгера<!--ru-->
в каждом классе приложения*, где будет осуществляться логирование.<!--ru-->
${name} — is a simple but powerful logger for Java applications, compatible with **SLF4J**, <!--en-->
that *removes the need for static logger instances* in every class requiring logging.<!--en-->

## ${en:'Overview', ru:'Краткое описание'}

${name} предоставляет усовершенствованное API для логирования приложений с набором<!--ru-->
дополнительного функционала, отсутствующего в других популярных реализациях логгеров: <!--ru-->
${name} provides an advanced API for application logging, offering additional features not found<!--en-->
in other popular logger implementations:<!--en-->

1. Автоматическое определение вызывающего класса<!--ru-->
2. Ленивая инициализация сообщений через Supplier<!--ru-->
3. Поддержка параметризованных сообщений<!--ru-->
4. Безопасное преобразование объектов в строки<!--ru-->
5. Удобное создание собственных реализаций логгеров<!--ru-->
1. Automatic caller class detection<!--en-->
2. Lazy message initialization via Supplier<!--en-->
3. Parameterized message support<!--en-->
4. Safe object-to-string conversion<!--en-->
5. Easy creation of custom logger implementations<!--en-->

${name} является очень легковесной библиотекой и может использоваться в качестве <!--ru-->
самостоятельного логгера, но также в ней доступна интеграция с **SLF4J**, а следовательно и с другими <!--ru-->
библиотеками, для которых та выступает мостом — **Logback**, **Log4j**, **JBoss logging** и др.<!--ru-->
${name} is an extremely lightweight library and can be used as a standalone logger. However, it also<!--en-->
supports integration with **SLF4J**, enabling compatibility with other logging libraries that act as its<!--en-->
bridges — such as **Logback**, **Log4j**, **JBoss Logging**, and others.<!--en-->

${name} поддерживает версии **Java 8** и выше.<!--ru-->
${name} compatible with **Java 8+**.<!--en-->

Последняя стабильная версия - **${stableVersion}**.<!--ru-->
The latest stable version of the library is **${stableVersion}**.<!--en-->

Последняя версия разработки - **${devVersion}**.<!--ru-->
The latest development version is **${devVersion}**.<!--en-->

${widget:tableOfContents(title = "${en:'Table of contents', ru:'Содержание'}", ordered = "true")}

## ${en:'Quick Start', ru:'Быстрый старт'}

```java
package com.nanolaba.logging.examples;

import com.nanolaba.logging.*;

public class QuickStart {
    public static void main(String[] args) {
        try {
            // Это логгер, используемый по-умолчанию<!--ru-->
            // This is the logger used by default<!--en-->
            LOG.init(new ConsoleLogger());

            // Если вы хотите использовать SLF4J<!--ru-->
            // If you want to use SLF4J<!--en-->
            LOG.init(new Slf4jLogger());

            // Если вы хотите написать свой логгер используйте лямбда-функцию <!--ru-->
            // или реализуйте интерфейс ILogger<!--ru-->
            // If you want to write your own logger, use a lambda function <!--en-->
            // or implement the ILogger interface.<!--en-->
            LOG.init(entry -> System.err.println(entry.getLevel() + " - " +
                    entry.getSourceClass() + " - " +
                    entry.getFormattedMessage()));

            LOG.debug("${en:'A static logger variable is not needed', ru:'Статическая переменная логгера не нужна'}");
            LOG.info(String.class, "${en:'But you can explicitly specify which class the logging should belong to', ru:'Но можно явно указать к какому классу должно относиться логирование'}");
            LOG.warn("${en:'This is a parameterized message', ru:'Это параметризованное сообщение'}: {}, {}, {} ",
                    100, "foo", new Object[]{"foo", "bar"});

            if (LOG.isDebugEnabled()) {
                LOG.debug("${en:'You can check if a log level is enabled in the standard way', ru:'Можно сделать проверку доступности уровня логирования стандартным способом'}: " +
                        hugeComputations());
            }
            LOG.debug(() -> "${en:'It''s also possible to pass a lambda expression', ru:'А можно передать лямбда-выражение'}: " +
                    hugeComputations());

        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private static String hugeComputations() {
        return "OK";
    }
}
```

## ${en:'Usage Guide', ru:'Руководство по использованию'}

### ${en:'Adding the library to the project', ru:'Добавление библиотеки к проекту'}

**Maven (pom.xml)**

```xml

<dependency>
    <groupId>com.nanolaba</groupId>
    <artifactId>nanolog</artifactId>
    <version>${stableVersion}</version>
</dependency>  
```

**Gradle (build.gradle)**

```groovy
dependencies {
    implementation 'com.nanolaba:nanolog:${stableVersion}'
}
```

**${en:'Manual download', ru:'Скачивание вручную'}**

Get the JAR from [Maven Central](https://repo1.maven.org/maven2/com/nanolaba/nanolog/${stableVersion}).<!--en-->
Add it to your project's classpath<!--en-->
Скачайте JAR из [Maven Central](https://repo1.maven.org/maven2/com/nanolaba/nanolog/${stableVersion})<!--ru-->
и добавьте его в classpath проекта.<!--ru-->

### ${en:'Using SNAPSHOT versions', ru:'Использование SNAPSHOT-версий'}

Для того чтобы использовать в своем проекте последнюю разрабатываемую версию необходимо <!--ru-->
указать адрес snapshot-репозитория, а затем добавить зависимость с -SNAPSHOT: <!--ru-->
To use the latest development version in your project, you need to specify the snapshot <!--en-->
repository URL and then add a dependency with the -SNAPSHOT suffix: <!--en-->

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
<groupId>com.nanolaba</groupId>
<artifactId>nanolog</artifactId>
<version>${devVersion}</version>
</dependency>  
```

**Gradle (build.gradle)**

```groovy
repositories {
    maven {
        url 'https://oss.sonatype.org/content/repositories/snapshots'
    }
}

dependencies {
    implementation 'com.nanolaba:nanolog:${devVersion}'
}
```

### ${en:'Setting up the logger type', ru:'Настройка типа логгера'}

${name} не требует обязательной настройки — сразу после подключения библиотеки можно начинать<!--ru-->
писать в лог, в качестве реализации по умолчанию будет использоваться `ConsoleLogger`.<!--ru-->
${name} does not require any mandatory setup — once the library is added to the project,<!--en-->
logging works out of the box and `ConsoleLogger` is used as the default implementation.<!--en-->

Выбрать другой логгер можно тремя способами:<!--ru-->
You can pick a different logger in one of three ways:<!--en-->

1. **${en:'Programmatically', ru:'Программно'}** — ${en:'pass any', ru:'передать любую'}
   `ILogger` ${en:'implementation to', ru:'реализацию в'} `LOG.init(...)`
   ${en:'at application startup', ru:'при старте приложения'}:

   ```java
   LOG.init(new Slf4jLogger());
   ```

2. **${en:'Via a system property', ru:'Через системное свойство'}** — ${en:'set the', ru:'задать'}
   `nanolog.logger` ${en:'property to the fully qualified class name of an', ru:'свойству полное имя класса, реализующего'}
   `ILogger` ${en:'implementation (the class must have a public no-arg constructor)', ru:'(у класса должен быть публичный конструктор без параметров)'}:

   ```
   -Dnanolog.logger=com.nanolaba.logging.Slf4jLogger
   ```

3. **${en:'Via a configuration file', ru:'Через файл конфигурации'}** — ${en:'place a', ru:'разместить'}
   `nanolog.properties` ${en:'file on the classpath', ru:'в classpath файл'}.
   ${en:'On the first call to', ru:'При первом обращении к'} `LOG` ${en:'its entries are copied into system properties (without overwriting values already set via', ru:'его значения копируются в системные свойства (без перезаписи тех, что уже переданы через'}
   `-D`):

   ```properties
   nanolog.logger=com.nanolaba.logging.Slf4jLogger
   ```

   ${en:'The file path can be overridden with the', ru:'Путь к файлу можно переопределить свойством'}
   `nanolog.config` ${en:'system property', ru:''}.

${en:'If instantiation of the class specified in', ru:'Если инстанциирование класса, указанного в'}
`nanolog.logger` ${en:'fails,', ru:'не удалось,'}
${name} ${en:'prints a diagnostic message with the', ru:'выведет в'} `System.err`
${en:'prefix', ru:'диагностическое сообщение с префиксом'} `[NANOLOG]`
${en:'and silently falls back to', ru:'и по-тихому переключится на'} `ConsoleLogger`.

#### ConsoleLogger

`ConsoleLogger` — ${en:'the default implementation. Writes', ru:'реализация по умолчанию. Выводит'}
`ERROR` ${en:'entries to', ru:'в'} `System.err`,
${en:'and all other levels to', ru:'остальные уровни — в'} `System.out`.

${en:'Format of a log line (components are separated by spaces):', ru:'Формат строки лога (компоненты разделены пробелами):'}

```
LEVEL dd.MM.yyyy HH:mm:ss [SourceClass] Message
```

${en:'Each component can be toggled or tuned via fluent setters:', ru:'Каждый компонент можно включать, выключать или настраивать цепочкой сеттеров:'}

```java
LOG.init(new ConsoleLogger()
        .setShowLevel(true)
        .setShowDate(true)
        .setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"))
        .setShowSource(true)
        .setShowSourceFullName(false)
        .setTraceEnabled(false)
        .setDebugEnabled(false));
```

${en:'Available options:', ru:'Доступные настройки:'}

- `showLevel` — ${en:'include the log level name', ru:'выводить имя уровня логирования'} (`TRACE`, `DEBUG`, …).
- `showDate` + `dateFormat` — ${en:'include the timestamp formatted with the given', ru:'выводить временную метку, отформатированную заданным'} `SimpleDateFormat`.
- `showSource` — ${en:'include the source class name in square brackets', ru:'выводить имя класса-источника в квадратных скобках'}.
- `showSourceFullName` — ${en:'use the fully qualified class name instead of the simple name', ru:'использовать полное имя класса вместо короткого'}.
- `traceEnabled`, `debugEnabled`, `infoEnabled`, `warnEnabled`, `errorEnabled` —<!--en-->
  per-level switches (all enabled by default). Levels that are disabled are<!--en-->
  short-circuited before the `LogEntry` is built, so `Supplier`-based lazy<!--en-->
  messages are not evaluated.<!--en-->
  переключатели по уровням (по умолчанию все включены). Отключённые уровни<!--ru-->
  отсекаются ещё до создания `LogEntry`, поэтому ленивые сообщения, переданные<!--ru-->
  через `Supplier`, не будут вычисляться.<!--ru-->

${en:'For custom rendering (coloured output, JSON, a different layout) extend', ru:'Для собственного рендеринга (цветной вывод, JSON, иная раскладка) унаследуйтесь от'}
`ConsoleLogger` ${en:'and override', ru:'и переопределите'} `createLogString(LogEntry)` / `getOutputStream(LogEntry)`.

#### Slf4jLogger

`Slf4jLogger` — ${en:'a bridge to', ru:'мост к'} **SLF4J**.
${en:'Delegates each', ru:'Каждый'} `LogEntry`
${en:'to the', ru:'передаётся в'} `org.slf4j.Logger`,
${en:'obtained via', ru:'полученный через'} `LoggerFactory.getLogger(sourceClass)`
${en:'. Loggers are cached per source class in a', ru:'. Логгеры кешируются по классу-источнику в'}
`ConcurrentHashMap`.

${en:'Level filtering is delegated to SLF4J (`isTraceEnabled()`, `isDebugEnabled()` and so on), so the actual log configuration — levels, patterns, appenders, files — is taken from whatever SLF4J backend you plug in (Logback, Log4j 2, JBoss Logging, slf4j-simple, etc.).', ru:'Фильтрация уровней делегируется SLF4J (`isTraceEnabled()`, `isDebugEnabled()` и так далее), поэтому реальная конфигурация логирования — уровни, шаблоны, аппендеры, файлы — берётся из того SLF4J-бекенда, который вы подключили (Logback, Log4j 2, JBoss Logging, slf4j-simple и т. д.).'}

${en:'The library declares', ru:'Библиотека объявляет'} `slf4j-api`
${en:'with', ru:'в области'} `provided` scope,
${en:'so if you use', ru:'поэтому при использовании'} `Slf4jLogger`
${en:'you must add', ru:'вам нужно самостоятельно добавить'} `slf4j-api`
${en:'and a concrete binding to your project yourself. Example (Maven, Logback):', ru:'и конкретный бекенд в свой проект. Пример (Maven, Logback):'}

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.12</version>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.6</version>
</dependency>
```

${en:'Initialisation:', ru:'Инициализация:'}

```java
LOG.init(new Slf4jLogger());
```

${en:'After that, writing through', ru:'После этого запись через'} `LOG.info(...)` / `LOG.debug(...)`
${en:'ends up in the SLF4J pipeline, and — because caller class detection is already done by', ru:'попадает в конвейер SLF4J, причём — поскольку определение вызывающего класса уже выполнено в'}
${name} — ${en:'SLF4J receives the real source class rather than', ru:'SLF4J получает реальный класс-источник, а не'}
`Slf4jLogger` ${en:'or', ru:'или'} `LOG` ${en:'itself.', ru:'.'}

#### ${en:'Creating a custom logger implementation', ru:'Написание собственной реализации логгера'}

${en:'A custom logger is any implementation of', ru:'Собственный логгер — это любая реализация'}
`ILogger`. ${en:'The interface has a single abstract method, so the simplest implementation is a lambda:', ru:'У интерфейса один абстрактный метод, поэтому самая простая реализация — лямбда:'}

```java
LOG.init(entry -> System.out.println(
        entry.getLevel() + " [" + entry.getSourceClass().getSimpleName() + "] "
                + entry.getFormattedMessage()));
```

${en:'A full implementation looks like this:', ru:'Полноценная реализация выглядит так:'}

```java
public class MyLogger implements ILogger {

    @Override
    public void log(LogEntry entry) {
        // ${en:'write the entry wherever you need — file, socket, queue, ...', ru:'запись в нужное место — файл, сокет, очередь, ...'}
    }

    @Override
    public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        // ${en:'return false to suppress entries of the given level for the given class', ru:'верните false, чтобы отсечь сообщения указанного уровня для данного класса'}
        return level != LogEntry.LogEntryLevel.TRACE;
    }
}
```

${en:'Useful things to know about', ru:'Полезно знать о'} `LogEntry`:

- `getLevel()` — ${en:'one of', ru:'одно из значений'} `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`.
- `getSourceClass()` — ${en:'the detected (or explicitly passed) source class', ru:'определённый (или явно переданный) класс-источник'}.
- `getThrowable()` — ${en:'the exception, if one was passed; otherwise', ru:'исключение, если оно было передано; иначе'} `null`.
- `getMessage()` — ${en:'the raw message object (already resolved from the', ru:'исходный объект-сообщение (уже разрешённый из'} `Supplier`,
  ${en:'if a lambda was used)', ru:'если использовалась лямбда)'}.
- `getArgs()` — ${en:'substitution arguments for the', ru:'аргументы подстановки для'} `{}`-${en:'placeholders', ru:'шаблонов'}.
- `getFormattedMessage()` — ${en:'the final string with', ru:'итоговая строка с подставленными'} `{}`
  ${en:'arguments substituted; arrays are rendered via', ru:'аргументами; массивы рендерятся через'} `Arrays.toString` / `Arrays.deepToString`,
  ${en:'and a throwing', ru:'а падающий'} `toString()` ${en:'yields', ru:'превращается в'} `"[FAILED toString()]"`,
  ${en:'so a broken', ru:'поэтому сломанный'} `toString()` ${en:'never breaks logging.', ru:'никогда не ломает логирование.'}

${en:'Two points worth keeping in mind:', ru:'Два момента, которые стоит держать в голове:'}

- `isEnabled(...)` ${en:'is called by', ru:'вызывается'} `LOG.log(...)` ${en:'**before** the', ru:'**до** сборки'}
  `LogEntry` ${en:'is built and before any', ru:'и до раскрытия любого'} `Supplier` ${en:'is resolved — return', ru:'— возвращайте'} `false`
  ${en:'to skip expensive message computation.', ru:'чтобы пропустить дорогое вычисление сообщения.'}
- ${en:'A custom logger class should not itself use', ru:'Пользовательский класс логгера не должен сам использовать'} `LOG`
  ${en:'for logging without passing an explicit source class — any class that implements', ru:'без явного указания класса-источника: любой класс, реализующий'}
  `ILogger` ${en:'is skipped by the automatic caller-detection, which would otherwise misattribute the log entry.', ru:'пропускается при автоматическом определении вызывающего класса, иначе запись была бы атрибутирована неверно.'}

## ${en:'Feedback', ru:'Обратная связь'}

${en:'Questions, bug reports and feature requests are welcome on the issue tracker:', ru:'Вопросы, сообщения об ошибках и пожелания принимаются в трекере задач:'}
[github.com/nanolaba/nanolog/issues](https://github.com/nanolaba/nanolog/issues).

${en:'Pull requests are also welcome. Please note that the build enforces 100% pitest mutation coverage, so any change to production code must come with tests that catch the corresponding mutants.', ru:'Pull request-ы тоже приветствуются. Обратите внимание, что сборка требует 100% покрытия мутационными тестами pitest, поэтому любое изменение кода библиотеки должно сопровождаться тестами, покрывающими соответствующие мутации.'}

---
*${en:'Last updated:', ru:'Дата последнего обновления:'} ${widget:date(pattern= 'dd.MM.yyyy')}*