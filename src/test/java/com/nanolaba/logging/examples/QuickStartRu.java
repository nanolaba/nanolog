package com.nanolaba.logging.examples;

import com.nanolaba.logging.ConsoleLogger;
import com.nanolaba.logging.LOG;
import com.nanolaba.logging.Slf4jLogger;

public class QuickStartRu {
    public static void main(String[] args) {
        try {
            // Это логгер, используемый по-умолчанию
            LOG.init(new ConsoleLogger());

            // Если вы хотите использовать SLF4J
            LOG.init(new Slf4jLogger());

            // Если вы хотите написать свой логгер используйте лямбда-функцию или реализуйте интерфейс ILogger
            LOG.init(entry -> System.err.println(entry.getLevel() + " - " +
                    entry.getSourceClass() + " - " +
                    entry.getFormattedMessage()));

            LOG.debug("Привет! Статическая переменная логгера не нужна");
            LOG.info(String.class, "Но можно явно указать к какому классу должно относиться логирование");
            LOG.warn("Это параметризованное сообщение: {}, {}, {} ",
                    100, "foo", new Object[]{"массив", "параметров"});

            if (LOG.isDebugEnabled()) {
                LOG.debug("Можно сделать проверку уровня логирования стандартным способом: " + hugeComputations());
            }
            LOG.debug(() -> "А можно передать лямбда-выражение: " + hugeComputations());

        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private static String hugeComputations() {
        return "OK";
    }
}
