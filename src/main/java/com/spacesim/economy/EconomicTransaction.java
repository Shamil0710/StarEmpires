package com.spacesim.economy;

/**
 * Неизменяемая запись одного экономического движения для диагностики и проверки инвариантов.
 *
 * <p>Транзакция различает обычные переводы и явные sources/sinks. Поля source/destination пока
 * используют устойчивые диагностические имена; persistent identity участников при необходимости
 * может быть добавлена отдельным versioned полем без изменения денежной семантики.</p>
 *
 * @param sequence монотонный положительный номер записи внутри ledger
 * @param type тип экономического движения
 * @param source имя источника либо пустая строка для внешнего source
 * @param destination имя получателя либо пустая строка для внешнего sink
 * @param itemId идентификатор товара или {@code -1}, если операция только денежная
 * @param itemAmount неотрицательное количество товара
 * @param moneyMilliCredits неотрицательная денежная сумма в milli-credits
 * @param reason непустое диагностическое объяснение операции
 */
public record EconomicTransaction(
        long sequence,
        Type type,
        String source,
        String destination,
        int itemId,
        long itemAmount,
        long moneyMilliCredits,
        String reason) {

    /** Категория движения ресурса или денег. */
    public enum Type {
        /** Обычная двусторонняя торговля: товар и деньги переходят между участниками. */
        TRADE,
        /** Обычный денежный transfer без создания/уничтожения денег. */
        MONEY_TRANSFER,
        /** Явное создание денег вне обычного transfer. */
        MONEY_SOURCE,
        /** Явное уничтожение денег вне обычного transfer. */
        MONEY_SINK,
        /** Явное появление физического ресурса, например добыча из природного источника. */
        RESOURCE_SOURCE,
        /** Явное исчезновение физического ресурса, например потребление. */
        RESOURCE_SINK,
        /** Производственное преобразование входных товаров в выходные. */
        RESOURCE_TRANSFORM
    }

    /**
     * Проверяет общие структурные инварианты записи.
     *
     * @param sequence монотонный положительный номер записи внутри ledger
     * @param type тип экономического движения
     * @param source имя источника либо пустая строка для внешнего source
     * @param destination имя получателя либо пустая строка для внешнего sink
     * @param itemId идентификатор товара или {@code -1}, если операция только денежная
     * @param itemAmount неотрицательное количество товара
     * @param moneyMilliCredits неотрицательная денежная сумма в milli-credits
     * @param reason непустое диагностическое объяснение операции
     * @throws IllegalArgumentException если номер, количество, сумма или reason некорректны
     * @throws NullPointerException если обязательные ссылки не заданы
     */
    public EconomicTransaction {
        if (sequence <= 0L) {
            throw new IllegalArgumentException("Номер экономической записи должен быть положительным");
        }
        if (type == null) {
            throw new NullPointerException("Тип экономической записи не задан");
        }
        if (source == null || destination == null) {
            throw new NullPointerException("Участники экономической записи не должны быть null");
        }
        if (itemId < -1 || itemAmount < 0L || moneyMilliCredits < 0L) {
            throw new IllegalArgumentException("Количество ресурса и денег не может быть отрицательным");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Экономическая запись должна содержать причину");
        }
        source = source.strip();
        destination = destination.strip();
        reason = reason.strip();
    }
}
