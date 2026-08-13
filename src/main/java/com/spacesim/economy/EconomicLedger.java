package com.spacesim.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Последовательный журнал экономических движений игровой сессии. */
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

    /** @param state сохранённый ledger */
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
     * @param buyer покупатель
     * @param seller продавец
     * @param itemId товар
     * @param itemAmount количество
     * @param moneyMilliCredits сумма
     * @return запись
     */
    public EconomicTransaction recordTrade(String buyer, String seller, int itemId, long itemAmount, long moneyMilliCredits) {
        requireName(buyer, "Покупатель");
        requireName(seller, "Продавец");
        if (itemId < 0 || itemAmount <= 0L || moneyMilliCredits <= 0L) {
            throw new IllegalArgumentException("Торговая запись должна содержать положительный товар и сумму");
        }
        return append(new EconomicTransaction(nextSequenceValue(), EconomicTransaction.Type.TRADE,
                buyer, seller, itemId, itemAmount, moneyMilliCredits, "trade"));
    }

    /**
     * @param source плательщик
     * @param destination получатель
     * @param amountMilliCredits сумма
     * @param reason причина
     * @return запись
     */
    public EconomicTransaction recordMoneyTransfer(String source, String destination, long amountMilliCredits, String reason) {
        requireName(source, "Источник transfer");
        requireName(destination, "Получатель transfer");
        requirePositive(amountMilliCredits, "Сумма денежного transfer");
        return append(new EconomicTransaction(nextSequenceValue(), EconomicTransaction.Type.MONEY_TRANSFER,
                source, destination, -1, 0L, amountMilliCredits, requireReason(reason)));
    }

    /**
     * @param destination получатель
     * @param amountMilliCredits сумма
     * @param reason причина
     * @return запись
     */
    public EconomicTransaction recordMoneySource(String destination, long amountMilliCredits, String reason) {
        requireName(destination, "Получатель");
        requirePositive(amountMilliCredits, "Сумма денежного source");
        return append(new EconomicTransaction(nextSequenceValue(), EconomicTransaction.Type.MONEY_SOURCE,
                "", destination, -1, 0L, amountMilliCredits, requireReason(reason)));
    }

    /**
     * @param source источник
     * @param amountMilliCredits сумма
     * @param reason причина
     * @return запись
     */
    public EconomicTransaction recordMoneySink(String source, long amountMilliCredits, String reason) {
        requireName(source, "Источник");
        requirePositive(amountMilliCredits, "Сумма денежного sink");
        return append(new EconomicTransaction(nextSequenceValue(), EconomicTransaction.Type.MONEY_SINK,
                source, "", -1, 0L, amountMilliCredits, requireReason(reason)));
    }

    /**
     * @param destination получатель
     * @param itemId товар
     * @param itemAmount количество
     * @param reason причина
     * @return запись
     */
    public EconomicTransaction recordResourceSource(String destination, int itemId, long itemAmount, String reason) {
        requireName(destination, "Получатель ресурса");
        requireItem(itemId, itemAmount);
        return append(new EconomicTransaction(nextSequenceValue(), EconomicTransaction.Type.RESOURCE_SOURCE,
                "", destination, itemId, itemAmount, 0L, requireReason(reason)));
    }

    /**
     * @param source источник
     * @param itemId товар
     * @param itemAmount количество
     * @param reason причина
     * @return запись
     */
    public EconomicTransaction recordResourceSink(String source, int itemId, long itemAmount, String reason) {
        requireName(source, "Источник ресурса");
        requireItem(itemId, itemAmount);
        return append(new EconomicTransaction(nextSequenceValue(), EconomicTransaction.Type.RESOURCE_SINK,
                source, "", itemId, itemAmount, 0L, requireReason(reason)));
    }

    /**
     * Фиксирует физический transfer товара без создания или уничтожения ресурса.
     *
     * @param source источник
     * @param destination получатель
     * @param itemId товар
     * @param itemAmount количество
     * @param reason причина
     * @return запись
     */
    public EconomicTransaction recordResourceTransfer(
            String source, String destination, int itemId, long itemAmount, String reason) {
        requireName(source, "Источник resource transfer");
        requireName(destination, "Получатель resource transfer");
        requireItem(itemId, itemAmount);
        return append(new EconomicTransaction(nextSequenceValue(), EconomicTransaction.Type.RESOURCE_TRANSFER,
                source, destination, itemId, itemAmount, 0L, requireReason(reason)));
    }

    /**
     * @param actor производственная сущность
     * @param reason рецепт
     * @return запись
     */
    public EconomicTransaction recordResourceTransform(String actor, String reason) {
        requireName(actor, "Производственная сущность");
        return append(new EconomicTransaction(nextSequenceValue(), EconomicTransaction.Type.RESOURCE_TRANSFORM,
                actor, actor, -1, 0L, 0L, requireReason(reason)));
    }

    /** @return живое неизменяемое представление всех записей */
    public List<EconomicTransaction> getEntries() {
        return entriesView;
    }

    /** @return число записей */
    public int size() {
        return entries.size();
    }

    /** Удаляет диагностические записи без изменения authoritative state. */
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
