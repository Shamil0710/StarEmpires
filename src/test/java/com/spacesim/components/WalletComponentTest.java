package com.spacesim.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletComponentTest {
    @Test
    void переводСохраняетОбщуюСуммуИДелаетсяАтомарно() {
        WalletComponent source = new WalletComponent(10_000L);
        WalletComponent target = new WalletComponent(2_000L);

        assertTrue(source.transferTo(target, 3_500L));
        assertEquals(6_500L, source.getBalanceMilliCredits());
        assertEquals(5_500L, target.getBalanceMilliCredits());
        assertEquals(12_000L,
                source.getBalanceMilliCredits() + target.getBalanceMilliCredits());

        assertFalse(source.transferTo(target, 100_000L));
        assertEquals(6_500L, source.getBalanceMilliCredits());
        assertEquals(5_500L, target.getBalanceMilliCredits());
    }

    @Test
    void переводОтклоняетТотЖеКошелёкИНекорректнуюСумму() {
        WalletComponent wallet = new WalletComponent(100L);
        assertFalse(wallet.transferTo(wallet, 1L));
        assertFalse(wallet.transferTo(null, 1L));
        assertFalse(wallet.transferTo(new WalletComponent(), 0L));
        assertFalse(wallet.transferTo(new WalletComponent(), -1L));
        assertEquals(100L, wallet.getBalanceMilliCredits());
    }

    @Test
    void переполнениеПолучателяНеМеняетНиОдинБаланс() {
        WalletComponent source = new WalletComponent(10L);
        WalletComponent target = new WalletComponent(Long.MAX_VALUE - 5L);

        assertFalse(source.transferTo(target, 10L));
        assertEquals(10L, source.getBalanceMilliCredits());
        assertEquals(Long.MAX_VALUE - 5L, target.getBalanceMilliCredits());
    }

    @Test
    void явныеSourceSinkОперацииТожеПроверяютГраницы() {
        WalletComponent wallet = new WalletComponent(100L);
        assertTrue(wallet.creditFromSource(50L));
        assertEquals(150L, wallet.getBalanceMilliCredits());
        assertTrue(wallet.debitToSink(20L));
        assertEquals(130L, wallet.getBalanceMilliCredits());
        assertFalse(wallet.debitToSink(131L));
        assertFalse(wallet.creditFromSource(0L));
    }

    @Test
    void конструкторОтклоняетОтрицательныйБаланс() {
        assertThrows(IllegalArgumentException.class, () -> new WalletComponent(-1L));
    }
}
