package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage15FormationOrdersAcceptanceTest {
    @Test
    void followMaintainsPhysicalSeparationThroughSharedInertialFlight() {
        Fixture fixture = fixture(15_501L, false);
        Entity leader = entity(fixture.runtime(), fixture.active().id());
        Entity follower = entity(fixture.runtime(), fixture.delegated().id());
        TransformComponent leaderTransform = leader.getComponent(TransformComponent.class);
        TransformComponent followerTransform = follower.getComponent(TransformComponent.class);
        leaderTransform.position.set(500f, 500f);
        leaderTransform.velocity.setZero();
        followerTransform.position.set(360f, 500f);
        followerTransform.velocity.setZero();

        PlayerFleetOrderService orders = new PlayerFleetOrderService(fixture.runtime());
        assertTrue(orders.issue(PlayerFleetOrderState.follow(
                fixture.delegated().id(), fixture.active().id())));
        fixture.runtime().advanceFrame(0.1f);

        FlightCommandComponent command = follower.getComponent(FlightCommandComponent.class);
        assertNotNull(command);
        assertTrue(command.axisX > 0f);
        assertTrue(followerTransform.velocity.x > 0f);
        assertTrue(followerTransform.velocity.len() < command.speedCap,
                "FOLLOW must accelerate under shared inertia rather than snap to velocity");

        for (int step = 0; step < 300; step++) {
            fixture.runtime().advanceFrame(0.1f);
        }
        float distance = followerTransform.position.dst(leaderTransform.position);
        assertTrue(distance <= 35f,
                "FOLLOW should converge near its physical separation radius without teleporting");
        assertEquals(FleetOrderType.FOLLOW, orders.order(fixture.delegated().id()).orElseThrow().type());
    }

    @Test
    void escortPresenceReducesProtectedFleetRiskCostWithoutErasingDanger() {
        Fixture fixture = fixture(15_502L, true);
        PlayerThreatIntelService intel = new PlayerThreatIntelService(fixture.runtime());
        long tick = fixture.runtime().world().findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getClock().getTick();
        assertTrue(intel.observeLink(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                8f,
                1f,
                tick));
        PlayerFleetRoutePlanner planner = new PlayerFleetRoutePlanner(fixture.runtime());
        PlayerRouteRiskView unescorted = planner.plan(
                fixture.active().id(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow();

        PlayerFleetOrderService orders = new PlayerFleetOrderService(fixture.runtime());
        assertTrue(orders.issue(PlayerFleetOrderState.escort(
                fixture.delegated().id(), fixture.active().id())));
        PlayerRouteRiskView escorted = planner.plan(
                fixture.active().id(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow();

        assertTrue(escorted.vulnerability() < unescorted.vulnerability());
        assertTrue(escorted.riskCostTicks() < unescorted.riskCostTicks());
        assertEquals(unescorted.linkExposure(), escorted.linkExposure(), 0.000001d,
                "escort mitigates expected actor loss but must not rewrite observed route danger");
        assertEquals(unescorted.systemExposure(), escorted.systemExposure(), 0.000001d);
    }

    @Test
    void patrolDwellsThenUsesStage10TransitAndKeepsPersistentCycle() {
        Fixture fixture = fixture(15_503L, false);
        PlayerFleetOrderService orders = new PlayerFleetOrderService(fixture.runtime());
        assertTrue(orders.issue(PlayerFleetOrderState.patrol(
                fixture.delegated().id(),
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.INNER_SYSTEM_ID))));

        for (int step = 0; step < 15; step++) {
            fixture.runtime().advanceFrame(0.1f);
        }
        assertTrue(fixture.runtime().world().findFleetJump(fixture.delegated().id()).isEmpty(),
                "PATROL should physically dwell before leaving a reached waypoint");

        boolean observedTransit = false;
        for (int step = 0; step < 1200; step++) {
            fixture.runtime().advanceFrame(0.1f);
            if (fixture.runtime().world().findFleetJump(fixture.delegated().id()).isPresent()) {
                observedTransit = true;
            }
            FleetPlacementState placement = fixture.runtime().world()
                    .findFleet(fixture.delegated().id()).orElseThrow();
            if (placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    && DemoGalaxyFactory.INNER_SYSTEM_ID.equals(placement.systemId())
                    && fixture.runtime().world().findFleetJump(fixture.delegated().id()).isEmpty()) {
                break;
            }
        }

        FleetPlacementState arrived = fixture.runtime().world()
                .findFleet(fixture.delegated().id()).orElseThrow();
        assertTrue(observedTransit);
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, arrived.systemId());
        assertEquals(FleetOrderType.PATROL,
                orders.order(fixture.delegated().id()).orElseThrow().type());
    }

    private static Fixture fixture(long seed, boolean requireCombatEscort) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(seed);
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        List<FleetPlacementState> fleets = new ArrayList<>();
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(placement.systemId())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            if (entity != null
                    && entity.getComponent(ShipComponent.class) != null
                    && entity.getComponent(TransformComponent.class) != null) {
                fleets.add(placement);
            }
        }
        fleets.sort((left, right) -> left.id().compareTo(right.id()));
        if (fleets.size() < 2) {
            throw new AssertionError("Stage15 formation fixture requires two fleets");
        }
        FleetPlacementState active = fleets.get(0);
        FleetPlacementState delegated = null;
        if (requireCombatEscort) {
            for (FleetPlacementState candidate : fleets) {
                if (candidate.id().equals(active.id())) {
                    continue;
                }
                Entity entity = session.getEntityRegistry().find(candidate.localEntityId());
                CombatComponent combat = entity.getComponent(CombatComponent.class);
                if (combat != null && combat.isOperational()) {
                    delegated = candidate;
                    break;
                }
            }
        } else {
            delegated = fleets.get(1);
        }
        if (delegated == null) {
            throw new AssertionError("No compatible delegated escort fleet");
        }
        PlayerState player = new PlayerState(
                10_000_000L,
                null,
                List.of(),
                List.of(active.id(), delegated.id()),
                active.id(),
                List.of(
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                        DemoGalaxyFactory.INNER_SYSTEM_ID,
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID),
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        return new Fixture(active, delegated, PlayerRuntime.create(world, content, player));
    }

    private static Entity entity(PlayerRuntime runtime, FleetId fleetId) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        Entity result = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().find(placement.localEntityId());
        assertNotNull(result);
        return result;
    }

    private record Fixture(
            FleetPlacementState active,
            FleetPlacementState delegated,
            PlayerRuntime runtime) {
    }
}
