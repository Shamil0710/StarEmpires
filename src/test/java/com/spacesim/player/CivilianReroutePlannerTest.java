package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilianReroutePlannerTest {
    @Test
    void newObservedDangerReroutesAlongARealDiscoveredAlternative() {
        Fixture fixture = fixture(true, 19_401L);
        CivilianReroutePlanner planner = new CivilianReroutePlanner(fixture.runtime());

        CivilianReroutePlanner.Decision initial = planner.plan(
                fixture.fleetId(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                List.of());
        assertEquals(CivilianReroutePlanner.Action.REROUTE, initial.action());
        assertEquals(List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID),
                initial.selectedPath());

        CivilianReroutePlanner.Decision stable = planner.plan(
                fixture.fleetId(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                initial.selectedPath());
        assertEquals(CivilianReroutePlanner.Action.CONTINUE, stable.action());

        long tick = fixture.runtime().world().findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getClock().getTick();
        assertTrue(new PlayerThreatIntelService(fixture.runtime()).observeLink(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                50f,
                1f,
                tick));

        CivilianReroutePlanner.Decision rerouted = planner.plan(
                fixture.fleetId(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                initial.selectedPath());
        assertEquals(CivilianReroutePlanner.Action.REROUTE, rerouted.action());
        assertEquals(List.of(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID), rerouted.selectedPath());
        assertTrue(rerouted.selectedRoute().orElseThrow().travelTicks()
                        > initial.selectedRoute().orElseThrow().travelTicks(),
                "civilian rerouting must accept a longer physical route when observed exposure warrants it");
    }

    @Test
    void undiscoveredDestinationHoldsInsteadOfInventingAPath() {
        Fixture fixture = fixture(false, 19_402L);
        CivilianReroutePlanner.Decision decision = new CivilianReroutePlanner(fixture.runtime()).plan(
                fixture.fleetId(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID));

        assertEquals(CivilianReroutePlanner.Action.HOLD, decision.action());
        assertTrue(decision.selectedRoute().isEmpty());
        assertTrue(decision.selectedPath().isEmpty());
    }

    @Test
    void repeatedDecisionIsDeterministicAndDoesNotMutateThreatIntel() {
        Fixture fixture = fixture(true, 19_403L);
        CivilianReroutePlanner planner = new CivilianReroutePlanner(fixture.runtime());
        List<PlayerThreatIntelState> before = fixture.runtime().player().threatIntel();

        CivilianReroutePlanner.Decision first = planner.plan(
                fixture.fleetId(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                List.of());
        CivilianReroutePlanner.Decision second = planner.plan(
                fixture.fleetId(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                List.of());

        assertEquals(first, second);
        assertEquals(before, fixture.runtime().player().threatIntel());
    }

    private static Fixture fixture(boolean discoverFrontier, long seed) {
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
        FleetPlacementState fleet = controllableFleet(world);
        List<com.spacesim.world.StarSystemId> discovered = discoverFrontier
                ? List.of(
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                        DemoGalaxyFactory.INNER_SYSTEM_ID,
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                : List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.INNER_SYSTEM_ID);
        PlayerState player = new PlayerState(
                10_000_000L,
                null,
                List.of(),
                List.of(fleet.id()),
                fleet.id(),
                discovered,
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        return new Fixture(fleet.id(), PlayerRuntime.create(world, content, player));
    }

    private static FleetPlacementState controllableFleet(WorldSimulation world) {
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
                    && entity.getComponent(TransformComponent.class) != null) {
                return placement;
            }
        }
        throw new AssertionError("No physical fleet in active system");
    }

    private record Fixture(FleetId fleetId, PlayerRuntime runtime) {
    }
}
