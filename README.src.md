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

${widget:todo}

#### ConsoleLogger

${widget:todo}

#### Slf4jLogger

${widget:todo}

#### ${en:'Creating a custom logger implementation', ru:'Написание собственной реализации логгера'}

${widget:todo}

## ${en:'Feedback', ru:'Обратная связь'}

${widget:todo}

---
*${en:'Last updated:', ru:'Дата последнего обновления:'} ${widget:date(pattern= 'dd.MM.yyyy')}*