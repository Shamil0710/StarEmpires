package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FFiscalTradeoffAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void higherTaxBuildsTreasuryByDrainingRealStationLiquidityAndSubsidyReversesThatAtRealFiscalCost() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F30001L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        FactionFiscalPolicyState initial = world.findFactionFiscalPolicy(TRADE_LEAGUE).orElseThrow();
        FactionFiscalPolicyState taxPolicy = new FactionFiscalPolicyState(
                1_000,
                0,
                0L,
                0L,
                initial.maxLiquiditySupportPerDecisionMilliCredits(),
                initial.maxConstructionInvestmentPerDecisionMilliCredits());
        world.updateFactionFiscalPolicy(TRADE_LEAGUE, taxPolicy);

        FactionFiscalPositionDiagnostics beforeTax =
                FactionFiscalPositionAnalyzer.analyze(world, TRADE_LEAGUE);
        WorldSimulation.FiscalPolicyReport tax = world.applyFiscalPolicy(TRADE_LEAGUE);
        FactionFiscalPositionDiagnostics afterTax =
                FactionFiscalPositionAnalyzer.analyze(world, TRADE_LEAGUE);

        assertTrue(tax.taxCollectedMilliCredits() > 0L);
        assertEquals(0L, tax.tariffCollectedMilliCredits());
        assertEquals(
                beforeTax.treasuryBalanceMilliCredits() + tax.taxCollectedMilliCredits(),
                afterTax.treasuryBalanceMilliCredits());
        assertEquals(
                beforeTax.ownedMarketLiquidityMilliCredits() - tax.taxCollectedMilliCredits(),
                afterTax.ownedMarketLiquidityMilliCredits());
        assertEquals(
                beforeTax.treasuryBalanceMilliCredits() + beforeTax.ownedMarketLiquidityMilliCredits(),
                afterTax.treasuryBalanceMilliCredits() + afterTax.ownedMarketLiquidityMilliCredits(),
                "Tax must redistribute existing money rather than create fiscal income");

        FactionFiscalPolicyState supportPolicy = new FactionFiscalPolicyState(
                0,
                0,
                0L,
                Money.fromCredits(300_000d),
                Money.fromCredits(100_000d),
                initial.maxConstructionInvestmentPerDecisionMilliCredits());
        world.updateFactionFiscalPolicy(TRADE_LEAGUE, supportPolicy);
        FactionFiscalPositionDiagnostics beforeSupport =
                FactionFiscalPositionAnalyzer.analyze(world, TRADE_LEAGUE);
        assertTrue(beforeSupport.liquidityShortfallMilliCredits() > 0L);

        WorldSimulation.LiquiditySupportReport support = world.applyLiquiditySupport(TRADE_LEAGUE);
        FactionFiscalPositionDiagnostics afterSupport =
                FactionFiscalPositionAnalyzer.analyze(world, TRADE_LEAGUE);

        assertTrue(support.transferredMilliCredits() > 0L);
        assertEquals(
                beforeSupport.treasuryBalanceMilliCredits() - support.transferredMilliCredits(),
                afterSupport.treasuryBalanceMilliCredits());
        assertEquals(
                beforeSupport.ownedMarketLiquidityMilliCredits() + support.transferredMilliCredits(),
                afterSupport.ownedMarketLiquidityMilliCredits());
        assertEquals(
                beforeSupport.liquidityShortfallMilliCredits() - support.transferredMilliCredits(),
                afterSupport.liquidityShortfallMilliCredits());
        assertEquals(
                beforeSupport.treasuryBalanceMilliCredits() + beforeSupport.ownedMarketLiquidityMilliCredits(),
                afterSupport.treasuryBalanceMilliCredits() + afterSupport.ownedMarketLiquidityMilliCredits(),
                "Subsidy must improve station liquidity only by consuming the same real treasury");
    }

    @Test
    void lowerReservePermitsRealExpansionCapitalWhileHigherReserveKeepsTheSameMoneyProtected() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation lowReserveWorld = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F30002L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        WorldSimulation highReserveWorld = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F30002L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        long treasury = lowReserveWorld.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits();
        assertEquals(treasury, highReserveWorld.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits());
        FactionFiscalPolicyState base = lowReserveWorld.findFactionFiscalPolicy(MINERS).orElseThrow();
        FactionFiscalPolicyState lowReserve = new FactionFiscalPolicyState(
                base.stationTaxBasisPoints(),
                base.foreignTerritoryLevyBasisPoints(),
                treasury - Money.fromCredits(30_000d),
                base.stationLiquidityReserveMilliCredits(),
                base.maxLiquiditySupportPerDecisionMilliCredits(),
                Money.fromCredits(50_000d));
        FactionFiscalPolicyState highReserve = new FactionFiscalPolicyState(
                base.stationTaxBasisPoints(),
                base.foreignTerritoryLevyBasisPoints(),
                treasury - Money.fromCredits(10_000d),
                base.stationLiquidityReserveMilliCredits(),
                base.maxLiquiditySupportPerDecisionMilliCredits(),
                Money.fromCredits(50_000d));
        lowReserveWorld.updateFactionFiscalPolicy(MINERS, lowReserve);
        highReserveWorld.updateFactionFiscalPolicy(MINERS, highReserve);

        FactionFiscalPositionDiagnostics lowBefore =
                FactionFiscalPositionAnalyzer.analyze(lowReserveWorld, MINERS);
        FactionFiscalPositionDiagnostics highBefore =
                FactionFiscalPositionAnalyzer.analyze(highReserveWorld, MINERS);
        assertEquals(Money.fromCredits(30_000d), lowBefore.spendableTreasuryMilliCredits());
        assertEquals(Money.fromCredits(10_000d), highBefore.spendableTreasuryMilliCredits());

        ConstructionProjectId lowProject = ConstructionProjectTestFixtures.createAuthorizedProject(
                lowReserveWorld,
                MINERS,
                "station.foundry",
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                910f,
                640f);
        ConstructionProjectId highProject = ConstructionProjectTestFixtures.createAuthorizedProject(
                highReserveWorld,
                MINERS,
                "station.foundry",
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                910f,
                640f);
        long funding = Money.fromCredits(20_000d);

        assertEquals(funding, lowReserveWorld.fundConstructionProject(lowProject, funding));
        assertEquals(0L, highReserveWorld.fundConstructionProject(highProject, funding));

        FactionFiscalPositionDiagnostics lowAfter =
                FactionFiscalPositionAnalyzer.analyze(lowReserveWorld, MINERS);
        FactionFiscalPositionDiagnostics highAfter =
                FactionFiscalPositionAnalyzer.analyze(highReserveWorld, MINERS);
        assertEquals(funding, lowAfter.activeConstructionWalletMilliCredits());
        assertEquals(0L, highAfter.activeConstructionWalletMilliCredits());
        assertEquals(
                lowBefore.treasuryBalanceMilliCredits(),
                lowAfter.treasuryBalanceMilliCredits() + lowAfter.activeConstructionWalletMilliCredits(),
                "Lower reserve may accelerate investment only by moving real treasury into a project wallet");
        assertEquals(
                highBefore.treasuryBalanceMilliCredits(),
                highAfter.treasuryBalanceMilliCredits(),
                "Higher reserve protects the treasury by denying ordinary construction funding");

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(lowReserveWorld.snapshot()));
        WorldSimulation restored = WorldSimulation.restore(
                decoded,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        assertEquals(lowAfter, FactionFiscalPositionAnalyzer.analyze(restored, MINERS));
    }
}
