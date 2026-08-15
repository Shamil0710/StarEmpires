package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F6ResilienceOverlayAcceptanceTest {
    private static final String SOURCE = "faction.neutral";

    @Test
    void resilienceOverlayIsIndependentCanonicalPersistentAndRemovable() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        ContentCatalog.ItemDefinition baseItem = content.getItems().get(0);
        ContentCatalog.ItemDefinition militaryItem = content.getItems().get(1);
        ContentCatalog.ItemDefinition resilienceItem = content.getItems().get(2);
        WorldState base = DemoGalaxyFactory.createState(0x17F60051L, content);
        FactionStrategicState original = strategy(base, SOURCE);
        FactionStockPolicyState baseStock = new FactionStockPolicyState(baseItem.id(), 40);
        FactionStrategicGoalState militaryGoal = new FactionStrategicGoalState(
                "goal.overlay-test-military",
                FactionStrategicGoalState.GoalType.MILITARY,
                List.of(new FactionStockPolicyState(militaryItem.id(), 50)));
        FactionStrategicState configuredSource = copyStrategy(
                original,
                List.of(baseStock),
                List.of(militaryGoal));
        WorldSimulation world = restore(replaceStrategy(base, configuredSource), content);

        List<FactionStockPolicyState> installed = world.updateFactionResilienceDemandFloors(
                SOURCE,
                List.of(
                        new FactionStockPolicyState(resilienceItem.id(), 30),
                        new FactionStockPolicyState(baseItem.id(), 20)));
        List<FactionStockPolicyState> expectedOverlay = new ArrayList<>(List.of(
                new FactionStockPolicyState(resilienceItem.id(), 30),
                new FactionStockPolicyState(baseItem.id(), 20)));
        expectedOverlay.sort(null);

        assertEquals(expectedOverlay, installed);
        assertEquals(List.of(baseStock), world.findFactionStockProductionPolicy(SOURCE).orElseThrow().stockPolicies(),
                "Automatic resilience overlay must not rewrite base stock policy");
        assertEquals(2, world.findFactionStrategicState(SOURCE).orElseThrow().strategicGoals().size());
        assertEquals(militaryGoal, world.findFactionStrategicState(SOURCE).orElseThrow().strategicGoals().stream()
                .filter(goal -> goal.type() == FactionStrategicGoalState.GoalType.MILITARY)
                .findFirst()
                .orElseThrow());
        FactionStrategicGoalState resilienceGoal = world.findFactionStrategicState(SOURCE).orElseThrow()
                .strategicGoals().stream()
                .filter(goal -> goal.type() == FactionStrategicGoalState.GoalType.RESILIENCE)
                .findFirst()
                .orElseThrow();
        assertEquals("policy.resilience", resilienceGoal.goalId());
        assertEquals(installed, resilienceGoal.demandFloors());

        List<FactionStockPolicyState> beforeInvalid = world.findFactionResilienceDemandFloors(SOURCE);
        assertThrows(IllegalArgumentException.class, () -> world.updateFactionResilienceDemandFloors(
                SOURCE,
                List.of(new FactionStockPolicyState("item.missing", 99))));
        assertEquals(beforeInvalid, world.findFactionResilienceDemandFloors(SOURCE),
                "Invalid content reference must be rejected before overlay mutation");
        assertEquals(militaryGoal, world.findFactionStrategicState(SOURCE).orElseThrow().strategicGoals().stream()
                .filter(goal -> goal.type() == FactionStrategicGoalState.GoalType.MILITARY)
                .findFirst()
                .orElseThrow());

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(world.snapshot()));
        WorldSimulation restored = restore(decoded, content);
        assertEquals(installed, restored.findFactionResilienceDemandFloors(SOURCE));
        assertEquals(List.of(baseStock), restored.findFactionStockProductionPolicy(SOURCE).orElseThrow().stockPolicies());
        assertEquals(militaryGoal, restored.findFactionStrategicState(SOURCE).orElseThrow().strategicGoals().stream()
                .filter(goal -> goal.type() == FactionStrategicGoalState.GoalType.MILITARY)
                .findFirst()
                .orElseThrow());

        assertTrue(restored.updateFactionResilienceDemandFloors(SOURCE, List.of()).isEmpty());
        assertTrue(restored.findFactionResilienceDemandFloors(SOURCE).isEmpty());
        assertEquals(List.of(baseStock), restored.findFactionStockProductionPolicy(SOURCE).orElseThrow().stockPolicies());
        assertEquals(List.of(militaryGoal), restored.findFactionStrategicState(SOURCE).orElseThrow().strategicGoals(),
                "Removing automatic resilience demand must preserve independent strategic goals");
    }

    private static WorldSimulation restore(WorldState state, ContentCatalog content) {
        return WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }

    private static FactionStrategicState strategy(WorldState state, String factionContentId) {
        return state.factionStrategies().stream()
                .filter(strategy -> strategy.factionContentId().equals(factionContentId))
                .findFirst()
                .orElseThrow();
    }

    private static FactionStrategicState copyStrategy(
            FactionStrategicState current,
            List<FactionStockPolicyState> stockPolicies,
            List<FactionStrategicGoalState> strategicGoals) {
        return new FactionStrategicState(
                current.factionContentId(),
                current.minimumMarketAccessRelation(),
                current.relations(),
                current.controlledSystems(),
                current.stationTaxBasisPoints(),
                current.foreignTerritoryTariffBasisPoints(),
                stockPolicies,
                current.productionPolicies(),
                strategicGoals,
                current.territorialClaims(),
                current.territorialControlStates(),
                current.territorialRecognitions(),
                current.constructionRightsGranted(),
                current.doctrine());
    }

    private static WorldState replaceStrategy(WorldState base, FactionStrategicState replacement) {
        List<FactionStrategicState> strategies = new ArrayList<>(base.factionStrategies().size());
        for (FactionStrategicState strategy : base.factionStrategies()) {
            strategies.add(strategy.factionContentId().equals(replacement.factionContentId())
                    ? replacement
                    : strategy);
        }
        return new WorldState(
                base.schemaVersion(),
                base.topology(),
                base.systems(),
                base.factions(),
                strategies,
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps(),
                base.factionIdentities(),
                base.factionDiplomacyStates());
    }
}
