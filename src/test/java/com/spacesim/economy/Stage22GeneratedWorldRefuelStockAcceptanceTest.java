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
import com.spacesim.world.GeneratedWorldFtlTestSupport;
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

    @Test
    void spentBootstrapPropellantSurvivesSaveRestoreWithoutFreeReplenishment() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime live =
                Stage20PlayableGeneratedWorldFactory.create(
                        Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        var endpoint = live.infrastructure().endpoints().stream()
                .filter(value -> value.storage().commodityMassKg(PROPELLANT) > 1d)
                .findFirst().orElseThrow();
        double before = endpoint.storage().commodityMassKg(PROPELLANT);
        double spent = Math.min(1_000d, before * 0.25d);
        endpoint.storage().removeCommodity(PROPELLANT, spent);
        double expected = before - spent;

        var restored = Stage20GeneratedWorldRuntimeBridge.restore(live.captureState());

        assertEquals(expected,
                restored.infrastructure().endpoint(endpoint.stationId())
                        .storage().commodityMassKg(PROPELLANT),
                1e-6d,
                "save/load must preserve spent station propellant instead of reseeding bootstrap stock");
    }

    @Test
    void twoHopJourneyCanReachIntermediateFiniteRefuelNodeBeforeContinuing() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime live =
                Stage20PlayableGeneratedWorldFactory.create(
                        Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        TwoHopCandidate candidate = twoHopCandidate(live);
        List<StarSystemId> route = List.of(
                candidate.placement().systemId(), candidate.middle(), candidate.destination());

        var direct = live.world().planFleetRouteFuel(candidate.placement().id(), route);
        assertFalse(direct.feasible(),
                "selected partial tank must be insufficient for the whole two-hop route without service");
        var firstHop = live.world().planFleetRouteFuel(
                candidate.placement().id(),
                List.of(candidate.placement().systemId(), candidate.middle()));
        assertTrue(firstHop.feasible(),
                "selected partial tank must safely reach the intermediate service system");

        var journey = live.world().planFleetPropellantJourney(candidate.placement().id(), route);
        assertTrue(journey.feasible());
        assertTrue(journey.refuelStops().stream()
                        .anyMatch(stop -> stop.systemId().equals(candidate.middle())),
                "journey must explicitly depend on the intermediate finite refuel node");

        var departure = live.world().prepareFleetPropellantDeparture(candidate.placement().id(), route);
        assertTrue(departure.ready());
        assertEquals(0d, departure.loadedMassKg(), 1e-6d,
                "the first safe hop must not pre-consume fuel from a future station");

        double fuelBeforeFirstHop = reactionMassKg(engineering(live, candidate.placement()));
        live.world().requestFleetJump(candidate.placement().id(), candidate.middle());
        GeneratedWorldFtlTestSupport.advanceOrdinaryJumpToCompletion(
                live, candidate.placement().id());
        FleetPlacementState atMiddle = live.world().findFleet(candidate.placement().id()).orElseThrow();
        assertEquals(candidate.middle(), atMiddle.systemId());
        double fuelAfterFirstHop = reactionMassKg(engineering(live, atMiddle));
        assertTrue(fuelAfterFirstHop < fuelBeforeFirstHop,
                "ordinary physical first-hop approach must consume reaction mass");

        double middleStockBefore = systemPropellant(live, candidate.middle());
        var continuePreparation = live.world().prepareFleetPropellantDeparture(
                candidate.placement().id(), List.of(candidate.middle(), candidate.destination()));
        assertTrue(continuePreparation.ready());
        assertTrue(continuePreparation.loadedMassKg() > 0d,
                "arrival at the intermediate node must physically load station propellant");
        assertEquals(middleStockBefore - continuePreparation.loadedMassKg(),
                systemPropellant(live, candidate.middle()), 1e-6d);
        assertTrue(live.world().planFleetRouteFuel(
                        candidate.placement().id(),
                        List.of(candidate.middle(), candidate.destination())).feasible(),
                "after committed refueling the remaining direct hop must be physically safe");
    }

    @Test
    void originCanProactivelyCarryFuelAcrossDryIntermediateSystem() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime live =
                Stage20PlayableGeneratedWorldFactory.create(
                        Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        TwoHopCandidate candidate = dryIntermediateCandidate(live);
        List<StarSystemId> route = List.of(
                candidate.placement().systemId(), candidate.middle(), candidate.destination());

        assertEquals(0d, systemPropellant(live, candidate.middle()), 0d,
                "fixture requires a genuinely dry intermediate system");
        assertFalse(live.world().planFleetRouteFuel(candidate.placement().id(), route).feasible(),
                "selected partial tank must not already cover the full dry two-hop route");

        var planned = live.world().planFleetPropellantJourney(candidate.placement().id(), route);
        assertTrue(planned.feasible());
        assertTrue(planned.refuelStops().stream()
                        .anyMatch(stop -> stop.systemId().equals(candidate.placement().systemId())),
                "planner must load extra propellant before leaving the last wet system");
        assertFalse(planned.refuelStops().stream()
                        .anyMatch(stop -> stop.systemId().equals(candidate.middle())),
                "no projected service may be invented in the dry intermediate system");

        double originStockBefore = systemPropellant(live, candidate.placement().systemId());
        var prepared = live.world().prepareFleetPropellantDeparture(candidate.placement().id(), route);
        assertTrue(prepared.ready());
        assertTrue(prepared.loadedMassKg() > 0d);
        assertEquals(originStockBefore - prepared.loadedMassKg(),
                systemPropellant(live, candidate.placement().systemId()), 1e-6d);
        assertTrue(live.world().planFleetRouteFuel(candidate.placement().id(), route).feasible(),
                "proactive origin loading must make the complete route safe without future service");
    }

    private static TwoHopCandidate dryIntermediateCandidate(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        double[] fractions = {0d, 0.05d, 0.10d, 0.15d, 0.20d, 0.30d, 0.40d, 0.50d};
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || systemPropellant(runtime, placement.systemId()) <= 0d) {
                continue;
            }
            EngineeringComponent fitted = engineering(runtime, placement);
            if (fitted == null || reactionMassKg(fitted) <= 0d) {
                continue;
            }
            ShipEngineeringRuntime.RuntimeState original = fitted.runtimeState;
            for (StarSystemId middle : runtime.world().getTopology()
                    .neighbors(placement.systemId()).stream().sorted().toList()) {
                List<StoredPropellant> drained = drainSystemPropellant(runtime, middle);
                boolean selected = false;
                try {
                    for (StarSystemId destination : runtime.world().getTopology()
                            .neighbors(middle).stream().sorted().toList()) {
                        if (destination.equals(placement.systemId())) {
                            continue;
                        }
                        for (double fraction : fractions) {
                            fitted.setRuntimeState(scaleReactionMass(original, fraction));
                            List<StarSystemId> route =
                                    List.of(placement.systemId(), middle, destination);
                            if (runtime.world().planFleetRouteFuel(placement.id(), route).feasible()) {
                                continue;
                            }
                            var journey = runtime.world().planFleetPropellantJourney(
                                    placement.id(), route);
                            if (journey.feasible()
                                    && journey.refuelStops().stream()
                                    .anyMatch(stop -> stop.systemId().equals(placement.systemId()))
                                    && journey.refuelStops().stream()
                                    .noneMatch(stop -> stop.systemId().equals(middle))) {
                                selected = true;
                                return new TwoHopCandidate(placement, middle, destination);
                            }
                        }
                    }
                } finally {
                    if (!selected) {
                        restoreSystemPropellant(drained);
                        fitted.setRuntimeState(original);
                    }
                }
            }
            fitted.setRuntimeState(original);
        }
        throw new AssertionError(
                "generated world lacks a two-hop route recoverable by proactive origin fuel "
                        + "after physically drying the intermediate system");
    }

    private static List<StoredPropellant> drainSystemPropellant(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            StarSystemId systemId) {
        ArrayList<StoredPropellant> drained = new ArrayList<>();
        for (var endpoint : runtime.infrastructure().endpoints()) {
            if (!endpoint.systemId().equals(systemId)) {
                continue;
            }
            double stored = endpoint.storage().commodityMassKg(PROPELLANT);
            if (stored <= 0d) {
                continue;
            }
            endpoint.storage().removeCommodity(PROPELLANT, stored);
            drained.add(new StoredPropellant(endpoint.storage(), stored));
        }
        return List.copyOf(drained);
    }

    private static void restoreSystemPropellant(List<StoredPropellant> drained) {
        for (StoredPropellant value : drained) {
            value.storage().addCommodity(PROPELLANT, value.massKg());
        }
    }

    private static TwoHopCandidate twoHopCandidate(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        double[] fractions = {0.10d, 0.15d, 0.20d, 0.25d, 0.30d, 0.40d, 0.50d, 0.60d, 0.70d, 0.80d};
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            EngineeringComponent fitted = engineering(runtime, placement);
            if (fitted == null || reactionMassKg(fitted) <= 0d) {
                continue;
            }
            ShipEngineeringRuntime.RuntimeState original = fitted.runtimeState;
            for (StarSystemId middle : runtime.world().getTopology()
                    .neighbors(placement.systemId()).stream().sorted().toList()) {
                if (systemPropellant(runtime, middle) <= 0d) {
                    continue;
                }
                for (StarSystemId destination : runtime.world().getTopology()
                        .neighbors(middle).stream().sorted().toList()) {
                    if (destination.equals(placement.systemId())) {
                        continue;
                    }
                    for (double fraction : fractions) {
                        fitted.setRuntimeState(scaleReactionMass(original, fraction));
                        var first = runtime.world().planFleetRouteFuel(
                                placement.id(), List.of(placement.systemId(), middle));
                        var whole = runtime.world().planFleetRouteFuel(
                                placement.id(), List.of(placement.systemId(), middle, destination));
                        if (!first.supported() || !first.feasible() || whole.feasible()) {
                            continue;
                        }
                        var journey = runtime.world().planFleetPropellantJourney(
                                placement.id(), List.of(placement.systemId(), middle, destination));
                        if (journey.feasible()
                                && journey.refuelStops().stream()
                                .anyMatch(stop -> stop.systemId().equals(middle))
                                && journey.refuelStops().stream()
                                .noneMatch(stop -> stop.systemId().equals(placement.systemId()))) {
                            return new TwoHopCandidate(placement, middle, destination);
                        }
                    }
                }
            }
            fitted.setRuntimeState(original);
        }
        throw new AssertionError(
                "generated world lacks a two-hop partial-tank route recoverable at an intermediate station");
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

    private static ShipEngineeringRuntime.RuntimeState scaleReactionMass(
            ShipEngineeringRuntime.RuntimeState current,
            double fraction) {
        ConsumableState consumables = current.consumables();
        ArrayList<ConsumableLoad> loads = new ArrayList<>();
        for (ConsumableLoad load : consumables.interfaceLoads()) {
            if (load.kind()
                    == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.REACTION_MASS) {
                loads.add(new ConsumableLoad(
                        load.mountId(),
                        load.interfaceId(),
                        load.kind(),
                        load.amount() * fraction,
                        load.massKg() * fraction,
                        load.itemCount()));
            } else {
                loads.add(load);
            }
        }
        ConsumableState scaled = new ConsumableState(
                consumables.cargoMassKg(),
                consumables.storesMassKg(),
                consumables.missionPayloadMassKg(),
                consumables.missionIntegrationVolumeM3(),
                List.copyOf(loads));
        return new ShipEngineeringRuntime.RuntimeState(
                scaled,
                current.sharedBusEnergyJ(),
                current.shipHeatStoredJ(),
                current.localHeatJByMount(),
                current.thrustLimitNByMount(),
                current.coolantBusCapacityW(),
                current.ftlCooldownSecondsByMount());
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

    private record StoredPropellant(Stage18StationStorage storage, double massKg) {
    }

    private record TwoHopCandidate(
            FleetPlacementState placement,
            StarSystemId middle,
            StarSystemId destination) {
    }
}
