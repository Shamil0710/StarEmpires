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
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage15RouteRiskAcceptanceTest {
    @Test
    void dangerousDirectLinkMakesLongerWholeRoutePreferSaferIntermediateSystems() {
        Fixture fixture = fixtureWithDirectAnchorFrontierLink(15_301L);
        PlayerFleetRoutePlanner planner = new PlayerFleetRoutePlanner(fixture.runtime());
        PlayerRouteRiskView baseline = planner.plan(
                fixture.fleetId(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow();
        assertEquals(List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID),
                baseline.path());

        long tick = fixture.runtime().world().findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getClock().getTick();
        assertTrue(new PlayerThreatIntelService(fixture.runtime()).observeLink(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                50f,
                1f,
                tick));

        PlayerRouteRiskView safer = planner.plan(
                fixture.fleetId(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow();
        assertEquals(List.of(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID), safer.path());
        assertTrue(safer.travelTicks() > baseline.travelTicks(),
                "planner must be willing to spend more travel time to avoid cumulative route danger");
    }

    @Test
    void dangerInIntermediateSystemChangesRouteEvenWhenDestinationIsIdentical() {
        Fixture fixture = fixtureWithDirectAnchorFrontierLink(15_302L);
        long tick = fixture.runtime().world().findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getClock().getTick();
        PlayerThreatIntelService intel = new PlayerThreatIntelService(fixture.runtime());
        assertTrue(intel.observeSystem(DemoGalaxyFactory.INNER_SYSTEM_ID, 80f, 1f, tick));
        assertTrue(intel.observeLink(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                0f,
                1f,
                tick));

        PlayerRouteRiskView route = new PlayerFleetRoutePlanner(fixture.runtime()).plan(
                fixture.fleetId(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow();
        assertEquals(List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID),
                route.path(), "an unsafe intermediate system must affect the whole-route decision");
    }

    @Test
    void realCargoMassAndUtilizationIncreaseActorSpecificRiskCost() {
        Fixture fixture = fixtureWithDirectAnchorFrontierLink(15_303L);
        PlayerFleetRoutePlanner planner = new PlayerFleetRoutePlanner(fixture.runtime());
        long tick = fixture.runtime().world().findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getClock().getTick();
        assertTrue(new PlayerThreatIntelService(fixture.runtime()).observeLink(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                5f,
                1f,
                tick));

        PlayerRouteRiskView empty = planner.plan(
                fixture.fleetId(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow();
        Entity ship = entity(fixture.runtime(), fixture.fleetId());
        InventoryComponent inventory = ship.getComponent(InventoryComponent.class);
        for (int index = 0; index < inventory.stock.length; index++) {
            inventory.stock[index] = 0;
        }
        inventory.capacity = Math.max(10, inventory.capacity);
        inventory.stock[0] = inventory.capacity;

        PlayerRouteRiskView loaded = planner.plan(
                fixture.fleetId(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow();
        assertTrue(loaded.vulnerability() > empty.vulnerability());
        assertTrue(loaded.riskCostTicks() > empty.riskCostTicks());
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
        FleetPlacementState fleet = controllableFleet(world);
        PlayerState player = new PlayerState(
                10_000_000L,
                null,
                List.of(),
                List.of(fleet.id()),
                fleet.id(),
                List.of(
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                        DemoGalaxyFactory.INNER_SYSTEM_ID,
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID),
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

    private static Entity entity(PlayerRuntime runtime, FleetId fleetId) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().find(placement.localEntityId());
    }

    private record Fixture(FleetId fleetId, PlayerRuntime runtime) {
    }
}
