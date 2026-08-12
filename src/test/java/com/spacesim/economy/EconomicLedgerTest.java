package com.spacesim.economy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomicLedgerTest {
    @Test
    void записиПолучаютМонотонныеSequenceИСохраняютТипОперации() {
        EconomicLedger ledger = new EconomicLedger();
        EconomicTransaction trade = ledger.recordTrade("Buyer", "Seller", 2, 4L, 8_000L);
        EconomicTransaction source = ledger.recordMoneySource("Station", 1_000L, "initial-capital");
        EconomicTransaction sink = ledger.recordMoneySink("Station", 500L, "tax");
        EconomicTransaction resourceSource = ledger.recordResourceSource("Miner", 0, 3L, "mining");
        EconomicTransaction resourceSink = ledger.recordResourceSink("Colony", 2, 2L, "consumption");
        EconomicTransaction transform = ledger.recordResourceTransform("Foundry", "steel-recipe");

        assertEquals(1L, trade.sequence());
        assertEquals(6L, transform.sequence());
        assertEquals(EconomicTransaction.Type.TRADE, trade.type());
        assertEquals(EconomicTransaction.Type.MONEY_SOURCE, source.type());
        assertEquals(EconomicTransaction.Type.MONEY_SINK, sink.type());
        assertEquals(EconomicTransaction.Type.RESOURCE_SOURCE, resourceSource.type());
        assertEquals(EconomicTransaction.Type.RESOURCE_SINK, resourceSink.type());
        assertEquals(EconomicTransaction.Type.RESOURCE_TRANSFORM, transform.type());
        assertEquals(6, ledger.size());
        assertEquals(6, ledger.getEntries().size());

        ledger.clear();
        assertEquals(0, ledger.size());
        EconomicTransaction afterClear = ledger.recordMoneySource("Station", 1L, "restart-observation");
        assertEquals(7L, afterClear.sequence());
    }

    @Test
    void snapshotRestoreПродолжаетSequenceИПослеДиагностическогоClear() {
        EconomicLedger original = new EconomicLedger();
        original.recordTrade("A", "B", 0, 1L, 1_000L);
        original.recordResourceTransform("Factory", "recipe");
        EconomicLedger restored = new EconomicLedger(original.snapshotState());

        assertEquals(original.snapshotState(), restored.snapshotState());
        assertEquals(3L, restored.recordMoneySink("Taxpayer", 1L, "tax").sequence());

        restored.clear();
        EconomicLedger.State cleared = restored.snapshotState();
        assertEquals(4L, cleared.nextSequence());
        assertEquals(List.of(), cleared.entries());

        EconomicLedger afterClearRestore = new EconomicLedger(cleared);
        assertEquals(4L,
                afterClearRestore.recordMoneySource("Treasury", 1L, "grant").sequence());
    }

    @Test
    void ledgerStateОтклоняетРазрывыSequenceНоРазрешаетУсечённыйХвост() {
        EconomicTransaction five = new EconomicTransaction(
                5L, EconomicTransaction.Type.MONEY_SOURCE,
                "", "A", -1, 0L, 1L, "source");
        EconomicTransaction six = new EconomicTransaction(
                6L, EconomicTransaction.Type.MONEY_SINK,
                "A", "", -1, 0L, 1L, "sink");

        EconomicLedger.State tail = new EconomicLedger.State(7L, List.of(five, six));
        assertEquals(7L, new EconomicLedger(tail).snapshotState().nextSequence());
        assertThrows(IllegalArgumentException.class,
                () -> new EconomicLedger.State(8L, List.of(five, six)));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomicLedger.State(7L, List.of(five,
                        new EconomicTransaction(7L, EconomicTransaction.Type.MONEY_SINK,
                                "A", "", -1, 0L, 1L, "sink"))));
    }

    @Test
    void ledgerОтклоняетНеобъяснимыеИлиНекорректныеОперации() {
        EconomicLedger ledger = new EconomicLedger();
        assertThrows(IllegalArgumentException.class,
                () -> ledger.recordTrade("", "Seller", 0, 1L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.recordTrade("Buyer", "Seller", -1, 1L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.recordTrade("Buyer", "Seller", 0, 0L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.recordMoneySource("Station", 0L, "initial"));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.recordMoneySink("Station", 1L, " "));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.recordResourceSource("Miner", 0, 0L, "mining"));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.recordResourceSink("Colony", -1, 1L, "consumption"));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.recordResourceTransform("", "recipe"));
    }

    @Test
    void transactionПроверяетСтруктурныеИнварианты() {
        assertThrows(IllegalArgumentException.class,
                () -> new EconomicTransaction(0L, EconomicTransaction.Type.TRADE,
                        "A", "B", 0, 1L, 1L, "trade"));
        assertThrows(NullPointerException.class,
                () -> new EconomicTransaction(1L, null,
                        "A", "B", 0, 1L, 1L, "trade"));
        assertThrows(NullPointerException.class,
                () -> new EconomicTransaction(1L, EconomicTransaction.Type.TRADE,
                        null, "B", 0, 1L, 1L, "trade"));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomicTransaction(1L, EconomicTransaction.Type.TRADE,
                        "A", "B", -2, 0L, 0L, "trade"));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomicTransaction(1L, EconomicTransaction.Type.TRADE,
                        "A", "B", 0, -1L, 0L, "trade"));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomicTransaction(1L, EconomicTransaction.Type.TRADE,
                        "A", "B", 0, 1L, -1L, "trade"));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomicTransaction(1L, EconomicTransaction.Type.TRADE,
                        "A", "B", 0, 1L, 1L, " "));
    }
}
