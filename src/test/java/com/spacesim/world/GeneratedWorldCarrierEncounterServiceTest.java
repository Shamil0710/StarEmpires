package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.CombatantResult;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Result;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Termination;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.GeneratedWorldCarrierEncounterService.DeployedCraftPhysicalState;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;
import com.spacesim.world.StrategicOperationState.ContactState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeneratedWorldCarrierEncounterServiceTest {

    @Test
    void combinedEncounterCommitsSmallCraftAndOrdinaryFleetConsequencesToSameWorld() {
        var runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        List<MilitaryFleet> fleets = militaryFleets(runtime);
        MilitaryFleet carrier = fleets.get(0);
        MilitaryFleet target = fleets.stream()
                .filter(value -> value.factionId() != carrier.factionId())
                .findFirst()
                .orElseThrow();
        moveFleetByOrdinaryRoute(runtime, target.fleetId(), carrier.systemId());

        FleetPlacementState carrierPlacement =
                runtime.world().findFleet(carrier.fleetId()).orElseThrow();
        FleetPlacementState targetPlacement =
                runtime.world().findFleet(target.fleetId()).orElseThrow();
        assertEquals(carrierPlacement.systemId(), targetPlacement.systemId());
        StarSystemId system = carrierPlacement.systemId();

        String carrierFaction = runtime.world().findFactionStableId(carrier.factionId()).orElseThrow();
        SmallCraftRegistry registry = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId craftId = registry.reserveIdentityForCompletedProduction();
        SmallCraftState source = ProductionSmallCraftFixture.craft(
                craftId, 20L, 2_000d, 200_000d, 1d, 0d);
        registry.registerProducedCraft(new SmallCraftState(
                source.id(),
                carrierFaction,
                source.designId(),
                source.fit(),
                source.runtimeState(),
                source.instanceState()));
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        SmallCraftMissionState missions = SmallCraftMissionState.empty().add(activeMission(1L, craftId));
        double reactionMassBefore = registry.find(craftId).orElseThrow()
                .runtimeState().consumables().reactionMassKg();

        SmallCraftTacticalEncounterService exact = new SmallCraftTacticalEncounterService(
                registry,
                hangars,
                (imported, maximumTicks) -> deterministicCombinedOutcome(imported));
        GeneratedWorldCarrierEncounterService service = new GeneratedWorldCarrierEncounterService(
                runtime,
                registry,
                hangars,
                new CarrierStrategicTacticalEncounterService(exact));

        long now = runtime.world().getAuthoritativeWorldTick();
        StrategicOperationState strategic = StrategicOperationState.empty().add(operation(
                carrier,
                target,
                system,
                now));
        LocalPhysicalKinematics carrierPhysical = runtime.arrival().materialization(system)
                .physicalState(carrierPlacement.localEntityId()).orElseThrow();
        DeployedCraftPhysicalState deployed = new DeployedCraftPhysicalState(
                craftId,
                system,
                carrierPhysical.position().translated(250d, 0d),
                carrierPhysical.velocityXMps(),
                carrierPhysical.velocityYMps());

        var result = service.resolve(
                strategic,
                1L,
                77L,
                new CarrierWingAssignment(
                        carrier.fleetId(),
                        "carrier:" + carrier.fleetId().value(),
                        carrierFaction,
                        List.of(craftId)),
                missions,
                List.of(deployed),
                now,
                10L);

        assertTrue(runtime.world().findFleet(carrier.fleetId()).isPresent());
        assertFalse(runtime.world().findFleet(target.fleetId()).isPresent(),
                "BETA ordinary fleet destruction must commit through the existing world authority");
        assertEquals(
                reactionMassBefore - 100d,
                registry.find(craftId).orElseThrow()
                        .runtimeState().consumables().reactionMassKg(),
                1e-9,
                "small-craft consumables must commit back to the same persistent identity");
        assertEquals(MissionStatus.ACTIVE,
                result.tacticalResult().missionState().requireMission(1L).status());
        assertEquals(OperationStatus.ACTIVE,
                result.strategicState().requireOperation(1L).status());
        assertEquals(77L,
                result.strategicState().requireOperation(1L).encounter().encounterId());
        assertFalse(result.strategicState().requireOperation(1L).encounter().active(),
                "synchronous exact encounter must not leave hidden tactical state behind");
        assertEquals(2L, registry.nextIdValue(),
                "combined combat must not allocate a replacement craft identity");
    }

    private static Result deterministicCombinedOutcome(
            List<ImportedCombatantState> imported) {
        ArrayList<CombatantResult> rows = new ArrayList<>();
        for (ImportedCombatantState row : imported) {
            RuntimeState runtime = row.engineering().runtimeState;
            if (row.entityId() == 1L) {
                runtime = withReactionMass(
                        runtime,
                        runtime.consumables().reactionMassKg() - 100d);
            }
            rows.add(new CombatantResult(
                    row.entityId(),
                    row.side(),
                    row.engineering().fit,
                    runtime,
                    row.engineering().instanceState,
                    row.xM() + 5d,
                    row.yM(),
                    row.velocityXMps(),
                    row.velocityYMps(),
                    row.side() == com.spacesim.ship.LiveTacticalBattleScenario.Side.BETA));
        }
        return new Result(
                1L,
                Termination.ENCOUNTER_HORIZON,
                List.copyOf(rows));
    }

    private static RuntimeState withReactionMass(
            RuntimeState source,
            double reactionMassKg) {
        List<ConsumableLoad> loads = source.consumables().interfaceLoads().stream()
                .map(load -> load.kind() == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.REACTION_MASS
                        ? new ConsumableLoad(
                                load.mountId(),
                                load.interfaceId(),
                                load.kind(),
                                reactionMassKg,
                                reactionMassKg,
                                load.itemCount())
                        : load)
                .toList();
        ConsumableState consumables = new ConsumableState(
                source.consumables().cargoMassKg(),
                source.consumables().storesMassKg(),
                source.consumables().missionPayloadMassKg(),
                source.consumables().missionIntegrationVolumeM3(),
                loads);
        return new RuntimeState(
                consumables,
                source.sharedBusEnergyJ(),
                source.shipHeatStoredJ(),
                source.localHeatJByMount(),
                source.thrustLimitNByMount(),
                source.coolantBusCapacityW(),
                source.ftlCooldownSecondsByMount());
    }

    private static OperationState operation(
            MilitaryFleet carrier,
            MilitaryFleet target,
            StarSystemId system,
            long tick) {
        ContactState contact = new ContactState(
                target.fleetId(),
                system,
                FactionActorObservationSnapshot.ObservationChannel.LOCAL_SENSOR_REPORT,
                "m22.8h:contact:" + target.fleetId().value(),
                tick,
                tick + 100L);
        return new OperationState(
                1L,
                OperationType.INTERCEPTION,
                1L,
                1L,
                carrier.factionId(),
                List.of(carrier.fleetId()),
                system,
                system,
                "system:" + system.value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, 0, 0L),
                new WithdrawalPolicy(system, 0, true, true),
                OperationStatus.CONTACT_CONFIRMED,
                tick,
                tick,
                -1L,
                contact,
                null);
    }

    private static MissionOrder activeMission(long id, SmallCraftId craftId) {
        return new MissionOrder(
                id,
                craftId,
                OrderSource.AI,
                MissionType.INTERCEPTION,
                new MissionTarget(TargetKind.TRACK, "track.enemy"),
                0L,
                MissionStatus.ACTIVE);
    }

    private static List<MilitaryFleet> militaryFleets(
            com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        ArrayList<MilitaryFleet> result = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering != null && faction != null) {
                result.add(new MilitaryFleet(
                        placement.id(),
                        faction.factionId,
                        placement.systemId()));
            }
        }
        result.sort(java.util.Comparator.comparing(MilitaryFleet::fleetId));
        if (result.size() < 2) {
            throw new AssertionError("generated world lacks military fleets");
        }
        return List.copyOf(result);
    }

    private static void moveFleetByOrdinaryRoute(
            com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        List<StarSystemId> route = route(runtime, placement.systemId(), destination);
        for (int index = 1; index < route.size(); index++) {
            GeneratedWorldFtlTestSupport.placeAtOutgoingEndpoint(
                    runtime, fleetId, route.get(index));
            runtime.world().requestFleetJump(fleetId, route.get(index));
            GeneratedWorldFtlTestSupport.advanceOrdinaryJumpToCompletion(runtime, fleetId);
        }
    }

    private static List<StarSystemId> route(
            com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            StarSystemId origin,
            StarSystemId destination) {
        if (origin.equals(destination)) {
            return List.of(origin);
        }
        ArrayDeque<StarSystemId> queue = new ArrayDeque<>();
        Map<StarSystemId, StarSystemId> previous = new HashMap<>();
        queue.add(origin);
        previous.put(origin, null);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            for (StarSystemId neighbor : runtime.world().getTopology().neighbors(current)) {
                if (previous.containsKey(neighbor)) {
                    continue;
                }
                previous.put(neighbor, current);
                if (neighbor.equals(destination)) {
                    ArrayList<StarSystemId> reverse = new ArrayList<>();
                    StarSystemId cursor = destination;
                    while (cursor != null) {
                        reverse.add(cursor);
                        cursor = previous.get(cursor);
                    }
                    java.util.Collections.reverse(reverse);
                    return List.copyOf(reverse);
                }
                queue.addLast(neighbor);
            }
        }
        throw new AssertionError("generated topology has no route between carrier encounter fleets");
    }

    private record MilitaryFleet(
            FleetId fleetId,
            int factionId,
            StarSystemId systemId) { }
}
