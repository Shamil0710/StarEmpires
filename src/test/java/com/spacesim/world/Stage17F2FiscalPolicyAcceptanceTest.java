package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F2FiscalPolicyAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void commonPolicyBoundaryChangesOnlyAuthorizationsAndHardGatesRealTreasurySpending() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(17_704L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        WorldState before = world.snapshot();
        FactionEconomicState economyBefore = world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow();
        FactionStrategicState strategyBefore = world.findFactionStrategicState(TRADE_LEAGUE).orElseThrow();
        FactionDiplomacyState diplomacyBefore = world.findFactionDiplomacyState(TRADE_LEAGUE).orElseThrow();
        long treasuryBefore = economyBefore.treasuryMilliCredits();

        FactionFiscalPolicyState locked = new FactionFiscalPolicyState(
                TRADE_LEAGUE,
                321,
                654,
                777,
                treasuryBefore,
                economyBefore.stationLiquidityReserveMilliCredits(),
                economyBefore.maxLiquiditySupportPerDecisionMilliCredits(),
                500L);
        assertEquals(locked, world.updateFactionFiscalPolicy(locked));
        assertEquals(locked, world.findFactionFiscalPolicy(TRADE_LEAGUE).orElseThrow());

        WorldState afterEdit = world.snapshot();
        assertEquals(before.systems(), afterEdit.systems());
        assertEquals(before.constructionProjects(), afterEdit.constructionProjects());
        assertEquals(before.fleets(), afterEdit.fleets());
        assertEquals(before.fleetJumps(), afterEdit.fleetJumps());
        assertEquals(before.factionIdentities(), afterEdit.factionIdentities());
        assertEquals(treasuryBefore, world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
        assertStrategicStateUnchangedExceptFiscalRates(
                strategyBefore,
                world.findFactionStrategicState(TRADE_LEAGUE).orElseThrow());
        FactionDiplomacyState diplomacyAfter = world.findFactionDiplomacyState(TRADE_LEAGUE).orElseThrow();
        assertEquals(diplomacyBefore.standings(), diplomacyAfter.standings());
        assertEquals(diplomacyBefore.grievances(), diplomacyAfter.grievances());
        assertEquals(diplomacyBefore.treaties(), diplomacyAfter.treaties());
        assertEquals(diplomacyBefore.embargoes(), diplomacyAfter.embargoes());
        assertEquals(777, diplomacyAfter.customsTariffBasisPoints());

        WorldState beforeBlockedSupport = world.snapshot();
        WorldSimulation.LiquiditySupportReport blockedSupport = world.applyLiquiditySupport(TRADE_LEAGUE);
        assertEquals(0L, blockedSupport.transferredMilliCredits());
        assertEquals(0, blockedSupport.supportedStations());
        assertEquals(beforeBlockedSupport, world.snapshot());

        FactionFiscalPolicyState constructionPolicy = new FactionFiscalPolicyState(
                TRADE_LEAGUE,
                locked.ownStationTaxBasisPoints(),
                locked.territorialForeignStationLevyBasisPoints(),
                locked.customsTariffBasisPoints(),
                treasuryBefore - 500L,
                locked.stationLiquidityReserveMilliCredits(),
                locked.maxLiquiditySupportPerDecisionMilliCredits(),
                500L);
        world.updateFactionFiscalPolicy(constructionPolicy);

        ContentCatalog.StationArchetypeDefinition station = content.getStationArchetypes().stream()
                .filter(candidate -> candidate.construction() != null)
                .filter(candidate -> TRADE_LEAGUE.equals(candidate.factionId()))
                .findFirst()
                .orElseThrow();
        ConstructionProjectId projectId = world.createConstructionProject(
                TRADE_LEAGUE,
                station.id(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                1250f,
                760f);

        assertEquals(0L, world.fundConstructionProject(projectId, 501L));
        assertEquals(treasuryBefore, world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
        assertEquals(0L, world.findConstructionProject(projectId).orElseThrow().projectWalletMilliCredits());

        assertEquals(500L, world.fundConstructionProject(projectId, 500L));
        assertEquals(
                constructionPolicy.treasuryReserveFloorMilliCredits(),
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
        assertEquals(500L, world.findConstructionProject(projectId).orElseThrow().projectWalletMilliCredits());
        assertEquals(0L, world.fundConstructionProject(projectId, 1L));

        assertTrue(world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getLedger().getEntries().stream()
                .anyMatch(entry -> entry.type() == EconomicTransaction.Type.MONEY_TRANSFER
                        && entry.moneyMilliCredits() == 500L
                        && "construction-project-funding".equals(entry.reason())));

        WorldState saved = world.snapshot();
        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(saved));
        assertEquals(saved, decoded);
        WorldSimulation restored = WorldSimulation.restore(
                decoded,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        assertEquals(constructionPolicy, restored.findFactionFiscalPolicy(TRADE_LEAGUE).orElseThrow());
        assertEquals(500L, restored.findConstructionProject(projectId).orElseThrow().projectWalletMilliCredits());
    }

    @Test
    void editedTaxAndTerritorialLevyImmediatelyUseExistingConservedFiscalSettlement() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(17_706L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        FactionFiscalPolicyState initial = world.findFactionFiscalPolicy(TRADE_LEAGUE).orElseThrow();
        long treasuryBefore = world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits();

        world.updateFactionFiscalPolicy(new FactionFiscalPolicyState(
                TRADE_LEAGUE,
                1_000,
                500,
                initial.customsTariffBasisPoints(),
                0L,
                0L,
                initial.maxLiquiditySupportPerDecisionMilliCredits(),
                initial.maxConstructionInvestmentPerDecisionMilliCredits()));
        WorldSimulation.FiscalPolicyReport report = world.applyFiscalPolicy(TRADE_LEAGUE);

        assertTrue(report.taxCollectedMilliCredits() > 0L);
        assertTrue(report.tariffCollectedMilliCredits() > 0L);
        assertTrue(report.taxedStations() > 0);
        assertTrue(report.tariffedStations() > 0);
        assertEquals(
                treasuryBefore + report.totalCollectedMilliCredits(),
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
        assertTrue(world.snapshot().systems().stream()
                .flatMap(system -> system.simulationState().ledger().entries().stream())
                .anyMatch(entry -> entry.type() == EconomicTransaction.Type.MONEY_TRANSFER
                        && "faction-station-tax".equals(entry.reason())));
        assertTrue(world.snapshot().systems().stream()
                .flatMap(system -> system.simulationState().ledger().entries().stream())
                .anyMatch(entry -> entry.type() == EconomicTransaction.Type.MONEY_TRANSFER
                        && "faction-territory-tariff".equals(entry.reason())));
    }

    private static void assertStrategicStateUnchangedExceptFiscalRates(
            FactionStrategicState before,
            FactionStrategicState after) {
        assertEquals(before.factionContentId(), after.factionContentId());
        assertEquals(before.minimumMarketAccessRelation(), after.minimumMarketAccessRelation());
        assertEquals(before.relations(), after.relations());
        assertEquals(before.controlledSystems(), after.controlledSystems());
        assertEquals(321, after.stationTaxBasisPoints());
        assertEquals(654, after.foreignTerritoryTariffBasisPoints());
        assertEquals(before.stockPolicies(), after.stockPolicies());
        assertEquals(before.productionPolicies(), after.productionPolicies());
        assertEquals(before.strategicGoals(), after.strategicGoals());
        assertEquals(before.territorialClaims(), after.territorialClaims());
        assertEquals(before.territorialControlStates(), after.territorialControlStates());
        assertEquals(before.territorialRecognitions(), after.territorialRecognitions());
        assertEquals(before.constructionRightsGranted(), after.constructionRightsGranted());
        assertEquals(before.doctrine(), after.doctrine());
    }
}
