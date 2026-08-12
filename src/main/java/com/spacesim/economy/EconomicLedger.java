package com.spacesim.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Последовательный журнал экономических движений игровой сессии.
 *
 * <p>Ledger не меняет кошельки или склады сам: authoritative systems сначала успешно выполняют
 * операцию, затем фиксируют её здесь. Журнал и следующий sequence входят в {@link State}, поэтому
 * диагностика после save/load остаётся одной непрерывной последовательностью.</p>
 */
public final class EconomicLedger {
    /**
     * Сериализуемый snapshot журнала.
     *
     * @param nextSequence sequence, который будет выдан следующей записи
     * @param entries накопленные записи в исходном порядке
     */
    public record State(long nextSequence, List<EconomicTransaction> entries) {
        /**
         * Проверяет монотонность и копирует список.
         *
         * <p>После диагностического {@link EconomicLedger#clear()} список может быть пустым либо
         * начинаться не с sequence {@code 1}; {@code nextSequence} при этом намеренно не
         * сбрасывается. Поэтому snapshot требует только непрерывность сохранённого хвоста и точное
         * продолжение его последней записи.</p>
         *
         * @param nextSequence sequence следующей записи
         * @param entries накопленные записи
         */
        public State {
            if (nextSequence <= 0L) {
                throw new IllegalArgumentException("Следующий sequence ledger должен быть положительным");
            }
            entries = List.copyOf(Objects.requireNonNull(entries, "Записи ledger не заданы"));
            if (!entries.isEmpty()) {
                long expected = entries.get(0).sequence();
                if (expected <= 0L) {
                    throw new IllegalArgumentException("Sequence ledger должен быть положительным");
                }
                for (EconomicTransaction entry : entries) {
                    if (entry.sequence() != expected) {
                        throw new IllegalArgumentException("Ledger должен содержать непрерывную sequence");
                    }
                    if (expected == Long.MAX_VALUE) {
                        throw new IllegalArgumentException("Последняя sequence ledger не оставляет следующего значения");
                    }
                    expected++;
                }
                if (nextSequence != expected) {
                    throw new IllegalArgumentException("Следующий sequence не продолжает ledger");
                }
            }
        }
    }

    private final List<EconomicTransaction> entries = new ArrayList<>();
    private final List<EconomicTransaction> entriesView = Collections.unmodifiableList(entries);
    private long nextSequence = 1L;

    /** Создаёт пустой журнал. */
    public EconomicLedger() {
    }

    /**
     * Восстанавливает журнал и его sequence из сохранённого состояния.
     *
     * @param state сохранённый ledger
     * @throws NullPointerException если state не задан
     */
    public EconomicLedger(State state) {
        State checked = Objects.requireNonNull(state, "Состояние EconomicLedger не задано");
        entries.addAll(checked.entries());
        nextSequence = checked.nextSequence();
    }

    /** @return immutable снимок записей и следующего sequence */
    public State snapshotState() {
        return new State(nextSequence, entries);
    }

    /**
     * Фиксирует успешно завершённую торговую операцию.
     *
     * @param buyer непустое диагностическое имя покупателя
     * @param seller непустое диагностическое имя продавца
     * @param itemId неотрицательный идентификатор товара
     * @param itemAmount строго положительное количество товара
     * @param moneyMilliCredits строго положительная сумма transfer в milli-credits
     * @return созданная запись
     */
    public EconomicTransaction recordTrade(
            String buyer,
            String seller,
            int itemId,
            long itemAmount,
            long moneyMilliCredits) {
        requireName(buyer, "Покупатель");
        requireName(seller, "Продавец");
        if (itemId < 0 || itemAmount <= 0L || moneyMilliCredits <= 0L) {
            throw new IllegalArgumentException("Торговая запись должна содержать положительный товар и сумму");
        }
        return append(new EconomicTransaction(
                nextSequenceValue(),
                EconomicTransaction.Type.TRADE,
                buyer,
                seller,
                itemId,
                itemAmount,
                moneyMilliCredits,
                "trade"));
    }

    /**
     * Фиксирует обычный денежный transfer между существующими экономическими участниками.
     *
     * <p>Метод вызывается только после успешного authoritative изменения обоих балансов и не
     * обозначает денежный source/sink. Он нужен world-level policy, налогам и другим переводам,
     * которые не сопровождаются движением товара.</p>
     *
     * @param source непустое диагностическое имя плательщика
     * @param destination непустое диагностическое имя получателя
     * @param amountMilliCredits строго положительная сумма transfer
     * @param reason непустая причина перевода
     * @return созданная запись
     */
    public EconomicTransaction recordMoneyTransfer(
            String source,
            String destination,
            long amountMilliCredits,
            String reason) {
        requireName(source, "Источник transfer");
        requireName(destination, "Получатель transfer");
        requirePositive(amountMilliCredits, "Сумма денежного transfer");
        return append(new EconomicTransaction(
                nextSequenceValue(),
                EconomicTransaction.Type.MONEY_TRANSFER,
                source,
                destination,
                -1,
                0L,
                amountMilliCredits,
                requireReason(reason)));
    }

    /**
     * Фиксирует явное создание денег.
     *
     * @param destination непустое имя получателя
     * @param amountMilliCredits строго положительная созданная сумма
     * @param reason непустая причина source-операции
     * @return созданная запись
     */
    public EconomicTransaction recordMoneySource(
            String destination,
            long amountMilliCredits,
            String reason) {
        requireName(destination, "Получатель");
        requirePositive(amountMilliCredits, "Сумма денежного source");
        return append(new EconomicTransaction(
                nextSequenceValue(),
                EconomicTransaction.Type.MONEY_SOURCE,
                "",
                destination,
                -1,
                0L,
                amountMilliCredits,
                requireReason(reason)));
    }

    /**
     * Фиксирует явное уничтожение денег.
     *
     * @param source непустое имя источника
     * @param amountMilliCredits строго положительная уничтоженная сумма
     * @param reason непустая причина sink-операции
     * @return созданная запись
     */
    public EconomicTransaction recordMoneySink(
            String source,
            long amountMilliCredits,
            String reason) {
        requireName(source, "Источник");
        requirePositive(amountMilliCredits, "Сумма денежного sink");
        return append(new EconomicTransaction(
                nextSequenceValue(),
                EconomicTransaction.Type.MONEY_SINK,
                source,
                "",
                -1,
                0L,
                amountMilliCredits,
                requireReason(reason)));
    }

    /**
     * Фиксирует появление физического ресурса из внешнего для товарного пула источника.
     *
     * @param destination непустое имя получателя
     * @param itemId неотрицательный идентификатор товара
     * @param itemAmount строго положительное количество появившегося товара
     * @param reason непустая причина появления ресурса
     * @return созданная запись
     */
    public EconomicTransaction recordResourceSource(
            String destination,
            int itemId,
            long itemAmount,
            String reason) {
        requireName(destination, "Получатель ресурса");
        requireItem(itemId, itemAmount);
        return append(new EconomicTransaction(
                nextSequenceValue(),
                EconomicTransaction.Type.RESOURCE_SOURCE,
                "",
                destination,
                itemId,
                itemAmount,
                0L,
                requireReason(reason)));
    }

    /**
     * Фиксирует исчезновение физического ресурса из товарного пула.
     *
     * @param source непустое имя источника
     * @param itemId неотрицательный идентификатор товара
     * @param itemAmount строго положительное количество исчезнувшего товара
     * @param reason непустая причина потребления
     * @return созданная запись
     */
    public EconomicTransaction recordResourceSink(
            String source,
            int itemId,
            long itemAmount,
            String reason) {
        requireName(source, "Источник ресурса");
        requireItem(itemId, itemAmount);
        return append(new EconomicTransaction(
                nextSequenceValue(),
                EconomicTransaction.Type.RESOURCE_SINK,
                source,
                "",
                itemId,
                itemAmount,
                0L,
                requireReason(reason)));
    }

    /**
     * Фиксирует производственное преобразование как диагностическое событие.
     *
     * @param actor непустое имя производственной сущности
     * @param reason непустое имя/описание рецепта
     * @return созданная запись
     */
    public EconomicTransaction recordResourceTransform(String actor, String reason) {
        requireName(actor, "Производственная сущность");
        return append(new EconomicTransaction(
                nextSequenceValue(),
                EconomicTransaction.Type.RESOURCE_TRANSFORM,
                actor,
                actor,
                -1,
                0L,
                0L,
                requireReason(reason)));
    }

    /** @return живое неизменяемое представление всех записей в порядке sequence */
    public List<EconomicTransaction> getEntries() {
        return entriesView;
    }

    /** @return число зафиксированных экономических операций */
    public int size() {
        return entries.size();
    }

    /** Удаляет все записи, не меняя authoritative economic state или sequence. */
    public void clear() {
        entries.clear();
    }

    private EconomicTransaction append(EconomicTransaction transaction) {
        entries.add(transaction);
        return transaction;
    }

    private long nextSequenceValue() {
        if (nextSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Диапазон sequence экономического журнала исчерпан");
        }
        return nextSequence++;
    }

    private void requireName(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " должен иметь непустое имя");
        }
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Экономическая операция должна иметь причину");
        }
        return reason.strip();
    }

    private void requirePositive(long value, String label) {
        if (value <= 0L) {
            throw new IllegalArgumentException(label + " должна быть положительной");
        }
    }

    private void requireItem(int itemId, long itemAmount) {
        if (itemId < 0 || itemAmount <= 0L) {
            throw new IllegalArgumentException("Ресурсная операция должна содержать допустимый товар и количество");
        }
    }
}
