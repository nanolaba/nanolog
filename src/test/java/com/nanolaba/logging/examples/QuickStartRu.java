package com.nanolaba.logging.examples;

// nrg:begin:quickstart
import com.nanolaba.logging.LOG;

public class QuickStartRu {

    public static void main(String[] args) {
        // Поле логгера не нужно: класс-источник определяется автоматически
        LOG.info("Приложение запущено");

        // Подстановка {} в стиле SLF4J
        LOG.debug("Загружено {} элементов за {} мс", 42, 17);

        // Ленивое сообщение: лямбда выполнится, только если уровень DEBUG включён
        LOG.debug(() -> "Дорогой отчёт: " + buildReport());

        // Класс-источник можно указать и явно
        LOG.warn(QuickStartRu.class, "Мало места на диске");

        String input = "not a number";
        try {
            Integer.parseInt(input);
        } catch (NumberFormatException e) {
            // Исключение и параметризованное сообщение; трассировка стека печатается после сообщения
            LOG.error(e, "Не удалось разобрать \"{}\"", input);
        }
    }

    private static String buildReport() {
        return "...";
    }
}
// nrg:end:quickstart
