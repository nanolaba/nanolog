<!--@nrg.languages=en,ru-->
<!--@nrg.defaultLanguage=en-->

<!--@repo=${pom.scm.url}-->
<!--@stableVersion=${pom.properties.nanolog.stableVersion}-->

${widget:languages}

# ${en:'Security policy', ru:'Политика безопасности'}<!--toc.ignore-->

## ${en:'Supported versions', ru:'Поддерживаемые версии'}

${en:'Fixes are released for the latest version only; older versions get no backports. Upgrade before you report: the problem may be fixed already.', ru:'Исправления выпускаются только для последней версии, в старые они не переносятся. Перед отчётом обновитесь: возможно, проблема уже исправлена.'}

| ${en:'Version', ru:'Версия'} | ${en:'Supported', ru:'Поддерживается'} |
|---|---|
| `${stableVersion}` | ${en:'yes', ru:'да'} |
| ${en:'older', ru:'более старые'} | ${en:'no', ru:'нет'} |

## ${en:'Reporting a vulnerability', ru:'Как сообщить об уязвимости'}

${en:'Report it privately through GitHub:', ru:'Сообщите о ней приватно через GitHub:'} [${en:'Report a vulnerability', ru:'Report a vulnerability'}](${repo}/security/advisories/new) (${en:'the Security tab of the repository', ru:'вкладка Security репозитория'}). ${en:'Never in a public issue, pull request or discussion: a vulnerability made public before its fix puts every user at risk.', ru:'Никогда не в публичном issue, pull request или обсуждении: уязвимость, раскрытая до исправления, подвергает риску всех пользователей.'}

${en:'Include the version, the JDK, what an attacker can do, and the smallest code that shows it. English or Russian.', ru:'Укажите версию, JDK, что может сделать атакующий, и минимальный код, на котором это видно. По-английски или по-русски.'}

## ${en:'What happens next', ru:'Что происходит дальше'}

1. ${en:'The report is acknowledged in the advisory, before any other work on the project.', ru:'Отчёт подтверждается в advisory раньше любой другой работы над проектом.'}
2. ${en:'The problem is reproduced and fixed in a private fork of the advisory, and a release carries the fix.', ru:'Проблема воспроизводится и исправляется в приватном форке advisory, исправление выходит в релизе.'}
3. ${en:'The advisory is published with the release, crediting the reporter unless they prefer otherwise.', ru:'Advisory публикуется вместе с релизом и называет автора отчёта, если он не против.'}

## ${en:'In scope', ru:'Что относится к безопасности'}

${en:'Nanolog has no network code and no dependencies. What matters is what reaches the log: a secret written despite `LOG.secret` or `nanolog.redact`, a payload of `LOG.content` written in full without `nanolog.content=show`, or a value that forges a line of its own in a `[NANOLOG]` diagnostic, a JSON line or an output that escapes, or makes such a line read as something else than it holds, with bidi controls or tags.', ru:'В Nanolog нет сетевого кода и зависимостей. Важно то, что попадает в лог: секрет, записанный вопреки `LOG.secret` или `nanolog.redact`, содержимое `LOG.content`, записанное целиком без `nanolog.content=show`, или значение, которое подделывает собственную строку в диагностике `[NANOLOG]`, строке JSON или выводе с экранированием либо заставляет такую строку читаться иначе, чем она записана, через управляющие символы направления письма или теги.'}
