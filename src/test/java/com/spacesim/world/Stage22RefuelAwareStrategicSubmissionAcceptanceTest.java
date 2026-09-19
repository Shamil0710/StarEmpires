package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetOrderSubmissionService.ServiceCapability;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22RefuelAwareStrategicSubmissionAcceptanceTest {
    @Test
    void emptyFittedFleetCanAcceptStrategicMovementOnlyWhenPhysicalStationRefuelMakesRouteSafe() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime live =
                Stage20PlayableGeneratedWorldFactory.create(
                        Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        Candidate candidate = refuelCandidate(live);
        FleetPlacementState placement = candidate.placement();
        Entity entity = live.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
        FactionComponent faction = entity.getComponent(FactionComponent.class);
        assertTrue(faction != null && faction.factionId >= 0);

        FleetReadinessState zeroPropellant = new FleetReadinessState(
                FleetReadinessState.FULL,
                FleetReadinessState.FULL,
                0,
                FleetReadinessState.FULL,
                FleetReadinessState.FULL,
                FleetReadinessState.FULL,
                FleetReadinessState.FULL);
        FleetForceRegistry forces = new FleetForceRegistry(List.of(new FleetForceRegistry.Entry(
                placement.id(),
                faction.factionId,
                FleetLocationKind.IN_SYSTEM,
                placement.systemId(),
                null,
                null,
                EntityStateMapper.capture(entity),
                zeroPropellant)));
        CommandGroupState group = new CommandGroupState(
                1L,
                faction.factionId,
                "Refuel-aware acceptance",
                List.of(placement.id()),
                placement.systemId(),
                false,
                false,
                FleetReadinessState.FULL);
        FleetCommandState command = new FleetCommandState(2L, 1L, List.of(group), List.of());

        FleetStrategicRoutePlanner planner = new FleetStrategicRoutePlanner(live.world().getTopology());
        var services = (FleetOrderSubmissionService.ServiceCapabilityPolicy)
                (factionId, systemId, tick) -> new ServiceCapability(true, true, true, 0L, 1L);
        var risk = (FleetOrderSubmissionService.StrategicRiskPolicy)
                (factionId, type, route, tick) -> 0;
        var access = (FleetStrategicRoutePlanner.TransitAccessPolicy)
                (factionId, from, to, tick, destination) -> true;

        FleetOrderSubmissionService legacy = new FleetOrderSubmissionService(planner);
        assertThrows(IllegalStateException.class, () -> legacy.submit(
                command,
                forces,
                group.id(),
                OrderType.STAGE,
                OrderSource.PLAYER,
                candidate.destination(),
                live.world().getAuthoritativeWorldTick(),
                access,
                services,
                risk));

        FleetOrderSubmissionService physical =
                new FleetOrderSubmissionService(planner, live.world());
        var accepted = physical.submit(
                command,
                forces,
                group.id(),
                OrderType.STAGE,
                OrderSource.PLAYER,
                candidate.destination(),
                live.world().getAuthoritativeWorldTick(),
                access,
                services,
                risk);

        assertEquals(placement.systemId(), accepted.order().route().get(0));
        assertEquals(candidate.destination(), accepted.order().targetSystemId());
        assertTrue(accepted.order().route().size() >= 2);
        assertTrue(live.world().planFleetPropellantJourney(
                placement.id(), accepted.order().route()).feasible());
    }

    private static Candidate refuelCandidate(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EngineeringComponent fitted = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (fitted == null || faction == null) {
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
                "generated world lacks an affiliated fitted fleet with a finite local refuel opportunity");
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
