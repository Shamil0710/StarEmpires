package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage11CPhysicalExpansionAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void factionPhysicallyBuildsAndClaimsUnclaimedNeighborAcrossSaveLoad() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(0x11C0L, content);
        WorldState unclaimed = withoutController(base, DemoGalaxyFactory.INNER_SYSTEM_ID);
        FactionExpansionRuntime runtime = new FactionExpansionRuntime(
                WorldSimulation.restore(
                        unclaimed,
                        content,
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                        WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                        WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME),
                content);

        StrategicGrowthState.Plan created = runtime.planBestUnclaimed(TRADE_LEAGUE).orElseThrow();
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, created.targetSystemId());
        StrategicGrowthState.PlanId planId = created.id();

        FleetId supportFleet = null;
        com.spacesim.persistence.EntityId originalLocalFleetId = null;
        long supportWalletBefore = -1L;
        boolean savedMidTransit = false;

        StrategicGrowthState.Plan current = created;
        for (int iteration = 0; iteration < 20_000 && !current.status().terminal(); iteration++) {
            current = runtime.advancePlan(planId);
            if (supportFleet == null && !current.assignedSupportFleetIds().isEmpty()) {
                supportFleet = current.assignedSupportFleetIds().get(0);
                FleetPlacementState placement = runtime.world().findFleet(supportFleet).orElseThrow();
                originalLocalFleetId = placement.localEntityId();
                supportWalletBefore = fleetWalletBalance(runtime.world(), supportFleet);
            }

            if (!savedMidTransit
                    && supportFleet != null
                    && runtime.world().findFleetJump(supportFleet).isPresent()) {
                byte[] bytes = WorldStateCodec.encode(runtime.snapshot());
                WorldState restoredState = WorldStateCodec.decode(bytes);
                runtime = new FactionExpansionRuntime(
                        WorldSimulation.restore(
                                restoredState,
                                content,
                                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME),
                        content);
                current = StrategicGrowthPlanService.findPlan(
                        runtime.world().findFactionStrategicState(TRADE_LEAGUE).orElseThrow(), planId)
                        .orElseThrow();
                savedMidTransit = true;
            }
            runtime.advanceFrame(1f);
        }

        assertTrue(savedMidTransit, "Acceptance must cross save/load during physical fleet transit");
        assertEquals(StrategicGrowthState.Status.ESTABLISHED, current.status());
        assertNotNull(supportFleet);
        ConstructionProjectState project = runtime.world()
                .findConstructionProject(current.anchorProjectId()).orElseThrow();
        assertEquals(ConstructionProjectStatus.COMPLETED, project.status());
        assertTrue(project.materials().stream().allMatch(ConstructionMaterialState::fulfilled));
        assertEquals(TRADE_LEAGUE,
                runtime.world().controllingFaction(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow());
        assertTrue(runtime.world().findFactionStrategicState(TRADE_LEAGUE).orElseThrow()
                .controls(DemoGalaxyFactory.INNER_SYSTEM_ID));

        FleetPlacementState finalPlacement = runtime.world().findFleet(supportFleet).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, finalPlacement.locationKind());
        assertNotEquals(originalLocalFleetId, finalPlacement.localEntityId(),
                "Physical inter-system handoff must allocate a new local EntityId");
        assertTrue(supportWalletBefore > 0L);
        assertTrue(fleetWalletBalance(runtime.world(), supportFleet) < supportWalletBefore,
                "Support fleet must pay real credits to source-system suppliers");
    }

    @Test
    void foreignControlledTargetIsNeverAutoClaimed() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        FactionExpansionRuntime runtime = new FactionExpansionRuntime(
                DemoGalaxyFactory.create(0x11C1L), content);
        List<ExpansionOpportunity> opportunities = FactionExpansionOpportunityAnalyzer
                .analyze(runtime.world(), content, TRADE_LEAGUE);
        assertFalse(opportunities.isEmpty());
        assertTrue(opportunities.stream().allMatch(candidate ->
                !candidate.controllingFactionContentId().isEmpty()));
        assertTrue(runtime.planBestUnclaimed(TRADE_LEAGUE).isEmpty());
    }

    private static long fleetWalletBalance(WorldSimulation world, FleetId fleetId) {
        FleetPlacementState placement = world.findFleet(fleetId).orElseThrow();
        Entity fleet = world.findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().find(placement.localEntityId());
        return fleet.getComponent(WalletComponent.class).getBalanceMilliCredits();
    }

    private static WorldState withoutController(WorldState state, StarSystemId target) {
        List<FactionStrategicState> strategies = new ArrayList<>(state.factionStrategies().size());
        for (FactionStrategicState strategy : state.factionStrategies()) {
            List<StarSystemId> controlled = strategy.controlledSystems().stream()
                    .filter(systemId -> !systemId.equals(target))
                    .toList();
            strategies.add(new FactionStrategicState(
                    strategy.factionContentId(),
                    strategy.minimumMarketAccessRelation(),
                    strategy.relations(),
                    controlled,
                    strategy.stationTaxBasisPoints(),
                    strategy.foreignTerritoryTariffBasisPoints(),
                    strategy.stockPolicies(),
                    strategy.productionPolicies(),
                    strategy.strategicGoals()));
        }
        return new WorldState(
                state.schemaVersion(),
                state.topology(),
                state.systems(),
                state.factions(),
                strategies,
                state.nextConstructionProjectIdValue(),
                state.constructionProjects(),
                state.factionEconomicPressures(),
                state.nextFleetIdValue(),
                state.fleets(),
                state.fleetJumps());
    }
}
