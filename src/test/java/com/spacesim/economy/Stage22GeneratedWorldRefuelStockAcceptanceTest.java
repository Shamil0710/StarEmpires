package com.spacesim.economy;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22GeneratedWorldRefuelStockAcceptanceTest {
    private static final String PROPELLANT = "commodity.material.purified_water";

    @Test
    void planningIsPureAndEmptyStationStockCannotProduceVirtualReactionMass() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime live =
                Stage20PlayableGeneratedWorldFactory.create(
                        Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        Candidate candidate = refuelCandidate(live);
        List<StarSystemId> route = List.of(candidate.placement().systemId(), candidate.destination());

        double stockBefore = systemPropellant(live, candidate.placement().systemId());
        var planned = live.world().planFleetPropellantJourney(candidate.placement().id(), route);
        assertTrue(planned.feasible());
        assertTrue(planned.refuelStops().stream()
                .anyMatch(stop -> stop.systemId().equals(candidate.placement().systemId())));
        assertEquals(stockBefore, systemPropellant(live, candidate.placement().systemId()), 0d,
                "route planning must not reserve or consume future station stock");

        for (var endpoint : live.infrastructure().endpoints()) {
            if (!endpoint.systemId().equals(candidate.placement().systemId())) {
                continue;
            }
            double stored = endpoint.storage().commodityMassKg(PROPELLANT);
            if (stored > 0d) {
                endpoint.storage().removeCommodity(PROPELLANT, stored);
            }
        }

        var exhausted = live.world().planFleetPropellantJourney(candidate.placement().id(), route);
        assertFalse(exhausted.feasible(),
                "zero ship fuel plus zero accessible station stock must fail closed");
        var preparation = live.world().prepareFleetPropellantDeparture(candidate.placement().id(), route);
        assertFalse(preparation.ready());
        assertEquals(0d, preparation.loadedMassKg(), 0d);
        assertEquals(0d, reactionMassKg(engineering(live, candidate.placement())), 0d,
                "failed preparation must not manufacture reaction mass");
    }

    private static Candidate refuelCandidate(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
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
        throw new AssertionError("generated world lacks a local finite refuel acceptance fixture");
    }

    private static double systemPropellant(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            StarSystemId systemId) {
        return runtime.infrastructure().endpoints().stream()
                .filter(endpoint -> endpoint.systemId().equals(systemId))
                .mapToDouble(endpoint -> endpoint.storage().commodityMassKg(PROPELLANT))
                .sum();
    }

    private static EngineeringComponent engineering(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
        return entity.getComponent(EngineeringComponent.class);
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
            if (load.kind()
                    == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.REACTION_MASS) {
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

    private record Candidate(FleetPlacementState placement, StarSystemId destination) {
    }
}
