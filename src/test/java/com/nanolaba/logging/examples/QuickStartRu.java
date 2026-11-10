package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;

public class QuickStartRu {

    // nrg:begin:quickstart
    public static void main(String[] args) {
        // Поле логгера не нужно: класс-источник определяется автоматически
        LOG.info("Приложение запущено");

        // Подстановка {} в стиле SLF4J
        LOG.debug("Загружено {} элементов за {} мс", 42, 17);

        // Ленивое сообщение: лямбда выполнится, только если уровень DEBUG включён
        LOG.debug(() -> "Дорогой отчёт: " + buildReport());

        // Класс-источник можно указать и явно
        LOG.warn(QuickStartRu.class, "Мало места на диске");

        try {
            Integer.parseInt("not a number");
        } catch (NumberFormatException e) {
            // Трассировка стека печатается после сообщения
            LOG.error(e, "Не удалось разобрать входные данные");
        }
    }
    // nrg:end:quickstart

    private static String buildReport() {
        return "...";
    }
}
