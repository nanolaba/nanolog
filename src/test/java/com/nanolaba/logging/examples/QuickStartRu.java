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

        // Контекст добавляет requestId=r-17 ко всем записям внутри блока;
        // единица работы пишет одну строку в конце: исход, длительность, поля, предупреждения
        try (LOG.Context context = LOG.context("requestId", "r-17");
             LOG.Unit checkout = LOG.unit("checkout")) {
            checkout.put("orderId", "A-17");
            LOG.info("Платёж принят", LOG.kv("method", "card")); // именованное поле
            LOG.warn("Мало места на диске");
        }

        // Таймер, который возвращает значение лямбды
        String report = LOG.timed("Сборка отчёта", () -> buildReport());

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
