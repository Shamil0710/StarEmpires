package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetOrderExecutionServiceStage21MobilityAcceptanceTest {
    @Test
    void multiHopStrategicOrderWaitsForPhysicalFtlCooldownBeforeNextOrdinaryJump() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        ShipEngineeringCatalog strategicCatalog =
                Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        RouteFixture fixture = runtime.world().getFleetPlacements().stream()
                .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(value -> isStrategicMilitary(runtime, strategicCatalog, value))
                .map(value -> twoHopRoute(runtime, value))
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparing(value -> value.placement().id()))
                .orElseThrow(() -> new AssertionError(
                        "generated world lacks a strategic military fleet with a two-hop route"));

        FleetPlacementState start = fixture.placement();
        FleetId fleetId = start.id();
        List<StarSystemId> route = fixture.route();
        CommandGroupState group = new CommandGroupState(
                1L,
                factionId(runtime, start),
                "Stage21E cooldown route",
                List.of(fleetId),
                route.get(0),
                false,
                false,
                FleetReadinessState.FULL);
        FleetOrderState order = new FleetOrderState(
                1L,
                group.id(),
                OrderType.STAGE,
                OrderSource.AI,
                route.get(2),
                route,
                0,
                runtime.world().getAuthoritativeWorldTick(),
                runtime.world().getAuthoritativeWorldTick() + 10_000L,
                OrderStatus.STAGING);
        FleetCommandState command = new FleetCommandState(2L, 2L, List.of(group), List.of(order));
        FleetOrderExecutionService service = new FleetOrderExecutionService(runtime.world().getTopology());

        FleetCommandState firstDispatched = service.dispatchMovementHop(
                runtime.world(), command, forces(runtime, start), order.id());
        assertTrue(runtime.world().findFleetJump(fleetId).isPresent());
        finishCurrentJump(runtime, fleetId);
        FleetPlacementState middle = runtime.world().findFleet(fleetId).orElseThrow();
        assertEquals(route.get(1), middle.systemId());

        FleetCommandState reconciled = service.reconcilePhysicalArrival(
                firstDispatched, forces(runtime, middle), order.id());
        assertEquals(1, reconciled.requireOrder(order.id()).routeCursor());
        assertTrue(hasFittedCooldown(runtime, fleetId),
                "first physical hop must leave the fitted FTL in cooldown");

        FleetCommandState waiting = service.dispatchMovementHop(
                runtime.world(), reconciled, forces(runtime, middle), order.id());
        assertEquals(OrderStatus.ACTIVE, waiting.requireOrder(order.id()).status());
        assertTrue(runtime.world().findFleetJump(fleetId).isEmpty(),
                "strategic execution must wait rather than reset or bypass physical cooldown");
        assertEquals(route.get(1), runtime.world().findFleet(fleetId).orElseThrow().systemId());

        awaitFittedCooldown(runtime, fleetId);
        FleetPlacementState ready = runtime.world().findFleet(fleetId).orElseThrow();
        FleetCommandState secondDispatched = service.dispatchMovementHop(
                runtime.world(), waiting, forces(runtime, ready), order.id());
        assertTrue(runtime.world().findFleetJump(fleetId).isPresent(),
                "the same ordinary jump authority must accept the second hop after physical cooldown");
        finishCurrentJump(runtime, fleetId);
        FleetPlacementState destination = runtime.world().findFleet(fleetId).orElseThrow();
        assertEquals(route.get(2), destination.systemId());
        assertEquals(fleetId, destination.id());

        FleetCommandState completed = service.reconcilePhysicalArrival(
                secondDispatched, forces(runtime, destination), order.id());
        assertEquals(OrderStatus.COMPLETE, completed.requireOrder(order.id()).status());
        assertEquals(2, completed.requireOrder(order.id()).routeCursor());
    }

    private static RouteFixture twoHopRoute(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        StarSystemId origin = placement.systemId();
        for (StarSystemId middle : runtime.world().getTopology().neighbors(origin).stream().sorted().toList()) {
            for (StarSystemId destination : runtime.world().getTopology().neighbors(middle).stream().sorted().toList()) {
                if (!destination.equals(origin)) {
                    return new RouteFixture(placement, List.of(origin, middle, destination));
                }
            }
        }
        return null;
    }

    private static boolean isStrategicMilitary(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            ShipEngineeringCatalog catalog,
            FleetPlacementState placement) {
        EngineeringComponent engineering = engineering(runtime, placement);
        return engineering != null && catalog.getDemonstratorFits().stream()
                .filter(Stage175ICombatTestContentPack::isStage21StrategicFit)
                .map(InstalledFit::fromDemonstrator)
                .anyMatch(engineering.fit::equals);
    }

    private static int factionId(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        Entity entity = entity(runtime, placement);
        var faction = entity.getComponent(com.spacesim.components.FactionComponent.class);
        if (faction == null) throw new AssertionError("strategic military lacks faction");
        return faction.factionId;
    }

    private static FleetForceRegistry forces(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        return new FleetForceRegistry(List.of(new FleetForceRegistry.Entry(
                placement.id(),
                factionId(runtime, placement),
                placement.locationKind(),
                placement.systemId(),
                placement.transitState() == null ? null : placement.transitState().originSystemId(),
                placement.transitState() == null ? null : placement.transitState().destinationSystemId(),
                EntityStateMapper.capture(entity(runtime, placement)),
                FleetReadinessState.FULL)));
    }

    private static Entity entity(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new AssertionError("acceptance helper requires an in-system fleet");
        }
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private static EngineeringComponent engineering(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        return entity(runtime, placement).getComponent(EngineeringComponent.class);
    }

    private static boolean hasFittedCooldown(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        EngineeringComponent engineering = engineering(runtime, placement);
        return engineering != null && engineering.runtimeState.ftlCooldownSecondsByMount().values().stream()
                .anyMatch(value -> value > 0d);
    }

    private static void awaitFittedCooldown(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        for (int attempt = 0; attempt < 400 && hasFittedCooldown(runtime, fleetId); attempt++) {
            runtime.advanceFrame(0.25f);
        }
        assertTrue(!hasFittedCooldown(runtime, fleetId),
                "physical FTL cooldown must clear through ordinary simulation time");
    }

    private static void finishCurrentJump(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        for (int attempt = 0;
                attempt < 800 && runtime.world().findFleetJump(fleetId).isPresent();
                attempt++) {
            runtime.advanceFrame(0.25f);
        }
        assertTrue(runtime.world().findFleetJump(fleetId).isEmpty(),
                "ordinary fitted jump must complete through its existing FSM");
    }

    private record RouteFixture(FleetPlacementState placement, List<StarSystemId> route) { }
}
