package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.economy.Money;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage8FactionEconomyEndToEndTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void factionDecisionФизическиМеняетDemandProductionИFinancialFlowsБезЭмиссии() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState configured = configuredWorld(
                DemoGalaxyFactory.createState(0x8FAC7101L, content));
        WorldSimulation world = WorldSimulation.restore(
                configured,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                10,
                8);
        prepareConservedLiquidityAsymmetry(world, content);
        long totalMoneyBefore = totalAuthoritativeMoney(world);

        FactionStrategicPolicyEngine.ApplicationReport strategic =
                FactionStrategicPolicyEngine.apply(world, content, TRADE_LEAGUE);
        WorldSimulation.FiscalPolicyReport fiscal = world.applyFiscalPolicy(TRADE_LEAGUE);
        WorldSimulation.LiquiditySupportReport subsidy = world.applyLiquiditySupport(TRADE_LEAGUE);

        assertTrue(strategic.marketsAdjusted() > 0);
        assertTrue(strategic.activeStrategicGoals() >= 2);
        assertTrue(fiscal.taxCollectedMilliCredits() > 0L);
        assertTrue(fiscal.tariffCollectedMilliCredits() > 0L);
        assertTrue(subsidy.transferredMilliCredits() > 0L);
        assertEquals(totalMoneyBefore, totalAuthoritativeMoney(world));

        for (int tick = 0; tick < 200; tick++) {
            world.advanceFrame(0.1f);
        }

        int weaponsId = content.findItem("item.weapons").runtimeId();
        boolean demandVisible = false;
        long trades = 0L;
        long moneyTransfers = 0L;
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                MarketComponent market = entity.getComponent(MarketComponent.class);
                if (market != null && market.isTradable(weaponsId) && market.targetStock[weaponsId] >= 600) {
                    demandVisible = true;
                }
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (inventory != null) {
                    for (int amount : inventory.stock) {
                        assertTrue(amount >= 0);
                    }
                }
            }
            for (EconomicTransaction entry : world.findSession(system.id()).orElseThrow()
                    .getLedger().getEntries()) {
                if (entry.type() == EconomicTransaction.Type.TRADE) {
                    trades++;
                } else if (entry.type() == EconomicTransaction.Type.MONEY_TRANSFER) {
                    moneyTransfers++;
                }
            }
        }
        assertTrue(demandVisible);
        assertTrue(trades > 0L);
        assertTrue(moneyTransfers >= fiscal.taxedStations()
                + fiscal.tariffedStations()
                + subsidy.supportedStations());
        assertEquals(totalMoneyBefore, totalAuthoritativeMoney(world));
    }

    private static WorldState configuredWorld(WorldState base) {
        List<FactionEconomicState> economics = new ArrayList<>();
        for (FactionEconomicState state : base.factions()) {
            if (state.factionContentId().equals(TRADE_LEAGUE)) {
                economics.add(new FactionEconomicState(
                        state.factionContentId(),
                        state.treasuryMilliCredits(),
                        Money.fromCredits(200_000d),
                        Money.fromCredits(100_000d)));
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
                        List.of(new FactionStockPolicyState("item.energy", 500)),
                        List.of(new FactionProductionPolicyState(
                                "station.arsenal", "recipe.weapons_assembly")),
                        List.of(
                                new FactionStrategicGoalState(
                                        "goal.rearm",
                                        FactionStrategicGoalState.GoalType.MILITARY,
                                        List.of(new FactionStockPolicyState("item.weapons", 600))),
                                new FactionStrategicGoalState(
                                        "goal.expand",
                                        FactionStrategicGoalState.GoalType.EXPANSION,
                                        List.of(new FactionStockPolicyState("item.food", 700))))));
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

    private static void prepareConservedLiquidityAsymmetry(
            WorldSimulation world,
            ContentCatalog content) {
        int factionId = content.findFaction(TRADE_LEAGUE).runtimeId();
        List<WalletComponent> wallets = new ArrayList<>();
        for (Entity entity : world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            MarketComponent market = entity.getComponent(MarketComponent.class);
            if (faction != null && faction.factionId == factionId && wallet != null && market != null) {
                wallets.add(wallet);
            }
        }
        if (wallets.size() < 2
                || !wallets.get(0).transferTo(wallets.get(1), Money.fromCredits(100_000d))) {
            throw new AssertionError("Не удалось подготовить conserved liquidity fixture");
        }
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
}
