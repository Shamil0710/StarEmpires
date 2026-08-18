package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.PhysicalWarfareOperation;
import com.spacesim.world.PhysicalWarfareOperationService;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage19EInterdictionAcceptanceTest {
    @Test
    void hiddenPhysicalInterdictionDoesNothingUntilObservedThenExistingPlannerReroutes() {
        Fixture fixture = fixtureWithDirectAnchorFrontierLink(19_503L);
        PhysicalWarfareOperation operation = PhysicalWarfareOperation.interdict(
                fixture.aggressorFleetId(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        PhysicalWarfareOperationService operations = new PhysicalWarfareOperationService(fixture.runtime().world());
        CivilianReroutePlanner reroutes = new CivilianReroutePlanner(fixture.runtime());

        assertTrue(operations.isPhysicallyActive(operation));
        List<PlayerThreatIntelState> beforeIntel = fixture.runtime().player().threatIntel();
        CivilianReroutePlanner.Decision baseline = reroutes.plan(
                fixture.civilianFleetId(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                List.of());
        assertEquals(List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID),
                baseline.selectedPath());
        assertEquals(beforeIntel, fixture.runtime().player().threatIntel(),
                "an unobserved physical operation must not leak into actor knowledge");

        long tick = fixture.runtime().world().getAuthoritativeWorldTick();
        assertTrue(new PlayerWarfareObservationService(fixture.runtime()).observe(
                operation, 50f, 1f, tick));
        CivilianReroutePlanner.Decision observed = reroutes.plan(
                fixture.civilianFleetId(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                baseline.selectedPath());

        assertEquals(CivilianReroutePlanner.Action.REROUTE, observed.action());
        assertEquals(List.of(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID), observed.selectedPath());
        assertTrue(fixture.runtime().player().threatIntel().stream().anyMatch(intel ->
                intel.kind() == PlayerThreatIntelKind.LINK
                        && intel.matchesLink(
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                        && intel.dangerScore() == 50f));
    }

    private static Fixture fixtureWithDirectAnchorFrontierLink(long seed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(seed, content);
        List<JumpConnection> links = new ArrayList<>(base.topology().connections());
        links.add(new JumpConnection(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
        GalaxyTopology topology = new GalaxyTopology(
                base.topology().id(),
                base.topology().name(),
                base.topology().sectors(),
                links);
        WorldState state = new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                base.systems(),
                base.factions(),
                base.factionStrategies(),
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps());
        WorldSimulation world = WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        FleetPlacementState civilian = civilianFleet(world);
        FleetPlacementState aggressor = operationalCombatFleet(world, civilian.id());
        PlayerState player = new PlayerState(
                10_000_000L,
                null,
                List.of(),
                List.of(civilian.id()),
                civilian.id(),
                List.of(
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                        DemoGalaxyFactory.INNER_SYSTEM_ID,
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID),
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        return new Fixture(civilian.id(), aggressor.id(), PlayerRuntime.create(world, content, player));
    }

    private static FleetPlacementState civilianFleet(WorldSimulation world) {
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(placement.systemId())) {
                continue;
            }
            Entity entity = world.findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().find(placement.localEntityId());
            if (entity != null
                    && entity.getComponent(ShipComponent.class) != null
                    && entity.getComponent(InventoryComponent.class) != null
                    && entity.getComponent(TransformComponent.class) != null
                    && (entity.getComponent(TradeAIComponent.class) != null
                    || entity.getComponent(MiningComponent.class) != null)) {
                return placement;
            }
        }
        throw new AssertionError("No civilian physical fleet in active system");
    }

    private static FleetPlacementState operationalCombatFleet(WorldSimulation world, FleetId excluded) {
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.id().equals(excluded)
                    || placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(placement.systemId())) {
                continue;
            }
            Entity entity = world.findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().find(placement.localEntityId());
            CombatComponent combat = entity == null ? null : entity.getComponent(CombatComponent.class);
            if (combat != null && combat.isOperational()) {
                return placement;
            }
        }
        throw new AssertionError("No separate operational combat fleet in active system");
    }

    private record Fixture(
            FleetId civilianFleetId,
            FleetId aggressorFleetId,
            PlayerRuntime runtime) {
    }
}
