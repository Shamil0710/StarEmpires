package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSimulationFactionFiscalTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void taxИTerritoryTariffПереносятДеньгиВTreasuryБезЭмиссии() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(0xF15CA100L, content);
        WorldState fiscalState = withFiscalPolicy(base);
        WorldSimulation world = WorldSimulation.restore(
                fiscalState,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                10,
                8);

        long totalBefore = totalAuthoritativeMoney(world);
        long treasuryBefore = world.findFactionEconomicState(TRADE_LEAGUE)
                .orElseThrow().treasuryMilliCredits();
        long transfersBefore = moneyTransferCount(world);

        WorldSimulation.FiscalPolicyReport report = world.applyFiscalPolicy(TRADE_LEAGUE);

        assertTrue(report.taxCollectedMilliCredits() > 0L);
        assertTrue(report.tariffCollectedMilliCredits() > 0L);
        assertTrue(report.taxedStations() > 0);
        assertTrue(report.tariffedStations() > 0);
        assertEquals(
                Math.addExact(treasuryBefore, report.totalCollectedMilliCredits()),
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
        assertEquals(totalBefore, totalAuthoritativeMoney(world));
        assertEquals(
                transfersBefore + report.taxedStations() + report.tariffedStations(),
                moneyTransferCount(world));

        WorldState snapshot = world.snapshot();
        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(snapshot));
        assertEquals(snapshot, decoded);
        FactionStrategicState restored = decoded.factionStrategies().stream()
                .filter(state -> state.factionContentId().equals(TRADE_LEAGUE))
                .findFirst().orElseThrow();
        assertEquals(1_000, restored.stationTaxBasisPoints());
        assertEquals(500, restored.foreignTerritoryTariffBasisPoints());
    }

    private static WorldState withFiscalPolicy(WorldState base) {
        List<FactionEconomicState> economics = new ArrayList<>();
        for (FactionEconomicState state : base.factions()) {
            if (state.factionContentId().equals(TRADE_LEAGUE)) {
                economics.add(new FactionEconomicState(
                        state.factionContentId(),
                        state.treasuryMilliCredits(),
                        0L,
                        state.maxLiquiditySupportPerDecisionMilliCredits()));
            } else {
                economics.add(state);
            }
        }

        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState state : base.factionStrategies()) {
            if (state.factionContentId().equals(TRADE_LEAGUE)) {
                strategies.add(new FactionStrategicState(
                        state.factionContentId(),
                        state.minimumMarketAccessRelation(),
                        state.relations(),
                        state.controlledSystems(),
                        1_000,
                        500,
                        state.stockPolicies(),
                        state.productionPolicies(),
                        state.strategicGoals()));
            } else {
                strategies.add(state);
            }
        }
        return new WorldState(
                WorldState.CURRENT_VERSION,
                base.topology(),
                base.systems(),
                economics,
                strategies);
    }

    private static long totalAuthoritativeMoney(WorldSimulation world) {
        long total = 0L;
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                WalletComponent wallet = entity.getComponent(WalletComponent.class);
                if (wallet != null) {
                    total = Math.addExact(total, wallet.getBalanceMilliCredits());
                }
            }
        }
        for (String factionId : List.of("faction.neutral", TRADE_LEAGUE, "faction.miners")) {
            total = Math.addExact(
                    total,
                    world.findFactionEconomicState(factionId).orElseThrow().treasuryMilliCredits());
        }
        return total;
    }

    private static long moneyTransferCount(WorldSimulation world) {
        long count = 0L;
        for (StarSystemNode system : world.getTopology().systems()) {
            count += world.findSession(system.id()).orElseThrow().getLedger().getEntries().stream()
                    .filter(entry -> entry.type() == EconomicTransaction.Type.MONEY_TRANSFER)
                    .count();
        }
        return count;
    }
}
