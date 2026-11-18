<!--@nrg.languages=en,ru-->
<!--@nrg.defaultLanguage=en-->

<!--@repo=${pom.scm.url}-->

${widget:languages}

# ${en:'Support', ru:'Поддержка'}<!--toc.ignore-->

${en:'English or Russian, whichever is easier for you.', ru:'Можно писать по-английски или по-русски, как удобнее.'}

| ${en:'You have', ru:'У вас'} | ${en:'Where', ru:'Куда'} |
|---|---|
| ${en:'a question: how to do something, why Nanolog behaves so', ru:'вопрос: как что-то сделать, почему Nanolog ведёт себя так'} | [Discussions Q&A](${repo}/discussions/categories/q-a) |
| ${en:'a bug: Nanolog printed something wrong, nothing, or failed', ru:'ошибка: Nanolog напечатал не то, ничего не напечатал или упал'} | [${en:'the bug report form', ru:'форма отчёта об ошибке'}](${repo}/issues/new?template=bug_report.yml) |
| ${en:'an idea to talk over', ru:'идея, которую хочется обсудить'} | [Discussions Ideas](${repo}/discussions/categories/ideas) ${en:'or', ru:'или'} [${en:'the feature request form', ru:'форма пожелания'}](${repo}/issues/new?template=feature_request.yml) |
| ${en:'a mistake in the documentation', ru:'ошибка в документации'} | [${en:'the documentation form', ru:'форма для документации'}](${repo}/issues/new?template=documentation.yml) |
| ${en:'a vulnerability', ru:'уязвимость'} | ${en:'privately, see', ru:'приватно, см.'} [SECURITY.md](${en:'SECURITY.md', ru:'SECURITY.ru.md'}) |

## ${en:'What helps to answer', ru:'Что помогает ответить'}

- ${en:'The Nanolog version and the JDK, `java -version` in one line.', ru:'Версия Nanolog и JDK, `java -version` одной строкой.'}
- ${en:'The smallest code that shows the problem, with its settings: the `-D` flags, the environment variables, `nanolog.properties`.', ru:'Минимальный код, на котором видна проблема, с его настройками: флаги `-D`, переменные окружения, `nanolog.properties`.'}
- ${en:'The lines that start with `[NANOLOG]` on `System.err`: Nanolog reports its own problems there.', ru:'Строки, начинающиеся с `[NANOLOG]`, в `System.err`: там Nanolog сообщает о собственных проблемах.'}
- ${en:'The logger in use (console, JSON, file, SLF4J and its backend), and whether a coding agent ran the program.', ru:'Какой логгер используется (консоль, JSON, файл, SLF4J и его бэкенд) и запускал ли программу ИИ-агент.'}

## ${en:'How soon', ru:'Когда ждать ответа'}

${en:'Nanolog is maintained by one person, so there is no guaranteed response time. Reports of vulnerabilities are answered first, then bugs, then questions and ideas. A question asked as an issue is answered there, not closed as the wrong place.', ru:'Nanolog поддерживает один человек, поэтому гарантированного срока ответа нет. Сначала разбираются отчёты об уязвимостях, затем ошибки, затем вопросы и идеи. На вопрос, заданный в issue, ответят там же, а не закроют как «не туда».'}
