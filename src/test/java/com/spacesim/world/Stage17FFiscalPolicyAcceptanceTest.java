package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage17FFiscalPolicyAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void reserveFloorBoundsOrdinaryLiquiditySupportWithoutCreatingOrDestroyingMoney() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F20001L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        FactionFiscalPolicyState before = world.findFactionFiscalPolicy(TRADE_LEAGUE).orElseThrow();
        long treasuryBefore = world.findFactionEconomicState(TRADE_LEAGUE)
                .orElseThrow().treasuryMilliCredits();
        long spendable = Money.fromCredits(60_000d);
        FactionFiscalPolicyState policy = new FactionFiscalPolicyState(
                1_200,
                800,
                treasuryBefore - spendable,
                Money.fromCredits(300_000d),
                Money.fromCredits(100_000d),
                Money.fromCredits(100_000d));

        assertEquals(policy, world.updateFactionFiscalPolicy(TRADE_LEAGUE, policy));
        assertEquals(
                treasuryBefore,
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits(),
                "Changing policy must not itself transfer money");
        assertEquals(1_200, world.findFactionStrategicState(TRADE_LEAGUE).orElseThrow().stationTaxBasisPoints());
        assertEquals(800,
                world.findFactionStrategicState(TRADE_LEAGUE).orElseThrow().foreignTerritoryTariffBasisPoints());

        WorldSimulation.LiquiditySupportReport first = world.applyLiquiditySupport(TRADE_LEAGUE);
        assertEquals(spendable, first.transferredMilliCredits());
        assertEquals(
                policy.treasuryReserveFloorMilliCredits(),
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());

        WorldSimulation.LiquiditySupportReport second = world.applyLiquiditySupport(TRADE_LEAGUE);
        assertEquals(0L, second.transferredMilliCredits(),
                "Protected treasury reserve must not be consumed by ordinary support");

        world.updateFactionFiscalPolicy(TRADE_LEAGUE, before);
    }

    @Test
    void constructionFundingRequiresBothSpendableTreasuryAndPerDecisionAuthorization() {
        WorldSimulation world = DemoGalaxyFactory.create(0x17F20002L);
        long treasuryBefore = world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits();
        FactionFiscalPolicyState initial = world.findFactionFiscalPolicy(MINERS).orElseThrow();
        ConstructionProjectId projectId = ConstructionProjectTestFixtures.createAuthorizedProject(
                world,
                MINERS,
                "station.foundry",
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                910f,
                640f);

        long requested = Money.fromCredits(20_000d);
        FactionFiscalPolicyState capBlocked = new FactionFiscalPolicyState(
                initial.stationTaxBasisPoints(),
                initial.foreignTerritoryLevyBasisPoints(),
                0L,
                initial.stationLiquidityReserveMilliCredits(),
                initial.maxLiquiditySupportPerDecisionMilliCredits(),
                Money.fromCredits(10_000d));
        world.updateFactionFiscalPolicy(MINERS, capBlocked);
        assertEquals(0L, world.fundConstructionProject(projectId, requested));
        assertEquals(treasuryBefore, world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits());

        FactionFiscalPolicyState reserveBlocked = new FactionFiscalPolicyState(
                capBlocked.stationTaxBasisPoints(),
                capBlocked.foreignTerritoryLevyBasisPoints(),
                treasuryBefore - Money.fromCredits(15_000d),
                capBlocked.stationLiquidityReserveMilliCredits(),
                capBlocked.maxLiquiditySupportPerDecisionMilliCredits(),
                Money.fromCredits(50_000d));
        world.updateFactionFiscalPolicy(MINERS, reserveBlocked);
        assertEquals(0L, world.fundConstructionProject(projectId, requested));
        assertEquals(treasuryBefore, world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits());

        FactionFiscalPolicyState allowed = new FactionFiscalPolicyState(
                reserveBlocked.stationTaxBasisPoints(),
                reserveBlocked.foreignTerritoryLevyBasisPoints(),
                treasuryBefore - Money.fromCredits(30_000d),
                reserveBlocked.stationLiquidityReserveMilliCredits(),
                reserveBlocked.maxLiquiditySupportPerDecisionMilliCredits(),
                Money.fromCredits(50_000d));
        world.updateFactionFiscalPolicy(MINERS, allowed);
        assertEquals(requested, world.fundConstructionProject(projectId, requested));
        assertEquals(
                treasuryBefore - requested,
                world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits());
        assertEquals(
                requested,
                world.findConstructionProject(projectId).orElseThrow().projectWalletMilliCredits());
    }
}
