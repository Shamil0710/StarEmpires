package com.spacesim.economy;

import org.junit.jupiter.api.Test;

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
}
