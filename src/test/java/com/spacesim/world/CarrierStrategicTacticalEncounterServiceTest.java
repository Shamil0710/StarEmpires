package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.CombatantResult;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Result;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Termination;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;
import com.spacesim.world.SmallCraftTacticalEncounterService.ExternalCombatant;
import com.spacesim.world.SmallCraftTacticalEncounterService.LocalFlightState;
import com.spacesim.world.SmallCraftTacticalEncounterService.SmallCraftParticipant;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CarrierStrategicTacticalEncounterServiceTest {
    private static final FleetId CARRIER = new FleetId(100L);
    private static final String HOST = "carrier.alpha";

    @Test
    void strategicHandoffCommitsIndividualLossAndLeavesDetachedCarrierAndBayStateUntouched() {
        SmallCraftRegistry registry = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId deployed = registry.reserveIdentityForCompletedProduction();
        SmallCraftId reserve = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                deployed, 20L, 2_000d, 200_000d, 1d, 0d));
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                reserve, 20L, 2_000d, 200_000d, 1d, 0d));

        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        BayDefinition bay = new BayDefinition(
                new BayId(HOST, "bay.flight"),
                HostKind.SHIP,
                new Dimensions3d(500d, 500d, 500d),
                100_000_000d,
                100_000_000d,
                1d);
        hangars.assign(reserve, bay, OccupancyState.READY);
        var hangarsBefore = hangars.snapshot();

        SmallCraftMissionState missions = SmallCraftMissionState.empty().add(activeMission(1L, deployed));
        SmallCraftTacticalEncounterService exact = new SmallCraftTacticalEncounterService(
                registry,
                hangars,
                (imported, maximumTicks) -> destroyCraftOutcome(imported));
        CarrierStrategicTacticalEncounterService service =
                new CarrierStrategicTacticalEncounterService(exact);

        SmallCraftState externalState = ProductionSmallCraftFixture.craft(
                new SmallCraftId(900L), 20L, 2_000d, 200_000d, 1d, 0d);
        EngineeringComponent carrierEngineering =
                SmallCraftEngineeringMaterializationBridge.materialize(externalState);
        RuntimeState carrierRuntimeBefore = carrierEngineering.runtimeState;
        ExternalCombatant carrierExternal = new ExternalCombatant(
                "fleet:" + CARRIER.value(),
                Side.ALPHA,
                externalState.stableFactionId(),
                carrierEngineering,
                new LocalFlightState(0d, 0d, 0d, 0d));
        ExternalCombatant hostile = new ExternalCombatant(
                "fleet:enemy",
                Side.BETA,
                "faction.enemy",
                SmallCraftEngineeringMaterializationBridge.materialize(externalState),
                new LocalFlightState(1_000d, 0d, 0d, 0d));

        var result = service.resolve(
                operation(),
                CARRIER,
                new CarrierWingAssignment(CARRIER, HOST, externalState.stableFactionId(), List.of(deployed, reserve)),
                missions,
                List.of(new SmallCraftParticipant(
                        deployed,
                        Side.ALPHA,
                        new LocalFlightState(100d, 0d, 0d, 0d))),
                List.of(carrierExternal, hostile),
                10L);

        assertTrue(registry.find(deployed).isEmpty(),
                "Stage-19 loss must remove the exact persistent craft identity");
        assertEquals(3L, registry.nextIdValue(),
                "tactical loss must never allocate or rewind replacement identity");
        assertEquals(MissionStatus.FAILED, result.missionState().requireMission(1L).status());
        assertEquals(hangarsBefore, hangars.snapshot(),
                "unrelated embarked craft/bay state must survive the detached tactical exchange exactly");
        assertEquals(carrierRuntimeBefore, carrierEngineering.runtimeState,
                "ordinary carrier engineering remains detached for its existing world commit authority");
        assertTrue(result.externalCombatants().stream()
                .anyMatch(row -> row.referenceId().equals("fleet:" + CARRIER.value())));
    }

    @Test
    void craftOutsideStrategicCarrierAssociationIsRejectedBeforeTacticalAuthority() {
        SmallCraftRegistry registry = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId assigned = registry.reserveIdentityForCompletedProduction();
        SmallCraftId foreign = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                assigned, 20L, 2_000d, 200_000d, 1d, 0d));
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                foreign, 20L, 2_000d, 200_000d, 1d, 0d));
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        SmallCraftMissionState missions = SmallCraftMissionState.empty().add(activeMission(1L, foreign));

        boolean[] invoked = {false};
        SmallCraftTacticalEncounterService exact = new SmallCraftTacticalEncounterService(
                registry,
                hangars,
                (imported, maximumTicks) -> {
                    invoked[0] = true;
                    return destroyCraftOutcome(imported);
                });
        CarrierStrategicTacticalEncounterService service =
                new CarrierStrategicTacticalEncounterService(exact);

        assertThrows(IllegalArgumentException.class, () -> service.resolve(
                operation(),
                CARRIER,
                new CarrierWingAssignment(CARRIER, HOST, "faction.empire", List.of(assigned)),
                missions,
                List.of(new SmallCraftParticipant(
                        foreign, Side.ALPHA, new LocalFlightState(0d, 0d, 0d, 0d))),
                List.of(externalOpponent()),
                10L));
        assertFalse(invoked[0]);
        assertTrue(registry.find(foreign).isPresent());
    }

    private static Result destroyCraftOutcome(List<ImportedCombatantState> imported) {
        ArrayList<CombatantResult> rows = new ArrayList<>();
        for (ImportedCombatantState row : imported) {
            boolean destroyed = row.entityId() == 1L;
            RuntimeState runtime = row.engineering().runtimeState;
            if (!destroyed) {
                runtime = withReactionMass(runtime,
                        Math.max(0d, runtime.consumables().reactionMassKg() - 10d));
            }
            rows.add(new CombatantResult(
                    row.entityId(),
                    row.side(),
                    row.engineering().fit,
                    runtime,
                    row.engineering().instanceState,
                    row.xM(),
                    row.yM(),
                    row.velocityXMps(),
                    row.velocityYMps(),
                    destroyed));
        }
        return new Result(1L, Termination.ENCOUNTER_HORIZON, List.copyOf(rows));
    }

    private static RuntimeState withReactionMass(RuntimeState source, double reactionMassKg) {
        List<ConsumableLoad> loads = source.consumables().interfaceLoads().stream()
                .map(load -> load.kind() == InterfaceKind.REACTION_MASS
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

    private static MissionOrder activeMission(long id, SmallCraftId craftId) {
        return new MissionOrder(
                id,
                craftId,
                OrderSource.AI,
                MissionType.ANTI_SHIP_STRIKE,
                new MissionTarget(TargetKind.TRACK, "track.enemy"),
                10L,
                MissionStatus.ACTIVE);
    }

    private static ExternalCombatant externalOpponent() {
        SmallCraftState state = ProductionSmallCraftFixture.craft(
                new SmallCraftId(999L), 20L, 2_000d, 200_000d, 1d, 0d);
        return new ExternalCombatant(
                "fleet:enemy",
                Side.BETA,
                "faction.enemy",
                SmallCraftEngineeringMaterializationBridge.materialize(state),
                new LocalFlightState(1_000d, 0d, 0d, 0d));
    }

    private static OperationState operation() {
        return new OperationState(
                1L,
                OperationType.RAID,
                1L,
                1L,
                0,
                List.of(CARRIER),
                new StarSystemId(1L),
                new StarSystemId(1L),
                "system:1",
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, 0, 0L),
                new WithdrawalPolicy(new StarSystemId(1L), 0, true, true),
                OperationStatus.ACTIVE,
                10L,
                10L,
                -1L,
                null,
                null);
    }
}
