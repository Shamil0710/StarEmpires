package com.spacesim.components;

import com.badlogic.ashley.core.Component;

/**
 * Authoritative денежный баланс экономического участника в целых milli-credits.
 *
 * <p>Компонент не допускает отрицательного баланса и предоставляет атомарный перевод между двумя
 * кошельками. Один кредит равен {@link com.spacesim.economy.Money#MILLI_CREDITS_PER_CREDIT}
 * внутренним единицам.</p>
 */
public final class WalletComponent implements Component {
    private long balanceMilliCredits;

    /** Создаёт пустой кошелёк. */
    public WalletComponent() {
        this(0L);
    }

    /**
     * Создаёт кошелёк с заданным начальным балансом.
     *
     * @param initialBalanceMilliCredits неотрицательный баланс в milli-credits
     * @throws IllegalArgumentException если баланс отрицателен
     */
    public WalletComponent(long initialBalanceMilliCredits) {
        if (initialBalanceMilliCredits < 0L) {
            throw new IllegalArgumentException("Начальный баланс не может быть отрицательным");
        }
        this.balanceMilliCredits = initialBalanceMilliCredits;
    }

    /** @return текущий неотрицательный баланс в milli-credits */
    public long getBalanceMilliCredits() {
        return balanceMilliCredits;
    }

    /**
     * Проверяет возможность списать сумму без ухода в отрицательный баланс.
     *
     * @param amountMilliCredits строго положительная сумма
     * @return {@code true}, если сумма положительна и доступна полностью
     */
    public boolean canDebit(long amountMilliCredits) {
        return amountMilliCredits > 0L && balanceMilliCredits >= amountMilliCredits;
    }

    /**
     * Проверяет возможность зачислить сумму без переполнения {@code long}.
     *
     * @param amountMilliCredits строго положительная сумма
     * @return {@code true}, если сумма положительна и итоговый баланс представим
     */
    public boolean canCredit(long amountMilliCredits) {
        return amountMilliCredits > 0L && balanceMilliCredits <= Long.MAX_VALUE - amountMilliCredits;
    }

    /**
     * Атомарно переводит деньги из этого кошелька в другой.
     *
     * <p>До изменения обоих балансов проверяются достаточность средств, переполнение получателя и
     * различие кошельков. При отказе ни один баланс не изменяется.</p>
     *
     * @param target получатель
     * @param amountMilliCredits строго положительная сумма перевода
     * @return {@code true}, если перевод выполнен полностью
     */
    public boolean transferTo(WalletComponent target, long amountMilliCredits) {
        if (target == null
                || target == this
                || !canDebit(amountMilliCredits)
                || !target.canCredit(amountMilliCredits)) {
            return false;
        }
        balanceMilliCredits -= amountMilliCredits;
        target.balanceMilliCredits += amountMilliCredits;
        return true;
    }

    /**
     * Явно создаёт деньги в кошельке для систем, объявленных денежным source.
     * Обычная торговля не должна вызывать этот метод.
     *
     * @param amountMilliCredits строго положительная сумма
     * @return {@code true}, если зачисление выполнено без переполнения
     */
    public boolean creditFromSource(long amountMilliCredits) {
        if (!canCredit(amountMilliCredits)) {
            return false;
        }
        balanceMilliCredits += amountMilliCredits;
        return true;
    }

    /**
     * Явно уничтожает деньги для систем, объявленных денежным sink.
     * Обычная торговля не должна вызывать этот метод.
     *
     * @param amountMilliCredits строго положительная сумма
     * @return {@code true}, если списание выполнено полностью
     */
    public boolean debitToSink(long amountMilliCredits) {
        if (!canDebit(amountMilliCredits)) {
            return false;
        }
        balanceMilliCredits -= amountMilliCredits;
        return true;
    }
}
