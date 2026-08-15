package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F6FiscalAntiOscillationAcceptanceTest {
    private static final String FACTION = "faction.trade_league";
    private static final long STATION_RESERVE = 100_000_000L;
    private static final long STRESS_WALLET = 50_000_000L;
    private static final long DEADBAND_WALLET = 80_000_000L;

    @Test
    void sustainedLiquidityStressMovesFiscalPolicyOnlyOncePerReviewWindow() {
        WorldSimulation world = world(0x17F60011L);
        FactionFiscalPolicyState initial = configuredPolicy(world, 1_000, 0L);
        setOwnedMarketWallets(world, STRESS_WALLET);
        FactionPolicyReviewCadence cadence = new FactionPolicyReviewCadence(10L, 0L);
        FactionFiscalReviewProfile profile = profile();
        long treasuryBefore = treasury(world);
        long stationMoneyBefore = stationMoney(world);

        FactionFiscalPolicyReviewer.Result first = FactionFiscalPolicyReviewer.review(
                world, FACTION, cadence, profile);

        assertTrue(first.reviewClaimed());
        assertTrue(first.policyChanged());
        assertEquals(FactionFiscalPolicyReviewer.Zone.STRESS, first.zone());
        assertEquals(5_000, first.liquidityShortfallBasisPoints());
        assertEquals(750, first.resultingPolicy().stationTaxBasisPoints());
        assertEquals(Money.fromCredits(10_000d),
                first.resultingPolicy().maxLiquiditySupportPerDecisionMilliCredits());
        assertEquals(initial.foreignTerritoryLevyBasisPoints(),
                first.resultingPolicy().foreignTerritoryLevyBasisPoints());
        assertEquals(initial.treasuryReserveFloorMilliCredits(),
                first.resultingPolicy().treasuryReserveFloorMilliCredits());
        assertEquals(initial.stationLiquidityReserveMilliCredits(),
                first.resultingPolicy().stationLiquidityReserveMilliCredits());
        assertEquals(initial.maxConstructionInvestmentPerDecisionMilliCredits(),
                first.resultingPolicy().maxConstructionInvestmentPerDecisionMilliCredits());
        assertEquals(treasuryBefore, treasury(world));
        assertEquals(stationMoneyBefore, stationMoney(world));

        FactionFiscalPolicyReviewer.Result repeated = FactionFiscalPolicyReviewer.review(
                world, FACTION, cadence, profile);
        assertFalse(repeated.reviewClaimed());
        assertFalse(repeated.policyChanged());
        assertEquals(first.resultingPolicy(), world.findFactionFiscalPolicy(FACTION).orElseThrow());
        assertEquals(treasuryBefore, treasury(world));
        assertEquals(stationMoneyBefore, stationMoney(world));

        advanceToTick(world, world.getAuthoritativeWorldTick() + cadence.intervalTicks());
        setOwnedMarketWallets(world, STRESS_WALLET);
        long secondTreasuryBefore = treasury(world);
        long secondStationMoneyBefore = stationMoney(world);
        FactionFiscalPolicyReviewer.Result secondWindow = FactionFiscalPolicyReviewer.review(
                world, FACTION, cadence, profile);

        assertTrue(secondWindow.reviewClaimed());
        assertTrue(secondWindow.policyChanged());
        assertEquals(FactionFiscalPolicyReviewer.Zone.STRESS, secondWindow.zone());
        assertEquals(500, secondWindow.resultingPolicy().stationTaxBasisPoints());
        assertEquals(Money.fromCredits(20_000d),
                secondWindow.resultingPolicy().maxLiquiditySupportPerDecisionMilliCredits());
        assertEquals(secondTreasuryBefore, treasury(world));
        assertEquals(secondStationMoneyBefore, stationMoney(world));
    }

    @Test
    void deadbandClaimsReviewButDoesNotRewriteFiscalPolicy() {
        WorldSimulation world = world(0x17F60012L);
        FactionFiscalPolicyState initial = configuredPolicy(world, 1_000, Money.fromCredits(20_000d));
        setOwnedMarketWallets(world, DEADBAND_WALLET);
        long treasuryBefore = treasury(world);
        long stationMoneyBefore = stationMoney(world);

        FactionFiscalPolicyReviewer.Result result = FactionFiscalPolicyReviewer.review(
                world,
                FACTION,
                new FactionPolicyReviewCadence(10L, 0L),
                profile());

        assertTrue(result.reviewClaimed());
        assertFalse(result.policyChanged());
        assertEquals(FactionFiscalPolicyReviewer.Zone.DEADBAND, result.zone());
        assertEquals(2_000, result.liquidityShortfallBasisPoints());
        assertEquals(initial, result.resultingPolicy());
        assertEquals(initial, world.findFactionFiscalPolicy(FACTION).orElseThrow());
        assertEquals(treasuryBefore, treasury(world));
        assertEquals(stationMoneyBefore, stationMoney(world));
    }

    @Test
    void recoveredLiquidityMovesOnlyTowardNormalTargetsByOneBoundedStep() {
        WorldSimulation world = world(0x17F60013L);
        configuredPolicy(world, 500, Money.fromCredits(60_000d));
        setOwnedMarketWallets(world, STATION_RESERVE);
        long treasuryBefore = treasury(world);
        long stationMoneyBefore = stationMoney(world);

        FactionFiscalPolicyReviewer.Result result = FactionFiscalPolicyReviewer.review(
                world,
                FACTION,
                new FactionPolicyReviewCadence(10L, 0L),
                profile());

        assertTrue(result.reviewClaimed());
        assertTrue(result.policyChanged());
        assertEquals(FactionFiscalPolicyReviewer.Zone.NORMAL, result.zone());
        assertEquals(0, result.liquidityShortfallBasisPoints());
        assertEquals(750, result.resultingPolicy().stationTaxBasisPoints());
        assertEquals(Money.fromCredits(50_000d),
                result.resultingPolicy().maxLiquiditySupportPerDecisionMilliCredits());
        assertEquals(treasuryBefore, treasury(world));
        assertEquals(stationMoneyBefore, stationMoney(world));
    }

    private static WorldSimulation world(long seed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        return WorldSimulation.restore(
                DemoGalaxyFactory.createState(seed, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }

    private static FactionFiscalPolicyState configuredPolicy(
            WorldSimulation world,
            int stationTaxBasisPoints,
            long supportCap) {
        FactionFiscalPolicyState base = world.findFactionFiscalPolicy(FACTION).orElseThrow();
        FactionFiscalPolicyState configured = new FactionFiscalPolicyState(
                stationTaxBasisPoints,
                base.foreignTerritoryLevyBasisPoints(),
                base.treasuryReserveFloorMilliCredits(),
                STATION_RESERVE,
                supportCap,
                base.maxConstructionInvestmentPerDecisionMilliCredits());
        world.updateFactionFiscalPolicy(FACTION, configured);
        return configured;
    }

    private static FactionFiscalReviewProfile profile() {
        return new FactionFiscalReviewProfile(
                4_000,
                1_000,
                1_000,
                0,
                250,
                0L,
                Money.fromCredits(100_000d),
                Money.fromCredits(10_000d));
    }

    private static void setOwnedMarketWallets(WorldSimulation world, long targetBalance) {
        int runtimeFactionId = world.findFactionRuntimeId(FACTION).orElseThrow();
        int touched = 0;
        for (StarSystemNode system : world.getTopology().systems()) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            for (Entity entity : session.getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                MarketComponent market = entity.getComponent(MarketComponent.class);
                WalletComponent wallet = entity.getComponent(WalletComponent.class);
                if (faction == null || faction.factionId != runtimeFactionId || market == null || wallet == null) {
                    continue;
                }
                long balance = wallet.getBalanceMilliCredits();
                if (balance > targetBalance) {
                    assertTrue(wallet.debitToSink(balance - targetBalance));
                } else if (balance < targetBalance) {
                    assertTrue(wallet.creditFromSource(targetBalance - balance));
                }
                touched++;
            }
        }
        assertTrue(touched > 0, "Fixture faction must own at least one market station");
    }

    private static long treasury(WorldSimulation world) {
        return world.findFactionEconomicState(FACTION).orElseThrow().treasuryMilliCredits();
    }

    private static long stationMoney(WorldSimulation world) {
        return FactionFiscalPositionAnalyzer.analyze(world, FACTION).ownedMarketLiquidityMilliCredits();
    }

    private static void advanceToTick(WorldSimulation world, long targetTick) {
        float fixedStep = world.findSession(world.getActiveSystemId()).orElseThrow()
                .getClock().getFixedStepSeconds();
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick && guard++ < 10_000) {
            world.advanceFrame(fixedStep);
        }
        assertTrue(world.getAuthoritativeWorldTick() >= targetTick, "World did not reach requested review tick");
    }
}
