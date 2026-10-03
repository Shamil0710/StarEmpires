package com.spacesim.controllers;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.economy.EconomicLedger;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static com.spacesim.controllers.TradeTransactionPolicy.Direction.*;

class PhysicalCargoSettlementTest {
    @Test void buyAndSellConserveConsiderationAndCustomsWithoutLegacyInventory() {
        var ledger = new EconomicLedger(); var treasury = new WalletComponent(100);
        var controller = new TradeController(ledger, (s,p,d,v) ->
                new TradeTransactionPolicy.Charge(v / 10, treasury, "treasury", "customs"));
        var station = entity("station", 1000); var pilot = entity("pilot", 1000);
        var transfers = new AtomicInteger();
        assertTrue(controller.settlePhysicalCargo(station, pilot, BUY_FROM_STATION, 100, "load", () -> { transfers.incrementAndGet(); return true; }));
        assertEquals(890, balance(pilot)); assertEquals(1100, balance(station)); assertEquals(110, treasury.getBalanceMilliCredits());
        assertTrue(controller.settlePhysicalCargo(station, pilot, SELL_TO_STATION, 100, "unload", () -> { transfers.incrementAndGet(); return true; }));
        assertEquals(980, balance(pilot)); assertEquals(1000, balance(station)); assertEquals(120, treasury.getBalanceMilliCredits());
        assertEquals(2100, balance(pilot) + balance(station) + treasury.getBalanceMilliCredits());
        assertEquals(2, transfers.get()); assertEquals(4, ledger.size());
    }

    @Test void insufficientFundsAndReceivingOverflowRejectBeforeCargoCallback() {
        var ledger = new EconomicLedger(); var controller = new TradeController(ledger);
        var station = entity("station", Long.MAX_VALUE); var pilot = entity("pilot", 50);
        var transfers = new AtomicInteger();
        assertFalse(controller.settlePhysicalCargo(station, pilot, BUY_FROM_STATION, 100, "load", () -> { transfers.incrementAndGet(); return true; }));
        assertFalse(controller.settlePhysicalCargo(station, pilot, BUY_FROM_STATION, 10, "load", () -> { transfers.incrementAndGet(); return true; }));
        assertEquals(0, transfers.get()); assertEquals(50, balance(pilot)); assertEquals(Long.MAX_VALUE, balance(station)); assertEquals(0, ledger.size());
    }

    @Test void rejectedPhysicalTransferPreservesEveryWalletAndLedger() {
        var ledger = new EconomicLedger(); var treasury = new WalletComponent(100);
        var controller = new TradeController(ledger, (s,p,d,v) ->
                new TradeTransactionPolicy.Charge(10, treasury, "treasury", "customs"));
        var station = entity("station", 1000); var pilot = entity("pilot", 1000);
        assertFalse(controller.settlePhysicalCargo(station, pilot, BUY_FROM_STATION, 100, "load", () -> false));
        assertEquals(1000, balance(pilot)); assertEquals(1000, balance(station)); assertEquals(100, treasury.getBalanceMilliCredits()); assertEquals(0, ledger.size());
    }
    private static Entity entity(String name, long balance) {
        return new Entity().add(new IdentityComponent(name, IdentityComponent.Kind.STATION)).add(new WalletComponent(balance));
    }
    private static long balance(Entity entity) { return entity.getComponent(WalletComponent.class).getBalanceMilliCredits(); }
}
