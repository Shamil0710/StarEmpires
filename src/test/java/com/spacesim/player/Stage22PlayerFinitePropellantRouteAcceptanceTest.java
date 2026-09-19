package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22PlayerFinitePropellantRouteAcceptanceTest {
    @Test
    void emptyFittedPlayerFleetUsesFiniteStationPropellantAndServicePersists() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime live =
                Stage20PlayableGeneratedWorldFactory.create(
                        Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        Candidate candidate = refuelCandidate(live);
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

        assertEquals(0d, reactionMassKg(engineering), 0d);
        var journey = live.world().planFleetPropellantJourney(
                placement.id(), List.of(placement.systemId(), destination));
        assertTrue(journey.supported());
        assertTrue(journey.feasible());
        assertTrue(journey.refuelStops().stream()
                        .anyMatch(stop -> stop.systemId().equals(placement.systemId())),
                "empty fitted ship must explicitly project a current-system finite refuel");
        assertTrue(planner.plan(placement.id(), placement.systemId(), destination).isPresent(),
                "player routing must retain a route that is physically recoverable by real local stock");

        String stationId = journey.refuelStops().get(0).stationId();
        String commodityId = journey.refuelStops().get(0).commodityId();
        double stationBefore = live.infrastructure().endpoint(stationId)
                .storage().commodityMassKg(commodityId);
        var prepared = live.world().prepareFleetPropellantDeparture(
                placement.id(), List.of(placement.systemId(), destination));
        assertTrue(prepared.ready());
        assertTrue(prepared.loadedMassKg() > 0d);
        assertEquals(stationBefore - prepared.loadedMassKg(),
                live.infrastructure().endpoint(stationId).storage().commodityMassKg(commodityId),
                1e-6d);
        assertTrue(reactionMassKg(engineering) > 0d,
                "physical service must commit reaction mass into the fitted ship");

        byte[] checkpoint = Stage20GeneratedWorldRuntimePersistenceCodec.encode(live.captureState());
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime restored =
                Stage20GeneratedWorldRuntimeBridge.restore(
                        Stage20GeneratedWorldRuntimePersistenceCodec.decode(checkpoint));
        assertEquals(
                live.infrastructure().endpoint(stationId).storage().commodityMassKg(commodityId),
                restored.infrastructure().endpoint(stationId).storage().commodityMassKg(commodityId),
                0d,
                "spent station propellant must not respawn after save/load");
        EngineeringComponent restoredEngineering = engineering(
                restored, restored.world().findFleet(placement.id()).orElseThrow());
        assertEquals(reactionMassKg(engineering), reactionMassKg(restoredEngineering), 0d,
                "loaded ship reaction mass must survive save/load exactly");
    }

    private static Candidate refuelCandidate(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            EngineeringComponent fitted = engineering(runtime, placement);
            if (fitted == null) {
                continue;
            }
            ShipEngineeringRuntime.RuntimeState original = fitted.runtimeState;
            fitted.setRuntimeState(withoutReactionMass(original));
            for (StarSystemId destination : runtime.world().getTopology()
                    .neighbors(placement.systemId()).stream().sorted().toList()) {
                var journey = runtime.world().planFleetPropellantJourney(
                        placement.id(), List.of(placement.systemId(), destination));
                if (journey.supported()
                        && journey.feasible()
                        && journey.refuelStops().stream()
                        .anyMatch(stop -> stop.systemId().equals(placement.systemId()))) {
                    return new Candidate(placement, destination);
                }
            }
            fitted.setRuntimeState(original);
        }
        throw new AssertionError(
                "generated world lacks a fitted fleet with a finite local propellant refuel opportunity");
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

    private static double reactionMassKg(EngineeringComponent engineering) {
        return engineering.runtimeState.consumables().interfaceLoads().stream()
                .filter(load -> load.kind()
                        == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.REACTION_MASS)
                .mapToDouble(ConsumableLoad::massKg)
                .sum();
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
