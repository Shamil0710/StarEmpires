package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.economy.Money;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSimulationFactionEconomyTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final long STATION_RESERVE = Money.MILLI_CREDITS_PER_CREDIT * 300_000L;
    private static final long STATION_START = Money.MILLI_CREDITS_PER_CREDIT * 250_000L;
    private static final long DECISION_BUDGET = Money.MILLI_CREDITS_PER_CREDIT * 100_000L;

    @Test
    void liquiditySupportПереводитСуществующиеДеньгиИДетерминированноВыбираетСтанции() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0xFA710001L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        long moneyBefore = totalWorldMoney(world);
        FactionEconomicState factionBefore = world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow();
        List<Entity> activeOwned = ownedMarketStations(
                world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow(),
                content.findFaction(TRADE_LEAGUE).runtimeId());
        List<Entity> innerOwned = ownedMarketStations(
                world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow(),
                content.findFaction(TRADE_LEAGUE).runtimeId());

        assertEquals(2, activeOwned.size());
        assertEquals(2, innerOwned.size());
        assertEquals(STATION_START, wallet(activeOwned.get(0)).getBalanceMilliCredits());
        assertEquals(STATION_START, wallet(activeOwned.get(1)).getBalanceMilliCredits());

        WorldSimulation.LiquiditySupportReport report = world.applyLiquiditySupport(TRADE_LEAGUE);

        assertEquals(DECISION_BUDGET, report.transferredMilliCredits());
        assertEquals(2, report.supportedStations());
        assertEquals(STATION_RESERVE, wallet(activeOwned.get(0)).getBalanceMilliCredits());
        assertEquals(STATION_RESERVE, wallet(activeOwned.get(1)).getBalanceMilliCredits());
        assertEquals(STATION_START, wallet(innerOwned.get(0)).getBalanceMilliCredits());
        assertEquals(STATION_START, wallet(innerOwned.get(1)).getBalanceMilliCredits());
        assertEquals(
                factionBefore.treasuryMilliCredits() - DECISION_BUDGET,
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
        assertEquals(moneyBefore, totalWorldMoney(world));

        List<EconomicTransaction> transfers = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getLedger().getEntries().stream()
                .filter(entry -> entry.type() == EconomicTransaction.Type.MONEY_TRANSFER)
                .toList();
        assertEquals(2, transfers.size());
        assertEquals(Money.fromCredits(50_000d), transfers.get(0).moneyMilliCredits());
        assertEquals(Money.fromCredits(50_000d), transfers.get(1).moneyMilliCredits());
        assertEquals("faction-liquidity-support", transfers.get(0).reason());
        assertFalse(world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getLedger().getEntries().stream()
                .anyMatch(entry -> entry.type() == EconomicTransaction.Type.MONEY_SOURCE
                        || entry.type() == EconomicTransaction.Type.MONEY_SINK));
    }

    @Test
    void повторноеРешениеПереходитКСледующейСистемеПослеЗаполненияЛокальногоРезерва() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0xFA710002L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                10,
                8);
        int runtimeFactionId = content.findFaction(TRADE_LEAGUE).runtimeId();
        List<Entity> innerOwned = ownedMarketStations(
                world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow(),
                runtimeFactionId);

        world.applyLiquiditySupport(TRADE_LEAGUE);
        WorldSimulation.LiquiditySupportReport second = world.applyLiquiditySupport(TRADE_LEAGUE);

        assertEquals(DECISION_BUDGET, second.transferredMilliCredits());
        assertEquals(2, second.supportedStations());
        assertEquals(STATION_RESERVE, wallet(innerOwned.get(0)).getBalanceMilliCredits());
        assertEquals(STATION_RESERVE, wallet(innerOwned.get(1)).getBalanceMilliCredits());
        assertEquals(2, world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow()
                .getLedger().getEntries().stream()
                .filter(entry -> entry.type() == EconomicTransaction.Type.MONEY_TRANSFER)
                .count());
    }

    @Test
    void factionTreasuryИMoneyTransferLedgerПереживаютWorldCodecContinuation() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0xFA710003L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                10,
                8);
        world.applyLiquiditySupport(TRADE_LEAGUE);
        WorldState saved = world.snapshot();

        WorldSimulation restored = WorldSimulation.restore(
                WorldStateCodec.decode(WorldStateCodec.encode(saved)),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                10,
                8);

        assertEquals(saved, restored.snapshot());
        assertEquals(
                world.findFactionEconomicState(TRADE_LEAGUE),
                restored.findFactionEconomicState(TRADE_LEAGUE));
        assertTrue(restored.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getLedger().getEntries().stream()
                .anyMatch(entry -> entry.type() == EconomicTransaction.Type.MONEY_TRANSFER));
    }

    @Test
    void unknownOrLegacyFactionБезEconomicStateНеМожетТратитьНесуществующийTreasury() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        SimulationSession legacySession = SimulationSession.createDemo(101L, content);
        WorldSimulation legacyWorld = WorldSimulation.restore(
                WorldState.singleSystem(legacySession.snapshot()),
                content,
                WorldTopologyDefaults.DEFAULT_SYSTEM_ID,
                10,
                8);

        assertTrue(legacyWorld.findFactionEconomicState(TRADE_LEAGUE).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> legacyWorld.applyLiquiditySupport(TRADE_LEAGUE));
    }

    private static long totalWorldMoney(WorldSimulation world) {
        long total = 0L;
        for (StarSystemNode system : world.getTopology().systems()) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            for (Entity entity : session.getEngine().getEntities()) {
                WalletComponent wallet = entity.getComponent(WalletComponent.class);
                if (wallet != null) {
                    total = Math.addExact(total, wallet.getBalanceMilliCredits());
                }
            }
        }
        for (String factionId : List.of("faction.neutral", "faction.trade_league", "faction.miners")) {
            FactionEconomicState faction = world.findFactionEconomicState(factionId).orElse(null);
            if (faction != null) {
                total = Math.addExact(total, faction.treasuryMilliCredits());
            }
        }
        return total;
    }

    private static List<Entity> ownedMarketStations(SimulationSession session, int runtimeFactionId) {
        List<Entity> result = new ArrayList<>();
        for (Entity entity : session.getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (faction != null
                    && faction.factionId == runtimeFactionId
                    && entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(WalletComponent.class) != null
                    && entity.getComponent(EntityIdComponent.class) != null) {
                result.add(entity);
            }
        }
        result.sort(Comparator.comparingLong(entity ->
                entity.getComponent(EntityIdComponent.class).id.value()));
        return result;
    }

    private static WalletComponent wallet(Entity entity) {
        return entity.getComponent(WalletComponent.class);
    }
}
