package com.spacesim.economy;

/**
 * Преобразования между пользовательскими кредитами и целочисленной денежной единицей симуляции.
 *
 * <p>Authoritative баланс хранится в тысячных долях кредита: один кредит равен
 * {@value #MILLI_CREDITS_PER_CREDIT} milli-credits. Рыночные цены пока остаются {@code float}, но
 * каждая исполняемая сумма сделки один раз округляется до целого milli-credit методом
 * {@link #tradeValue(float, int)}. После этого движение денег выполняется только целыми
 * {@code long}.</p>
 */
public final class Money {
    /** Число внутренних денежных единиц в одном отображаемом кредите. */
    public static final long MILLI_CREDITS_PER_CREDIT = 1_000L;

    private Money() {
        throw new AssertionError("Money — статический utility-класс");
    }

    /**
     * Переводит отображаемые кредиты во внутренние milli-credits с округлением до ближайшей единицы.
     *
     * @param credits конечное неотрицательное количество кредитов
     * @return неотрицательный баланс в milli-credits
     * @throws IllegalArgumentException если значение отрицательно, неконечно или не помещается в
     *                                  диапазон {@code long}
     */
    public static long fromCredits(double credits) {
        if (!Double.isFinite(credits) || credits < 0d) {
            throw new IllegalArgumentException("Количество кредитов должно быть конечным и неотрицательным");
        }
        double milliCredits = credits * MILLI_CREDITS_PER_CREDIT;
        if (!Double.isFinite(milliCredits) || milliCredits > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Денежное значение не помещается в диапазон long");
        }
        return Math.round(milliCredits);
    }

    /**
     * Переводит внутренние milli-credits в отображаемые кредиты.
     *
     * @param milliCredits неотрицательное внутреннее денежное значение
     * @return количество кредитов
     * @throws IllegalArgumentException если внутреннее значение отрицательно
     */
    public static double toCredits(long milliCredits) {
        if (milliCredits < 0L) {
            throw new IllegalArgumentException("Денежный баланс не может быть отрицательным");
        }
        return milliCredits / (double) MILLI_CREDITS_PER_CREDIT;
    }

    /**
     * Рассчитывает целочисленную сумму сделки для нескольких единиц товара.
     *
     * <p>Округление применяется к полной партии, а не отдельно к каждой единице. Строго
     * положительная цена и количество должны давать хотя бы один milli-credit; слишком малая сумма
     * считается неисполняемой, чтобы товар не мог передаваться бесплатно из-за округления.</p>
     *
     * @param unitPriceCredits конечная строго положительная цена одной единицы в кредитах
     * @param amount строго положительное количество товара
     * @return строго положительная стоимость партии в milli-credits
     * @throws IllegalArgumentException если цена/количество некорректны, итог округляется в ноль или
     *                                  выходит за диапазон {@code long}
     */
    public static long tradeValue(float unitPriceCredits, int amount) {
        if (!Float.isFinite(unitPriceCredits) || unitPriceCredits <= 0f || amount <= 0) {
            throw new IllegalArgumentException("Цена и количество сделки должны быть положительными");
        }
        double milliCredits = (double) unitPriceCredits * amount * MILLI_CREDITS_PER_CREDIT;
        if (!Double.isFinite(milliCredits) || milliCredits > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Стоимость сделки не помещается в диапазон long");
        }
        long rounded = Math.round(milliCredits);
        if (rounded <= 0L) {
            throw new IllegalArgumentException("Стоимость сделки меньше минимальной денежной единицы");
        }
        return rounded;
    }

    /**
     * Находит наибольшее количество товара до заданного лимита, которое можно оплатить балансом.
     *
     * <p>Используется бинарный поиск по той же полной формуле округления, что и
     * {@link #tradeValue(float, int)}, поэтому планировщик и фактическая транзакция не расходятся на
     * дробных ценах.</p>
     *
     * @param balanceMilliCredits доступный неотрицательный баланс
     * @param unitPriceCredits конечная строго положительная цена единицы товара
     * @param maximumAmount неотрицательная верхняя граница количества
     * @return максимальное исполнимое количество в диапазоне от нуля до {@code maximumAmount}
     * @throws IllegalArgumentException если баланс, цена или верхняя граница некорректны
     */
    public static int maximumAffordable(long balanceMilliCredits, float unitPriceCredits, int maximumAmount) {
        if (balanceMilliCredits < 0L
                || !Float.isFinite(unitPriceCredits)
                || unitPriceCredits <= 0f
                || maximumAmount < 0) {
            throw new IllegalArgumentException("Некорректные параметры расчёта доступного количества");
        }
        if (balanceMilliCredits == 0L || maximumAmount == 0) {
            return 0;
        }

        int low = 0;
        int high = maximumAmount;
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (isAffordable(balanceMilliCredits, unitPriceCredits, middle)) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private static boolean isAffordable(long balanceMilliCredits, float unitPriceCredits, int amount) {
        try {
            return tradeValue(unitPriceCredits, amount) <= balanceMilliCredits;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
