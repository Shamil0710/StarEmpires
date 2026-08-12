package com.spacesim.economy;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {
    @Test
    void конвертируетКредитыВЦелыеMilliCredits() {
        assertEquals(0L, Money.fromCredits(0d));
        assertEquals(1_000L, Money.fromCredits(1d));
        assertEquals(12_345L, Money.fromCredits(12.345d));
        assertEquals(12.345d, Money.toCredits(12_345L), 1e-12);
    }

    @Test
    void стоимостьПартииОкругляетсяОдинРаз() {
        assertEquals(30_000L, Money.tradeValue(10f, 3));
        assertEquals(1L, Money.tradeValue(0.0005f, 1));
        assertEquals(2L, Money.tradeValue(0.0005f, 3));
    }

    @Test
    void maximumAffordableИспользуетТуЖеФормулуПартии() {
        assertEquals(0, Money.maximumAffordable(0L, 2.5f, 100));
        assertEquals(4, Money.maximumAffordable(10_000L, 2.5f, 100));
        assertEquals(3, Money.maximumAffordable(100_000L, 2.5f, 3));
    }

    @Test
    void optimizedMaximumAffordableСовпадаетСЭталоннымБинарнымПоиском() {
        Random random = new Random(0x6B_2026L);
        for (int iteration = 0; iteration < 20_000; iteration++) {
            float price = 0.001f + random.nextFloat() * 100_000f;
            int maximumAmount = random.nextInt(100_001);
            long balance = random.nextLong(0L, 10_000_000_000_001L);

            assertEquals(
                    referenceMaximumAffordable(balance, price, maximumAmount),
                    Money.maximumAffordable(balance, price, maximumAmount),
                    () -> "balance=" + balance + ", price=" + price + ", max=" + maximumAmount);
        }

        assertEquals(
                referenceMaximumAffordable(Long.MAX_VALUE, Float.MAX_VALUE, Integer.MAX_VALUE),
                Money.maximumAffordable(Long.MAX_VALUE, Float.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE,
                Money.maximumAffordable(Long.MAX_VALUE, 0.0005f, Integer.MAX_VALUE));
    }

    @Test
    void отклоняетНекорректныеДенежныеЗначения() {
        assertThrows(IllegalArgumentException.class, () -> Money.fromCredits(-1d));
        assertThrows(IllegalArgumentException.class, () -> Money.fromCredits(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Money.toCredits(-1L));
        assertThrows(IllegalArgumentException.class, () -> Money.tradeValue(0f, 1));
        assertThrows(IllegalArgumentException.class, () -> Money.tradeValue(Float.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> Money.tradeValue(1f, 0));
        assertThrows(IllegalArgumentException.class, () -> Money.maximumAffordable(-1L, 1f, 1));
        assertThrows(IllegalArgumentException.class, () -> Money.maximumAffordable(1L, 0f, 1));
        assertThrows(IllegalArgumentException.class, () -> Money.maximumAffordable(1L, 1f, -1));
    }

    private static int referenceMaximumAffordable(long balance, float price, int maximumAmount) {
        if (balance == 0L || maximumAmount == 0) {
            return 0;
        }
        int low = 0;
        int high = maximumAmount;
        while (low < high) {
            int middle = low + (high - low) / 2 + 1;
            if (referenceAffordable(balance, price, middle)) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private static boolean referenceAffordable(long balance, float price, int amount) {
        if (amount == 0) {
            return true;
        }
        try {
            return Money.tradeValue(price, amount) <= balance;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
