<!--@nrg.languages=en,ru-->
<!--@nrg.defaultLanguage=en-->

<!--@name=**Nanolog**-->
<!--@stableVersion=1.0-->

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

1. Автоматическое определение вызывающего класса<!--ru-->
2. Ленивая инициализация сообщений через Supplier<!--ru-->
3. Поддержка параметризованных сообщений<!--ru-->
4. Безопасное преобразование объектов в строки<!--ru-->
5. Удобное создание собственных реализаций логгеров<!--ru-->

<!--ru-->
${name} является очень легковесной библиотекой и может использоваться в качестве <!--ru-->
самостоятельного логгера, но также в ней доступна интеграция с **SLF4J**, а следовательно и с другими <!--ru-->
библиотеками, для которых та выступает мостом — **Logback**, **Log4j**, **JBoss logging** и др.<!--ru-->
<!--ru-->
Последняя стабильная версия - **${stableVersion}**.<!--ru-->

${name} provides an advanced API for application logging, offering additional features not found<!--en-->
in other popular logger implementations:<!--en-->

1. Automatic caller class detection<!--en-->
2. Lazy message initialization via Supplier<!--en-->
3. Parameterized message support<!--en-->
4. Safe object-to-string conversion<!--en-->
5. Easy creation of custom logger implementations<!--en-->

<!--en-->
${name} is an extremely lightweight library and can be used as a standalone logger. However, it also<!--en-->
supports integration with **SLF4J**, enabling compatibility with other logging libraries that act as its<!--en-->
bridges — such as **Logback**, **Log4j**, **JBoss Logging**, and others.<!--en-->
<!--en-->
The latest stable version of the program is **${stableVersion}**.<!--en-->

${widget:tableOfContents(title = "${en:'Table of contents', ru:'Содержание'}", ordered = "true")}

## ${en:'Quick Start', ru:'Быстрый старт'}

${widget:todo}

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
implementation 'com.nanolaba:nanolog:${stableVersion}'
```

**${en:'Manual download', ru:'Скачивание вручную'}**

Get the JAR from [Maven Central](https://mvnrepository.com/artifact/com.nanolaba/nanolog/${stableVersion}).<!--en-->
Add it to your project's classpath<!--en-->
Скачайте JAR из [Maven Central](https://mvnrepository.com/artifact/com.nanolaba/nanolog/${stableVersion})<!--ru-->
и добавьте его в classpath проекта.<!--ru-->

## ${en:'Feedback', ru:'Обратная связь'}

${widget:todo}

---
*${en:'Last updated:', ru:'Дата последнего обновления:'} ${widget:date(pattern= 'dd.MM.yyyy')}*