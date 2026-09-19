package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22PlayerFinitePropellantRouteAcceptanceTest {
    @Test
    void fittedPlayerRouteExistsWithFuelAndDisappearsWhenReactionMassIsEmpty() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime live =
                Stage20PlayableGeneratedWorldFactory.create(
                        Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        Candidate candidate = feasibleCandidate(live);
        FleetPlacementState placement = candidate.placement();
        EngineeringComponent engineering = engineering(live, placement);
        StarSystemId destination = candidate.destination();

        List<StarSystemId> discovered = live.world().getTopology().systems().stream()
                .map(value -> value.id())
                .sorted()
                .toList();
        PlayerState player = new PlayerState(
                0L,
                null,
                List.of(),
                List.of(placement.id()),
                placement.id(),
                discovered,
                List.of(),
                placement.systemId());
        PlayerRuntime runtime = PlayerRuntime.create(
                live.world(), ContentCatalogLoader.loadDefault(), player);
        PlayerFleetRoutePlanner planner = new PlayerFleetRoutePlanner(runtime);

        assertTrue(planner.plan(placement.id(), placement.systemId(), destination).isPresent(),
                "ordinary generated fitted ship must have a fuel-safe neighboring route from bootstrap stock");

        engineering.setRuntimeState(withoutReactionMass(engineering.runtimeState));
        assertFalse(planner.plan(placement.id(), placement.systemId(), destination).isPresent(),
                "player route planner must not expose any route once the fitted ship has no reaction mass");
    }

    private static Candidate feasibleCandidate(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || engineering(runtime, placement) == null) {
                continue;
            }
            for (StarSystemId destination : runtime.world().getTopology().neighbors(placement.systemId()).stream()
                    .sorted().toList()) {
                var fuel = runtime.world().planFleetRouteFuel(
                        placement.id(), List.of(placement.systemId(), destination));
                if (fuel.supported() && fuel.feasible()) {
                    return new Candidate(placement, destination);
                }
            }
        }
        throw new AssertionError("generated world lacks a fitted fleet with one fuel-safe neighboring hop");
    }

    private static EngineeringComponent engineering(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
        return entity.getComponent(EngineeringComponent.class);
    }

    private record Candidate(FleetPlacementState placement, StarSystemId destination) {
    }

    private static ShipEngineeringRuntime.RuntimeState withoutReactionMass(
            ShipEngineeringRuntime.RuntimeState current) {
        ConsumableState consumables = current.consumables();
        ArrayList<ConsumableLoad> loads = new ArrayList<>();
        for (ConsumableLoad load : consumables.interfaceLoads()) {
            if (load.kind() == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.REACTION_MASS) {
                loads.add(new ConsumableLoad(
                        load.mountId(), load.interfaceId(), load.kind(), 0d, 0d, load.itemCount()));
            } else {
                loads.add(load);
            }
        }
        ConsumableState empty = new ConsumableState(
                consumables.cargoMassKg(),
                consumables.storesMassKg(),
                consumables.missionPayloadMassKg(),
                consumables.missionIntegrationVolumeM3(),
                List.copyOf(loads));
        return new ShipEngineeringRuntime.RuntimeState(
                empty,
                current.sharedBusEnergyJ(),
                current.shipHeatStoredJ(),
                current.localHeatJByMount(),
                current.thrustLimitNByMount(),
                current.coolantBusCapacityW(),
                current.ftlCooldownSecondsByMount());
    }
}
